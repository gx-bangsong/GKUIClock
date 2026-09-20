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

import com.best.deskclock.provider.Alarm;

import android.content.Context;

import com.best.deskclock.data.Weekdays;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class HolidayUtils {

    public static final int HOLIDAY_OPTION_NONE = 0;
    public static final int HOLIDAY_OPTION_SKIP_HOLIDAY = 1;
    public static final int HOLIDAY_OPTION_BIG_SMALL_DA = 2;
    public static final int HOLIDAY_OPTION_BIG_SMALL_XIAO = 3;
    public static final int HOLIDAY_OPTION_SINGLE_DAY_OFF = 4;

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd", Locale.US));

    /**
     * Determines if the alarm should ring on the given date based on the selected holiday option.
     *
     * @param context       The context.
     * @param holidayOption The holiday option selected for the alarm.
     * @param daysOfWeek    The repeating days of the week for the alarm.
     * @param calendar      The date to check.
     * @return True if the alarm should ring, false otherwise.
     */
    public static boolean shouldAlarmRing(Context context, int holidayOption, Weekdays daysOfWeek, Calendar calendar) {
        if (holidayOption == HOLIDAY_OPTION_NONE) {
            return true;
        }

        String dateStr = DATE_FORMAT_THREAD_LOCAL.get().format(calendar.getTime());

        // Check if it's a legal holiday or compensation workday. Missing or broken
        // holiday data must never silence an alarm: fall back to the plain weekday bits.
        boolean isLegalHoliday = false;
        boolean isCompWorkday = false;
        try {
            HolidayRepository repo = HolidayRepository.getInstance(context);
            Holiday holiday = repo.getHolidayByDate(dateStr);
            Holiday compDay = repo.getCompDayByDate(dateStr);
            isLegalHoliday = (holiday != null);
            isCompWorkday = (compDay != null);
        } catch (RuntimeException e) {
            // Data issue; treat the date as an ordinary day and keep ringing per the
            // alarm's weekday selection.
        }

        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        switch (holidayOption) {
            case HOLIDAY_OPTION_SKIP_HOLIDAY:
                // Normal skip holiday logic:
                // 1. Ring if it's a compensation workday (even if it's a weekend)
                // 2. Skip if it's a legal holiday
                // 3. Otherwise, follow the alarm's set days of the week
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                return daysOfWeek.isBitOn(dayOfWeek);

            case HOLIDAY_OPTION_BIG_SMALL_DA:
                // Big Week: Sat don't work this week, next week Sat work.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                if (dayOfWeek == Calendar.SATURDAY) {
                    return (calendar.get(Calendar.WEEK_OF_YEAR) % 2 == 0);
                }
                return (dayOfWeek != Calendar.SUNDAY);

            case HOLIDAY_OPTION_BIG_SMALL_XIAO:
                // Small Week: Opposite of Big Week.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                if (dayOfWeek == Calendar.SATURDAY) {
                    return (calendar.get(Calendar.WEEK_OF_YEAR) % 2 != 0);
                }
                return (dayOfWeek != Calendar.SUNDAY);

            case HOLIDAY_OPTION_SINGLE_DAY_OFF:
                // Single Day Off: Every week work Mon-Sat, Sun off.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                return (dayOfWeek != Calendar.SUNDAY);

            default:
                return true;
        }
    }

    /**
     * Calculates the absolute next alarm time, taking holidays into account.
     *
     * @param context       The context.
     * @param holidayOption The holiday option selected for the alarm.
     * @param alarm         The alarm object.
     * @param currentTime   The reference time.
     * @return The next valid firing time.
     */
    public static Calendar getNextWorkdayAlarmTime(Context context, int holidayOption, Alarm alarm, Calendar currentTime) {
        if (holidayOption == HOLIDAY_OPTION_NONE) {
            return alarm.getNextAlarmTime(currentTime);
        }

        Calendar nextInstanceTime;
        if (alarm.daysOfWeek.isRepeating()) {
            // Anchor the search at *today* at the alarm's wall-clock time, not at the next
            // weekday-bit match. getNextAlarmTime alone would already skip weekend days for
            // e.g. a Mon-Fri alarm, so a weekend day that is a compensation workday (调休)
            // would never even be considered as a candidate.
            nextInstanceTime = Calendar.getInstance(currentTime.getTimeZone());
            nextInstanceTime.setTimeInMillis(currentTime.getTimeInMillis());
            nextInstanceTime.set(Calendar.HOUR_OF_DAY, alarm.hour);
            nextInstanceTime.set(Calendar.MINUTE, alarm.minutes);
            nextInstanceTime.set(Calendar.SECOND, 0);
            nextInstanceTime.set(Calendar.MILLISECOND, 0);
        } else {
            nextInstanceTime = alarm.getNextAlarmTime(currentTime);
        }

        // Iterate up to 365 days, day by day, to find the next day the alarm may ring.
        // shouldAlarmRing combines the weekday bits with the holiday rules, so
        // compensation workdays on non-selected weekdays are picked up as well.
        for (int i = 0; i < 365; i++) {
            if (nextInstanceTime.getTimeInMillis() > currentTime.getTimeInMillis()
                    && shouldAlarmRing(context, holidayOption, alarm.daysOfWeek, nextInstanceTime)) {
                return nextInstanceTime;
            }
            // Advance to the next day
            nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1);
            // Ensure time is reset to the alarm time (in case of DST)
            nextInstanceTime.set(Calendar.HOUR_OF_DAY, alarm.hour);
            nextInstanceTime.set(Calendar.MINUTE, alarm.minutes);
            nextInstanceTime.set(Calendar.SECOND, 0);
            nextInstanceTime.set(Calendar.MILLISECOND, 0);
        }

        return alarm.getNextAlarmTime(currentTime); // Fallback
    }
}
