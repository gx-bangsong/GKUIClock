package com.best.deskclock.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Mocking the logic found in Alarm.java to test without Android runtime.
 * This is effectively a white-box test of the algorithm.
 */
public class AlarmRotationTest {

    private Calendar getNextRotationAlarmTime(String rotationPayload, Calendar currentTime) {
        String[] parts = rotationPayload.split("\\|");
        if (parts.length < 6) return null;

        int cycleLength;
        long anchorDateMs;
        boolean holidaySkip;
        String[] minutesPerDay;
        Map<String, Double> overrides = new HashMap<>();

        try {
            cycleLength = Integer.parseInt(parts[1]);
            anchorDateMs = Long.parseLong(parts[2]);
            holidaySkip = Boolean.parseBoolean(parts[3]);
            minutesPerDay = parts[4].split(",");
            Type type = new TypeToken<Map<String, Double>>(){}.getType();
            overrides = new Gson().fromJson(parts[5], type);
        } catch (Exception e) {
            return null;
        }

        Calendar candidate = (Calendar) currentTime.clone();
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);

        Calendar anchor = Calendar.getInstance(candidate.getTimeZone());
        anchor.setTimeInMillis(anchorDateMs);
        anchor.set(Calendar.HOUR_OF_DAY, 0);
        anchor.set(Calendar.MINUTE, 0);
        anchor.set(Calendar.SECOND, 0);
        anchor.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 365; i++) {
            String dateKey = String.format(Locale.US, "%04d-%02d-%02d",
                    candidate.get(Calendar.YEAR), candidate.get(Calendar.MONTH) + 1, candidate.get(Calendar.DAY_OF_MONTH));

            if (overrides != null && overrides.containsKey(dateKey)) {
                int minuteVal = overrides.get(dateKey).intValue();
                if (minuteVal >= 0) {
                    candidate.set(Calendar.HOUR_OF_DAY, minuteVal / 60);
                    candidate.set(Calendar.MINUTE, minuteVal % 60);
                    if (candidate.after(currentTime)) return candidate;
                }
            } else {
                // Simplified: assuming no holidays in this pure logic test
                boolean isHoliday = false;

                if (!isHoliday) {
                    Calendar targetDay = (Calendar) candidate.clone();
                    targetDay.set(Calendar.HOUR_OF_DAY, 0);
                    targetDay.set(Calendar.MINUTE, 0);
                    targetDay.set(Calendar.SECOND, 0);
                    targetDay.set(Calendar.MILLISECOND, 0);

                    long dayMs = 1000 * 60 * 60 * 24;
                    long targetLocal = targetDay.getTimeInMillis() + targetDay.getTimeZone().getOffset(targetDay.getTimeInMillis());
                    long anchorLocal = anchor.getTimeInMillis() + anchor.getTimeZone().getOffset(anchor.getTimeInMillis());
                    int diffDays = (int) (targetLocal / dayMs) - (int) (anchorLocal / dayMs);

                    int cycleIndex = diffDays % cycleLength;
                    if (cycleIndex < 0) cycleIndex += cycleLength;

                    if (cycleIndex < minutesPerDay.length) {
                        try {
                            int minuteVal = Integer.parseInt(minutesPerDay[cycleIndex]);
                            if (minuteVal >= 0) {
                                candidate.set(Calendar.HOUR_OF_DAY, minuteVal / 60);
                                candidate.set(Calendar.MINUTE, minuteVal % 60);
                                if (candidate.after(currentTime)) return candidate;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1);
            candidate.set(Calendar.HOUR_OF_DAY, 0);
            candidate.set(Calendar.MINUTE, 0);
        }
        return null;
    }

    @Test
    public void testLeapYearBoundary() {
        Calendar anchor = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        anchor.set(2028, Calendar.FEBRUARY, 28, 0, 0, 0);
        anchor.set(Calendar.MILLISECOND, 0);
        long anchorMs = anchor.getTimeInMillis();

        // 2-day cycle: Day 1: 08:00, Day 2: Off (-1)
        String payload = String.format(Locale.US, "SHIFT_ROTATION_V2|2|%d|false|480,-1|{}", anchorMs);

        Calendar now = (Calendar) anchor.clone();
        now.set(Calendar.HOUR_OF_DAY, 9);

        // Feb 29 2028 is Day 2 (Off)
        // Mar 01 2028 is Day 1 (08:00)
        Calendar next = getNextRotationAlarmTime(payload, now);

        assertNotNull(next);
        assertEquals(2028, next.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, next.get(Calendar.MONTH));
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testDSTTransition_ClockForward() {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        Calendar anchor = Calendar.getInstance(tz);
        anchor.set(2024, Calendar.MARCH, 9, 0, 0, 0);
        anchor.set(Calendar.MILLISECOND, 0);

        String payload = String.format(Locale.US, "SHIFT_ROTATION_V2|3|%d|false|480,480,480|{}", anchor.getTimeInMillis());

        Calendar now = (Calendar) anchor.clone();
        now.set(Calendar.HOUR_OF_DAY, 9);

        // Next should be Day 2 (Mar 10) 08:00
        Calendar next = getNextRotationAlarmTime(payload, now);
        assertEquals(10, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY));

        // Next should be Day 3 (Mar 11) 08:00
        next = getNextRotationAlarmTime(payload, next);
        assertEquals(11, next.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testDSTTransition_ClockBackward() {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        // DST Backward in US/Eastern: 2024-11-03 02:00 -> 01:00 (25 hour day)
        Calendar anchor = Calendar.getInstance(tz);
        anchor.set(2024, Calendar.NOVEMBER, 2, 0, 0, 0);
        anchor.set(Calendar.MILLISECOND, 0);

        String payload = String.format(Locale.US, "SHIFT_ROTATION_V2|3|%d|false|480,480,480|{}", anchor.getTimeInMillis());

        Calendar now = (Calendar) anchor.clone();
        now.set(Calendar.HOUR_OF_DAY, 9);

        // Next should be Day 2 (Nov 3) 08:00
        Calendar next = getNextRotationAlarmTime(payload, now);
        assertEquals(3, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY));

        // Next should be Day 3 (Nov 4) 08:00
        next = getNextRotationAlarmTime(payload, next);
        assertEquals(4, next.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testMalformedPayloadFallback() {
        Calendar now = Calendar.getInstance();
        Calendar next = getNextRotationAlarmTime("SHIFT_ROTATION_V2|broken|NaN|null", now);
        assertNull(next);
    }
}
