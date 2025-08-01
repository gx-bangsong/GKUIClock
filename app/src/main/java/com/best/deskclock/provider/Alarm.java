/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.loader.content.CursorLoader;

import com.best.deskclock.R;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.utils.RingtoneUtils;
import com.best.deskclock.utils.SdkUtils;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public final class Alarm implements Parcelable, ClockContract.AlarmsColumns {
    /**
     * Alarms start with an invalid id when it hasn't been saved to the database.
     */
    public static final long INVALID_ID = -1;

    public static final Parcelable.Creator<Alarm> CREATOR = new Parcelable.Creator<>() {
        public Alarm createFromParcel(Parcel p) {
            return new Alarm(p);
        }

        public Alarm[] newArray(int size) {
            return new Alarm[size];
        }
    };
    /**
     * The default sort order for this table
     */
    private static final String DEFAULT_SORT_ORDER =
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + HOUR + ", " +
                    ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + MINUTES + " ASC" + ", " +
                    ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + ClockContract.AlarmsColumns._ID + " DESC";
    private static final String[] QUERY_COLUMNS = {
            _ID,
            YEAR,
            MONTH,
            DAY,
            HOUR,
            MINUTES,
            DAYS_OF_WEEK,
            ENABLED,
            VIBRATE,
            FLASH,
            LABEL,
            RINGTONE,
            DELETE_AFTER_USE,
            AUTO_SILENCE_DURATION,
            SNOOZE_DURATION,
            CRESCENDO_DURATION,
            ALARM_VOLUME,
            HOLIDAY_OPTION // FIX: 添加 HOLIDAY_OPTION
    };
    private static final String[] QUERY_ALARMS_WITH_INSTANCES_COLUMNS = {
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + _ID,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + YEAR,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + MONTH,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + DAY,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + HOUR,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + MINUTES,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + DAYS_OF_WEEK,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + ENABLED,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + VIBRATE,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + FLASH,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + LABEL,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + RINGTONE,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + DELETE_AFTER_USE,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AUTO_SILENCE_DURATION,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + SNOOZE_DURATION,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + CRESCENDO_DURATION,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + ALARM_VOLUME,
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + HOLIDAY_OPTION, // FIX: 添加 HOLIDAY_OPTION
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.ALARM_STATE,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns._ID,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.YEAR,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.MONTH,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.DAY,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.HOUR,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.MINUTES,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.LABEL,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.VIBRATE,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.FLASH,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.AUTO_SILENCE_DURATION,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.SNOOZE_DURATION,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.CRESCENDO_DURATION,
            ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + ClockContract.InstancesColumns.ALARM_VOLUME
    };
    /**
     * These save calls to cursor.getColumnIndexOrThrow()
     * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
     */
    private static final int ID_INDEX = 0;
    private static final int YEAR_INDEX = 1;
    private static final int MONTH_INDEX = 2;
    private static final int DAY_INDEX = 3;
    private static final int HOUR_INDEX = 4;
    private static final int MINUTES_INDEX = 5;
    private static final int DAYS_OF_WEEK_INDEX = 6;
    private static final int ENABLED_INDEX = 7;
    private static final int VIBRATE_INDEX = 8;
    private static final int FLASH_INDEX = 9;
    private static final int LABEL_INDEX = 10;
    private static final int RINGTONE_INDEX = 11;
    private static final int DELETE_AFTER_USE_INDEX = 12;
    private static final int AUTO_SILENCE_DURATION_INDEX = 13;
    private static final int SNOOZE_DURATION_INDEX = 14;
    private static final int CRESCENDO_DURATION_INDEX = 15;
    private static final int ALARM_VOLUME_INDEX = 16;
    private static final int HOLIDAY_OPTION_INDEX = 17; // FIX: 添加 HOLIDAY_OPTION_INDEX

    private static final int INSTANCE_STATE_INDEX = 18; // FIX: 更新 INSTANCE_STATE_INDEX
    public static final int INSTANCE_ID_INDEX = 19;     // FIX: 更新 INSTANCE_ID_INDEX
    public static final int INSTANCE_YEAR_INDEX = 20;    // FIX: 更新 INSTANCE_YEAR_INDEX
    public static final int INSTANCE_MONTH_INDEX = 21;   // FIX: 更新 INSTANCE_MONTH_INDEX
    public static final int INSTANCE_DAY_INDEX = 22;     // FIX: 更新 INSTANCE_DAY_INDEX
    public static final int INSTANCE_HOUR_INDEX = 23;    // FIX: 更新 INSTANCE_HOUR_INDEX
    public static final int INSTANCE_MINUTE_INDEX = 24;  // FIX: 更新 INSTANCE_MINUTE_INDEX
    public static final int INSTANCE_LABEL_INDEX = 25;   // FIX: 更新 INSTANCE_LABEL_INDEX
    public static final int INSTANCE_VIBRATE_INDEX = 26; // FIX: 更新 INSTANCE_VIBRATE_INDEX
    public static final int INSTANCE_FLASH_INDEX = 27;   // FIX: 更新 INSTANCE_FLASH_INDEX
    public static final int INSTANCE_AUTO_SILENCE_DURATION_INDEX = 28; // FIX: 更新 INSTANCE_AUTO_SILENCE_DURATION_INDEX
    public static final int INSTANCE_SNOOZE_DURATION_INDEX = 29;     // FIX: 更新 INSTANCE_SNOOZE_DURATION_INDEX
    public static final int INSTANCE_CRESCENDO_DURATION_INDEX = 30;  // FIX: 更新 INSTANCE_CRESCENDO_DURATION_INDEX
    public static final int INSTANCE_ALARM_VOLUME_INDEX = 31;        // FIX: 更新 INSTANCE_ALARM_VOLUME_INDEX

    private static final int COLUMN_COUNT = HOLIDAY_OPTION_INDEX + 1; // FIX: 更新 COLUMN_COUNT
    private static final int ALARM_JOIN_INSTANCE_COLUMN_COUNT = INSTANCE_ALARM_VOLUME_INDEX + 1; // FIX: 更新 ALARM_JOIN_INSTANCE_COLUMN_COUNT
    // Public fields
    public long id;
    public boolean enabled;
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minutes;
    public Weekdays daysOfWeek;
    public boolean vibrate;
    public boolean flash;
    public String label;
    public Uri alert;
    public boolean deleteAfterUse;
    public int autoSilenceDuration;
    public int snoozeDuration;
    public int crescendoDuration;
    // Alarm volume level in steps; not a percentage
    public int alarmVolume;
    public int holidayOption = 0; // 确保这个字段存在
    public int instanceState;
    public int instanceId;

    // Creates a default alarm at the current time.
    public Alarm() {
        this(Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH),
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                0,
                0);
    }

    public Alarm(int year, int month, int day, int hour, int minutes) {
        this.id = INVALID_ID;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minutes = minutes;
        this.vibrate = true;
        this.flash = true;
        this.daysOfWeek = Weekdays.NONE;
        this.label = "";
        this.alert = DataModel.getDataModel().getAlarmRingtoneUriFromSettings();
        this.deleteAfterUse = false;
        this.autoSilenceDuration = 10;
        this.snoozeDuration = 10;
        this.crescendoDuration = 0;
        this.alarmVolume = 11;
        this.holidayOption = 0; // FIX: 默认构造函数也要初始化
    }

    // Used to backup/restore the alarm
    public Alarm(long id, boolean enabled, int year, int month, int day, int hour, int minutes,
                 boolean vibrate, boolean flash, Weekdays daysOfWeek, String label, String alert,
                 boolean deleteAfterUse, int autoSilenceDuration, int snoozeDuration,
                 int crescendoDuration, int alarmVolume) { // FIX: 备份/恢复构造函数也要添加 holidayOption
        this(id, enabled, year, month, day, hour, minutes, vibrate, flash, daysOfWeek, label, alert,
                deleteAfterUse, autoSilenceDuration, snoozeDuration, crescendoDuration, alarmVolume, 0); // 默认值0
    }

    // FIX: 完整备份/恢复构造函数
    public Alarm(long id, boolean enabled, int year, int month, int day, int hour, int minutes,
                 boolean vibrate, boolean flash, Weekdays daysOfWeek, String label, String alert,
                 boolean deleteAfterUse, int autoSilenceDuration, int snoozeDuration,
                 int crescendoDuration, int alarmVolume, int holidayOption) {

        this.id = id;
        this.enabled = enabled;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minutes = minutes;
        this.vibrate = vibrate;
        this.flash = flash;
        this.daysOfWeek = daysOfWeek;
        this.label = label;
        this.alert = Uri.parse(alert);
        this.deleteAfterUse = deleteAfterUse;
        this.autoSilenceDuration = autoSilenceDuration;
        this.snoozeDuration = snoozeDuration;
        this.crescendoDuration = crescendoDuration;
        this.alarmVolume = alarmVolume;
        this.holidayOption = holidayOption; // FIX: 复制 holidayOption
    }

    public Alarm(Cursor c) {
        id = c.getLong(ID_INDEX);
        enabled = c.getInt(ENABLED_INDEX) == 1;
        year = c.getInt(YEAR_INDEX);
        month = c.getInt(MONTH_INDEX);
        day = c.getInt(DAY_INDEX);
        hour = c.getInt(HOUR_INDEX);
        minutes = c.getInt(MINUTES_INDEX);
        daysOfWeek = Weekdays.fromBits(c.getInt(DAYS_OF_WEEK_INDEX));
        vibrate = c.getInt(VIBRATE_INDEX) == 1;
        flash = c.getInt(FLASH_INDEX) == 1;
        label = c.getString(LABEL_INDEX);
        deleteAfterUse = c.getInt(DELETE_AFTER_USE_INDEX) == 1;
        autoSilenceDuration = c.getInt(AUTO_SILENCE_DURATION_INDEX);
        snoozeDuration = c.getInt(SNOOZE_DURATION_INDEX);
        crescendoDuration = c.getInt(CRESCENDO_DURATION_INDEX);
        alarmVolume = c.getInt(ALARM_VOLUME_INDEX);
        holidayOption = c.getInt(HOLIDAY_OPTION_INDEX); // FIX: 使用 HOLIDAY_OPTION_INDEX

        if (c.getColumnCount() == ALARM_JOIN_INSTANCE_COLUMN_COUNT) { // FIX: 这里也需要考虑实例的列数
            instanceState = c.getInt(INSTANCE_STATE_INDEX);
            instanceId = c.getInt(INSTANCE_ID_INDEX);
        }

        if (c.isNull(RINGTONE_INDEX)) {
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        } else {
            alert = Uri.parse(c.getString(RINGTONE_INDEX));
        }
    }

    Alarm(Parcel p) {
        id = p.readLong();
        enabled = p.readInt() == 1;
        year = p.readInt();
        month = p.readInt();
        day = p.readInt();
        hour = p.readInt();
        minutes = p.readInt();
        daysOfWeek = Weekdays.fromBits(p.readInt());
        vibrate = p.readInt() == 1;
        flash = p.readInt() == 1;
        label = p.readString();
        alert = SdkUtils.isAtLeastAndroid13()
                ? p.readParcelable(getClass().getClassLoader(), Uri.class)
                : p.readParcelable(getClass().getClassLoader());
        deleteAfterUse = p.readInt() == 1;
        autoSilenceDuration = p.readInt();
        snoozeDuration = p.readInt();
        crescendoDuration = p.readInt();
        alarmVolume = p.readInt();
        holidayOption = p.readInt(); // FIX: Parcelable 读取
    }

    public static ContentValues createContentValues(Alarm alarm) {
        ContentValues values = new ContentValues(COLUMN_COUNT);
        if (alarm.id != INVALID_ID) {
            values.put(ClockContract.AlarmsColumns._ID, alarm.id);
        }

        values.put(ENABLED, alarm.enabled ? 1 : 0);
        values.put(YEAR, alarm.year);
        values.put(MONTH, alarm.month);
        values.put(DAY, alarm.day);
        values.put(HOUR, alarm.hour);
        values.put(MINUTES, alarm.minutes);
        values.put(DAYS_OF_WEEK, alarm.daysOfWeek.getBits());
        values.put(VIBRATE, alarm.vibrate ? 1 : 0);
        values.put(FLASH, alarm.flash ? 1 : 0);
        values.put(LABEL, alarm.label);
        values.put(DELETE_AFTER_USE, alarm.deleteAfterUse ? 1 : 0);
        values.put(AUTO_SILENCE_DURATION, alarm.autoSilenceDuration);
        values.put(SNOOZE_DURATION, alarm.snoozeDuration);
        values.put(CRESCENDO_DURATION, alarm.crescendoDuration);
        values.put(ALARM_VOLUME, alarm.alarmVolume);
        values.put(ClockContract.AlarmsColumns.HOLIDAY_OPTION, alarm.holidayOption); // 确保这里已经使用 HOLIDAY_OPTION
        if (alarm.alert == null) {
            values.putNull(RINGTONE);
        } else {
            values.put(RINGTONE, alarm.alert.toString());
        }

        return values;
    }

    // ... 其他方法保持不变 ...

    @NonNull
    @Override
    public String toString() {
        return "Alarm{" +
                "alert=" + alert +
                ", id=" + id +
                ", enabled=" + enabled +
                ", year=" + year +
                ", month=" + month +
                ", day=" + day +
                ", hour=" + hour +
                ", minutes=" + minutes +
                ", daysOfWeek=" + daysOfWeek +
                ", vibrate=" + vibrate +
                ", flash=" + flash +
                ", label='" + label + '\'' +
                ", deleteAfterUse=" + deleteAfterUse +
                ", autoSilenceDuration=" + autoSilenceDuration +
                ", snoozeDuration=" + snoozeDuration +
                ", crescendoDuration=" + crescendoDuration +
                ", alarmVolume=" + alarmVolume +
                ", holidayOption=" + holidayOption + // FIX: 添加到 toString
                '}';
    }
}
