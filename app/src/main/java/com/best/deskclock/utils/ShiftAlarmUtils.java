package com.best.deskclock.utils;

import com.best.deskclock.provider.AlarmInstance;
import java.util.regex.Pattern;

public final class ShiftAlarmUtils {
    /**
     * Regex for matching shift keywords in calendar event titles.
     * Match regex for work shifts: ^(早班|中班|晚班|夜班|白班|Shift).*$
     */
    public static final Pattern SHIFT_TITLE_PATTERN =
        Pattern.compile("^(早班|中班|晚班|夜班|白班|Shift).*$", Pattern.CASE_INSENSITIVE);

    /**
     * Regex for matching custom alarm offsets inside calendar descriptions.
     * e.g., "Alarm: -90" means ring 90 minutes before the calendar event starts.
     */
    public static final Pattern OFFSET_DESCRIPTION_PATTERN =
        Pattern.compile("Alarm:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * Keyword to force the alarm to ring even on a statutory holiday.
     */
    public static final String IGNORE_HOLIDAY_KEYWORD = "#IgnoreHoliday";

    /**
     * Default offset in minutes before the event start time.
     */
    public static final int DEFAULT_OFFSET_MINUTES = 90;

    /**
     * Settings.Secure key for the global shift alarm offset.
     */
    public static final String SETTINGS_SECURE_SHIFT_ALARM_OFFSET = "deskclock_shift_alarm_offset";

    /**
     * Buffer time in minutes for conflict resolution.
     */
    public static final int CONFLICT_RESOLUTION_BUFFER_MINUTES = 30;

    private ShiftAlarmUtils() {
        // Prevent instantiation
    }

    /**
     * Helper to check if an AlarmInstance is a Calendar-synced Shift Alarm.
     */
    public static boolean isCalendarShift(AlarmInstance instance) {
        return instance != null && instance.mSourceType == 1;
    }

    /**
     * Helper to check if an AlarmInstance is a local Rotation Shift Alarm.
     */
    public static boolean isRotation(AlarmInstance instance) {
        return instance != null && instance.mSourceType == 2;
    }

    /**
     * Backward compatibility check for any ephemeral instances (Calendar shift or rotation).
     */
    public static boolean isEphemeralId(long id) {
        // Let's keep this if needed, but we will migrate checks to use sourceType.
        return id <= -10000L && id >= -29999L;
    }
}
