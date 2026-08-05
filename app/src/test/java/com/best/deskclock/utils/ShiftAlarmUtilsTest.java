/*
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShiftAlarmUtilsTest {

    @Test
    public void recognizesGkuiCalendarTitles() {
        assertTrue(ShiftAlarmUtils.isShiftEvent("早班", null));
        assertTrue(ShiftAlarmUtils.isShiftEvent("夜班 A 组", null));
        assertTrue(ShiftAlarmUtils.isShiftEvent("Shift: Support", null));
        assertFalse(ShiftAlarmUtils.isShiftEvent("会议", "ordinary event"));
    }

    @Test
    public void recognizesStructuredMetadataWithoutMatchingTitle() {
        assertTrue(ShiftAlarmUtils.isShiftEvent("Support duty",
                "X-GKUI-SHIFT-ID: team-a-2026-08-05"));
    }

    @Test
    public void parsesLegacyAndStructuredOffsets() {
        assertEquals(-90, ShiftAlarmUtils.parseOffsetMinutes(
                "Alarm: -90\n#IgnoreHoliday", 0));
        assertEquals(-45, ShiftAlarmUtils.parseOffsetMinutes(
                "Alarm: -90\nX-GKUI-ALARM-OFFSET: -45", 0));
        assertEquals(-30, ShiftAlarmUtils.parseOffsetMinutes(null, -30));
    }

    @Test
    public void clampsUntrustedCalendarOffset() {
        assertEquals(ShiftAlarmUtils.MAX_ABSOLUTE_OFFSET_MINUTES,
                ShiftAlarmUtils.parseOffsetMinutes("Alarm: 999999", 0));
        assertEquals(-ShiftAlarmUtils.MAX_ABSOLUTE_OFFSET_MINUTES,
                ShiftAlarmUtils.parseOffsetMinutes("Alarm: -999999", 0));
    }

    @Test
    public void parsesHolidayOverrideCaseInsensitively() {
        assertTrue(ShiftAlarmUtils.shouldIgnoreHoliday("#ignoreholiday"));
        assertTrue(ShiftAlarmUtils.shouldIgnoreHoliday(
                "X-GKUI-IGNORE-HOLIDAY: true"));
        assertFalse(ShiftAlarmUtils.shouldIgnoreHoliday("Alarm: -90"));
    }

    @Test
    public void syncKeyIsStableForMovedNonRecurringEvent() {
        assertEquals("calendar:42",
                ShiftAlarmUtils.createSyncKey(42, 0, "Alarm: -90"));
        assertEquals("calendar:42:123456",
                ShiftAlarmUtils.createSyncKey(42, 123456, "Alarm: -90"));
        assertEquals("gkui:stable-id:42",
                ShiftAlarmUtils.createSyncKey(42, 0,
                        "X-GKUI-SHIFT-ID: stable-id\nAlarm: -90"));
    }
}
