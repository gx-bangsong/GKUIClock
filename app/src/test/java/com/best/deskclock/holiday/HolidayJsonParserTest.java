package com.best.deskclock.holiday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;

public class HolidayJsonParserTest {

    @Test
    public void parsesBundledChineseYearsFormatCaseInsensitively() {
        String json = "{\"Name\":\"中国节假日\",\"Timezone\":\"Asia/Shanghai\","
                + "\"Years\":{\"2026\":[{\"Name\":\"元旦\","
                + "\"StartDate\":\"2026-01-01\",\"EndDate\":\"2026-01-03\","
                + "\"CompDays\":[\"2026-01-04\"]}]}}";

        HolidayJsonParser.Result result = HolidayJsonParser.parse(new StringReader(json));

        assertEquals(1, result.getHolidays().size());
        Holiday holiday = result.getHolidays().get(0);
        assertEquals("元旦", holiday.name);
        assertEquals("2026-01-03", holiday.endDate);
        assertEquals("CN", holiday.countryCode);
        assertEquals("2026-01-04", holiday.compDays.get(0));
    }

    @Test
    public void parsesNagerDateInternationalArray() {
        String json = "[{\"date\":\"2026-07-04\",\"localName\":\"Independence Day\","
                + "\"name\":\"Independence Day\",\"countryCode\":\"US\","
                + "\"global\":true,\"types\":[\"Public\"]}]";

        HolidayJsonParser.Result result = HolidayJsonParser.parse(new StringReader(json));

        assertEquals(1, result.getHolidays().size());
        assertEquals("US", result.getHolidays().get(0).countryCode);
        assertEquals("2026-07-04", result.getHolidays().get(0).startDate);
        assertTrue(result.getCountryCodes().contains("US"));
    }

    @Test
    public void parsesCalendarificWrapperAndNestedIsoDate() {
        String json = "{\"response\":{\"holidays\":[{\"name\":\"Bastille Day\","
                + "\"country\":{\"id\":\"fr\"},"
                + "\"date\":{\"iso\":\"2026-07-14T00:00:00+02:00\"}}]}}";

        HolidayJsonParser.Result result = HolidayJsonParser.parse(new StringReader(json));

        assertEquals("FR", result.getHolidays().get(0).countryCode);
        assertEquals("2026-07-14", result.getHolidays().get(0).startDate);
    }

    @Test
    public void collectsStandaloneCompensationWorkdays() {
        String json = "{\"countryCode\":\"CN\",\"data\":["
                + "{\"date\":\"2026-10-01\",\"name\":\"National Day\",\"isOffDay\":true},"
                + "{\"date\":\"2026-10-10\",\"name\":\"Workday\",\"isOffDay\":false}]}";

        HolidayJsonParser.Result result = HolidayJsonParser.parse(new StringReader(json));

        assertEquals(1, result.getHolidays().size());
        assertTrue(result.getHolidays().get(0).compDays.contains("2026-10-10"));
    }
}
