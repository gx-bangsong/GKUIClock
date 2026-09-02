package com.best.deskclock.holiday;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for both GKUIClock's historic holiday format and common international holiday APIs.
 *
 * <p>Accepted inputs include a plain holiday array, the {@code Years} object used by the bundled
 * Chinese source, Nager.Date arrays, Calendarific's {@code response.holidays} wrapper, and generic
 * {@code holidays}, {@code data}, or {@code events} arrays. Field names are matched without regard
 * to case. A holiday can use {@code date}, {@code startDate}/{@code endDate}, or
 * {@code start}/{@code end}. Optional compensation workdays can be supplied in {@code compDays},
 * {@code workdays}, {@code workingDays}, {@code compensationDays}, or {@code makeUpDays}.</p>
 */
public final class HolidayJsonParser {
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?:^|\\D)(\\d{4})[-/.]?(\\d{2})[-/.]?(\\d{2})(?:\\D|$)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

    private HolidayJsonParser() {
    }

    public static Result parse(Reader reader) {
        final JsonElement root = JsonParser.parseReader(reader);
        if (root == null || root.isJsonNull()) {
            throw new JsonParseException("Holiday file is empty");
        }

        final List<Holiday> holidays = new ArrayList<>();
        final Map<String, LinkedHashSet<String>> standaloneWorkdays = new LinkedHashMap<>();
        parseRoot(root, null, holidays, standaloneWorkdays);
        appendStandaloneWorkdays(holidays, standaloneWorkdays);

        // Remove exact duplicates, which are common in files containing both national and local
        // aliases for the same holiday.
        final Map<String, Holiday> unique = new LinkedHashMap<>();
        for (Holiday holiday : holidays) {
            final String key = nullToEmpty(holiday.countryCode) + '|' + holiday.startDate + '|'
                    + holiday.endDate + '|' + holiday.name;
            final Holiday previous = unique.get(key);
            if (previous == null) {
                unique.put(key, holiday);
            } else {
                previous.compDays.addAll(holiday.compDays);
                previous.compDays = new ArrayList<>(new LinkedHashSet<>(previous.compDays));
            }
        }

        if (unique.isEmpty()) {
            throw new JsonParseException("No valid holidays were found");
        }

        final List<Holiday> result = new ArrayList<>(unique.values());
        final Set<String> countryCodes = new LinkedHashSet<>();
        for (Holiday holiday : result) {
            if (holiday.countryCode != null && !holiday.countryCode.isEmpty()) {
                countryCodes.add(holiday.countryCode);
            }
        }
        return new Result(result, countryCodes);
    }

    private static void parseRoot(JsonElement root, String inheritedCountry,
                                  List<Holiday> holidays,
                                  Map<String, LinkedHashSet<String>> standaloneWorkdays) {
        if (root.isJsonArray()) {
            parseArray(root.getAsJsonArray(), inheritedCountry, holidays, standaloneWorkdays);
            return;
        }
        if (!root.isJsonObject()) {
            throw new JsonParseException("Holiday data must be a JSON object or array");
        }

        final JsonObject object = root.getAsJsonObject();
        final String rootCountry = firstNonEmpty(readCountry(object), inheritedCountry,
                inferCountry(object));

        final JsonElement years = getIgnoreCase(object, "years");
        if (years != null && years.isJsonObject()) {
            parseYearObject(years.getAsJsonObject(), rootCountry, holidays, standaloneWorkdays);
            return;
        }

        final JsonElement response = getIgnoreCase(object, "response");
        if (response != null && response.isJsonObject()) {
            final JsonElement responseHolidays = getIgnoreCase(response.getAsJsonObject(), "holidays");
            if (responseHolidays != null && responseHolidays.isJsonArray()) {
                parseArray(responseHolidays.getAsJsonArray(), rootCountry, holidays,
                        standaloneWorkdays);
                return;
            }
        }

        for (String collectionName : new String[]{"holidays", "data", "events"}) {
            final JsonElement collection = getIgnoreCase(object, collectionName);
            if (collection != null && collection.isJsonArray()) {
                parseArray(collection.getAsJsonArray(), rootCountry, holidays, standaloneWorkdays);
                return;
            }
        }

        boolean foundYear = false;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (YEAR_PATTERN.matcher(entry.getKey()).matches() && entry.getValue().isJsonArray()) {
                parseArray(entry.getValue().getAsJsonArray(), rootCountry, holidays,
                        standaloneWorkdays);
                foundYear = true;
            }
        }
        if (foundYear) {
            return;
        }

        if (hasAny(object, "date", "startDate", "start")) {
            parseRecord(object, rootCountry, holidays, standaloneWorkdays);
            return;
        }

        // Also accept a compact {"2026-01-01": "New Year's Day"} map.
        boolean foundDateMap = false;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final String date = normalizeDate(entry.getKey());
            if (date != null && entry.getValue().isJsonPrimitive()) {
                final Holiday holiday = createHoliday(entry.getValue().getAsString(), date, date,
                        rootCountry, Collections.emptyList());
                holidays.add(holiday);
                foundDateMap = true;
            }
        }
        if (!foundDateMap) {
            throw new JsonParseException("Unsupported holiday JSON structure");
        }
    }

    private static void parseYearObject(JsonObject years, String country,
                                        List<Holiday> holidays,
                                        Map<String, LinkedHashSet<String>> standaloneWorkdays) {
        for (Map.Entry<String, JsonElement> entry : years.entrySet()) {
            if (entry.getValue().isJsonArray()) {
                parseArray(entry.getValue().getAsJsonArray(), country, holidays,
                        standaloneWorkdays);
            }
        }
    }

    private static void parseArray(JsonArray array, String country, List<Holiday> holidays,
                                   Map<String, LinkedHashSet<String>> standaloneWorkdays) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                parseRecord(element.getAsJsonObject(), country, holidays, standaloneWorkdays);
            }
        }
    }

    private static void parseRecord(JsonObject object, String inheritedCountry,
                                    List<Holiday> holidays,
                                    Map<String, LinkedHashSet<String>> standaloneWorkdays) {
        final String country = firstNonEmpty(readCountry(object), inheritedCountry);
        final String startDate = readDate(object, "startDate", "start", "date", "observed");
        if (startDate == null) {
            return;
        }

        if (isWorkdayRecord(object)) {
            standaloneWorkdays.computeIfAbsent(nullToEmpty(country), ignored -> new LinkedHashSet<>())
                    .add(startDate);
            return;
        }

        String endDate = readDate(object, "endDate", "end");
        if (endDate == null || endDate.compareTo(startDate) < 0) {
            endDate = startDate;
        }

        final String name = firstNonEmpty(readName(getIgnoreCase(object, "localName")),
                readName(getIgnoreCase(object, "name")),
                readName(getIgnoreCase(object, "summary")),
                readName(getIgnoreCase(object, "title")), "Holiday");
        final List<String> compensationDays = readDateList(object, "compDays", "workdays",
                "workingDays", "compensationDays", "makeUpDays");
        holidays.add(createHoliday(name, startDate, endDate, country, compensationDays));
    }

    private static Holiday createHoliday(String name, String startDate, String endDate,
                                         String country, List<String> compensationDays) {
        final Holiday holiday = new Holiday();
        holiday.name = name;
        holiday.startDate = startDate;
        holiday.endDate = endDate;
        holiday.countryCode = normalizeCountry(country);
        holiday.compDays = new ArrayList<>(new LinkedHashSet<>(compensationDays));
        return holiday;
    }

    private static boolean isWorkdayRecord(JsonObject object) {
        final Boolean workingDay = readBoolean(object, "isWorkingDay", "workingDay");
        if (Boolean.TRUE.equals(workingDay)) {
            return true;
        }
        final Boolean offDay = readBoolean(object, "isOffDay");
        if (Boolean.FALSE.equals(offDay)) {
            return true;
        }
        final Boolean holiday = readBoolean(object, "isHoliday");
        if (Boolean.FALSE.equals(holiday)) {
            return true;
        }
        final String type = readPrimitiveString(getIgnoreCase(object, "type"));
        if (type == null) {
            return false;
        }
        final String normalized = type.replace("-", "").replace("_", "")
                .replace(" ", "").toLowerCase(Locale.US);
        return normalized.equals("workday") || normalized.equals("workingday")
                || normalized.equals("compensationday") || normalized.equals("makeupday");
    }

    private static void appendStandaloneWorkdays(
            List<Holiday> holidays, Map<String, LinkedHashSet<String>> workdays) {
        for (Map.Entry<String, LinkedHashSet<String>> entry : workdays.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Holiday target = null;
            for (Holiday holiday : holidays) {
                if (nullToEmpty(holiday.countryCode).equals(entry.getKey())) {
                    target = holiday;
                    break;
                }
            }
            if (target == null) {
                target = createHoliday("Imported workdays", "0001-01-01", "0001-01-01",
                        entry.getKey(), Collections.emptyList());
                holidays.add(target);
            }
            target.compDays.addAll(entry.getValue());
            target.compDays = new ArrayList<>(new LinkedHashSet<>(target.compDays));
        }
    }

    private static List<String> readDateList(JsonObject object, String... keys) {
        final LinkedHashSet<String> dates = new LinkedHashSet<>();
        for (String key : keys) {
            final JsonElement value = getIgnoreCase(object, key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonArray()) {
                for (JsonElement dateElement : value.getAsJsonArray()) {
                    addNormalizedDate(dates, dateElement);
                }
            } else {
                addNormalizedDate(dates, value);
            }
        }
        return new ArrayList<>(dates);
    }

    private static void addNormalizedDate(Set<String> dates, JsonElement element) {
        final String raw;
        if (element.isJsonObject()) {
            raw = readPrimitiveString(firstPresent(element.getAsJsonObject(),
                    "date", "iso", "startDate"));
        } else {
            raw = readPrimitiveString(element);
        }
        final String normalized = normalizeDate(raw);
        if (normalized != null) {
            dates.add(normalized);
        }
    }

    private static String readDate(JsonObject object, String... keys) {
        for (String key : keys) {
            final JsonElement element = getIgnoreCase(object, key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            final String raw;
            if (element.isJsonObject()) {
                raw = readPrimitiveString(firstPresent(element.getAsJsonObject(),
                        "iso", "date", "datetime"));
            } else {
                raw = readPrimitiveString(element);
            }
            final String normalized = normalizeDate(raw);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeDate(String value) {
        if (value == null) {
            return null;
        }
        final Matcher matcher = DATE_PATTERN.matcher(value.trim());
        if (!matcher.find()) {
            return null;
        }
        final int year = Integer.parseInt(matcher.group(1));
        final int month = Integer.parseInt(matcher.group(2));
        final int day = Integer.parseInt(matcher.group(3));
        final GregorianCalendar calendar = new GregorianCalendar();
        calendar.setLenient(false);
        calendar.clear();
        calendar.set(year, month - 1, day);
        try {
            calendar.getTime();
        } catch (IllegalArgumentException invalidDate) {
            return null;
        }
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    private static String readCountry(JsonObject object) {
        final JsonElement countryCode = firstPresent(object, "countryCode", "country_code",
                "countryId", "country_id");
        String value = readPrimitiveString(countryCode);
        if (value == null) {
            final JsonElement country = getIgnoreCase(object, "country");
            if (country != null && country.isJsonObject()) {
                value = readPrimitiveString(firstPresent(country.getAsJsonObject(),
                        "code", "id", "countryCode"));
            } else {
                value = readPrimitiveString(country);
            }
        }
        return normalizeCountry(value);
    }

    private static String inferCountry(JsonObject root) {
        final String timezone = readPrimitiveString(getIgnoreCase(root, "timezone"));
        final String name = readPrimitiveString(getIgnoreCase(root, "name"));
        if ((timezone != null && timezone.equalsIgnoreCase("Asia/Shanghai"))
                || (name != null && (name.contains("中国")
                || name.toLowerCase(Locale.US).contains("china")))) {
            return "CN";
        }
        return null;
    }

    private static String normalizeCountry(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        final String candidate = value.trim();
        if (candidate.length() == 2) {
            return candidate.toUpperCase(Locale.US);
        }
        for (String code : Locale.getISOCountries()) {
            final Locale locale = new Locale("", code);
            try {
                if (candidate.equalsIgnoreCase(locale.getISO3Country())
                        || candidate.equalsIgnoreCase(locale.getDisplayCountry(Locale.ENGLISH))
                        || candidate.equalsIgnoreCase(locale.getDisplayCountry())) {
                    return code;
                }
            } catch (MissingResourceException ignored) {
                // Continue with the next ISO country.
            }
        }
        return candidate.toUpperCase(Locale.US);
    }

    private static String readName(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            return firstNonEmpty(readPrimitiveString(firstPresent(element.getAsJsonObject(),
                    "text", "name", "value", "localName")));
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                final String name = readName(child);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        }
        return null;
    }

    private static Boolean readBoolean(JsonObject object, String... keys) {
        for (String key : keys) {
            final JsonElement element = getIgnoreCase(object, key);
            if (element != null && element.isJsonPrimitive()) {
                try {
                    return element.getAsBoolean();
                } catch (RuntimeException ignored) {
                    // Try the next alias.
                }
            }
        }
        return null;
    }

    private static String readPrimitiveString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        return firstPresent(object, keys) != null;
    }

    private static JsonElement firstPresent(JsonObject object, String... keys) {
        for (String key : keys) {
            final JsonElement element = getIgnoreCase(object, key);
            if (element != null && !element.isJsonNull()) {
                return element;
            }
        }
        return null;
    }

    private static JsonElement getIgnoreCase(JsonObject object, String key) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class Result {
        private final List<Holiday> mHolidays;
        private final Set<String> mCountryCodes;

        private Result(List<Holiday> holidays, Set<String> countryCodes) {
            mHolidays = Collections.unmodifiableList(holidays);
            mCountryCodes = Collections.unmodifiableSet(countryCodes);
        }

        public List<Holiday> getHolidays() {
            return mHolidays;
        }

        public Set<String> getCountryCodes() {
            return mCountryCodes;
        }
    }
}
