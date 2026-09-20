/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.best.deskclock.holiday;

import android.content.Context;
import android.os.Looper;

import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.alarms.ShiftCalendarManager;
import com.best.deskclock.data.DataModel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.best.deskclock.utils.LogUtils;

public class HolidayRepository {

    private static volatile HolidayRepository sInstance;
    private final Context mContext;
    private final HolidayDao mHolidayDao;
    private final ExecutorService mExecutorService;
    private final Map<String, Holiday> mHolidayCache = new ConcurrentHashMap<>();
    private final Map<String, Holiday> mCompDayCache = new ConcurrentHashMap<>();

    private HolidayRepository(Context context) {
        mContext = context.getApplicationContext();
        HolidayDatabase db = HolidayDatabase.getDatabase(mContext);
        mHolidayDao = db.holidayDao();
        mExecutorService = Executors.newSingleThreadExecutor();

        // Caches are filled on-demand or during update.
    }

    public static HolidayRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (HolidayRepository.class) {
                if (sInstance == null) {
                    sInstance = new HolidayRepository(context);
                }
            }
        }
        return sInstance;
    }

    public void updateWorkdayData() {
        mExecutorService.execute(() -> {
            try {
                String urlStr = DataModel.getDataModel().getHolidayDataUrl();
                if (urlStr == null || urlStr.isEmpty()) return;
                URL url = new URL(urlStr);
                String body;
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    body = sb.toString();
                }
                List<Holiday> holidays = parseHolidayData(body);
                if (holidays.isEmpty()) {
                    LogUtils.e("Holiday data at " + urlStr + " contained no usable entries");
                    return;
                }
                mHolidayDao.deleteAll();
                mHolidayDao.insertAll(holidays);
                mHolidayCache.clear();
                mCompDayCache.clear();
                ShiftCalendarManager.getInstance(mContext).requestSync();
                // The freshest holiday data can move an alarm's true next firing (e.g. a
                // Sunday marked as a makeup workday must ring before Monday); re-evaluate
                // pending instances of holiday-aware alarms so the change takes effect
                // without the user touching the alarm.
                AlarmStateManager.reconcileHolidayInstances(mContext);
            } catch (Exception e) {
                LogUtils.e("Error updating holiday data: " + e.getMessage());
            }
        });
    }

    /**
     * Parses holiday data into {@link Holiday} rows.
     *
     * <p>Supported document shapes:
     * <ul>
     *     <li>a top-level JSON array of holiday entries (simple custom mirrors);</li>
     *     <li>a top-level object with a {@code "Years"}/{@code "years"} member mapping
     *         year strings to arrays of holiday entries (the format published at the
     *         default data URL).</li>
     * </ul>
     * Entry keys accept both PascalCase ({@code Name}/{@code StartDate}/{@code EndDate}/
     * {@code CompDays}) and camelCase variants. Entries without usable dates are skipped.
     */
    static List<Holiday> parseHolidayData(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            final List<JsonObject> entries = new ArrayList<>();
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        entries.add(element.getAsJsonObject());
                    }
                }
            } else if (root.isJsonObject()) {
                final JsonObject rootObject = root.getAsJsonObject();
                for (String yearsKey : new String[]{"Years", "years"}) {
                    final JsonElement yearsElement = rootObject.get(yearsKey);
                    if (yearsElement != null && yearsElement.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> yearEntry
                                : yearsElement.getAsJsonObject().entrySet()) {
                            if (yearEntry.getValue().isJsonArray()) {
                                for (JsonElement element : yearEntry.getValue().getAsJsonArray()) {
                                    if (element.isJsonObject()) {
                                        entries.add(element.getAsJsonObject());
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            }

            if (entries.isEmpty()) {
                return Collections.emptyList();
            }

            final List<Holiday> holidays = new ArrayList<>();
            for (JsonObject entry : entries) {
                final Holiday holiday = new Holiday();
                holiday.name = getAnyString(entry, "Name", "name");
                holiday.startDate = getAnyString(entry, "StartDate", "startDate");
                holiday.endDate = getAnyString(entry, "EndDate", "endDate");
                if (holiday.startDate == null || holiday.endDate == null
                        || holiday.startDate.isEmpty() || holiday.endDate.isEmpty()) {
                    continue;
                }
                final List<String> compDays = new ArrayList<>();
                for (String compKey : new String[]{"CompDays", "compDays"}) {
                    final JsonElement compElement = entry.get(compKey);
                    if (compElement != null && compElement.isJsonArray()) {
                        for (JsonElement day : compElement.getAsJsonArray()) {
                            if (day.isJsonPrimitive()) {
                                final String value = day.getAsString().trim();
                                if (!value.isEmpty()) {
                                    compDays.add(value);
                                }
                            }
                        }
                        break;
                    }
                }
                holiday.compDays = compDays;
                holidays.add(holiday);
            }
            return holidays;
        } catch (Exception e) {
            LogUtils.e("Failed to parse holiday data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String getAnyString(JsonObject object, String firstKey, String secondKey) {
        JsonElement element = object.get(firstKey);
        if (element == null || !element.isJsonPrimitive()) {
            element = object.get(secondKey);
        }
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    public Holiday getHolidayByDate(String date) {
        Holiday h = mHolidayCache.get(date);
        if (h == null && Looper.myLooper() != Looper.getMainLooper()) {
            h = mHolidayDao.getHolidayByDate(date);
            if (h != null) mHolidayCache.put(date, h);
        }
        return h;
    }

    public Holiday getCompDayByDate(String date) {
        Holiday h = mCompDayCache.get(date);
        if (h == null && Looper.myLooper() != Looper.getMainLooper()) {
            h = mHolidayDao.getCompDayByDate(date);
            if (h != null) mCompDayCache.put(date, h);
        }
        return h;
    }

    public List<Holiday> getAllHolidays() {
        return mHolidayDao.getAllHolidays();
    }
}
