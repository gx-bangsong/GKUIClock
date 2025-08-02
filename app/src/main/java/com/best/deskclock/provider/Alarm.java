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
                    ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + _ID + " DESC";

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
            ClockContract.AlarmsColumns.HOLIDAY_OPTION // FIX: Added holiday option column
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
            ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + ClockContract.AlarmsColumns.HOLIDAY_OPTION, // FIX: Added holiday option column to join query
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
    private static final int HOLIDAY_OPTION_INDEX = 17; // FIX: Added index constant

    private static final int COLUMN_COUNT = HOLIDAY_OPTION_INDEX + 1; // FIX: Updated column count

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
    public int alarmVolume;
    public int holidayOption; // FIX: Declared the field
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
        this.holidayOption = 0; // FIX: Initialized in constructor
    }

    // Used to backup/restore the alarm
    public Alarm(long id, boolean enabled, int year, int month, int day, int hour, int minutes,
                 boolean vibrate, boolean flash, Weekdays daysOfWeek, String label, String alert,
                 boolean deleteAfterUse, int autoSilenceDuration, int snoozeDuration,
                 int crescendoDuration, int alarmVolume, int holidayOption) { // FIX: Added to constructor args

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
        this.holidayOption = holidayOption; // FIX: Initialized in constructor
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
        holidayOption = c.getInt(HOLIDAY_OPTION_INDEX); // FIX: Correctly read from cursor using the defined index

        // ...
        // No need for getColumnIndex and if check if using the hardcoded index approach
        // ...

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
        holidayOption = p.readInt(); // FIX: Read from parcel
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
        values.put(ClockContract.AlarmsColumns.HOLIDAY_OPTION, alarm.holidayOption); // FIX: Write to ContentValues
        if (alarm.alert == null) {
            values.putNull(RINGTONE);
        } else {
            values.put(RINGTONE, alarm.alert.toString());
        }

        return values;
    }

    public static Intent createIntent(Context context, Class<?> cls, long alarmId) {
        return new Intent(context, cls).setData(getContentUri(alarmId));
    }

    public static Uri getContentUri(long alarmId) {
        return ContentUris.withAppendedId(CONTENT_URI, alarmId);
    }

    public static long getId(Uri contentUri) {
        return ContentUris.parseId(contentUri);
    }

    public static CursorLoader getAlarmsCursorLoader(Context context) {
        return new CursorLoader(context, ALARMS_WITH_INSTANCES_URI,
                QUERY_ALARMS_WITH_INSTANCES_COLUMNS, null, null, DEFAULT_SORT_ORDER) {
            @Override
            public Cursor loadInBackground() {
                DataModel.getDataModel().loadRingtoneTitles();
                return super.loadInBackground();
            }
        };
    }

    public static Alarm getAlarm(ContentResolver cr, long alarmId) {
        try (Cursor cursor = cr.query(getContentUri(alarmId), QUERY_COLUMNS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return new Alarm(cursor);
            }
        }
        return null;
    }

    public static List<Alarm> getAlarms(ContentResolver cr, String selection, String... selectionArgs) {
        final List<Alarm> result = new LinkedList<>();
        try (Cursor cursor = cr.query(CONTENT_URI, QUERY_COLUMNS, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    result.add(new Alarm(cursor));
                } while (cursor.moveToNext());
            }
        }
        return result;
    }

    public static Alarm addAlarm(ContentResolver contentResolver, Alarm alarm) {
        ContentValues values = createContentValues(alarm);
        Uri uri = contentResolver.insert(CONTENT_URI, values);
        alarm.id = getId(uri);
        return alarm;
    }

    public static void updateAlarm(ContentResolver contentResolver, Alarm alarm) {
        if (alarm.id == Alarm.INVALID_ID) return;
        ContentValues values = createContentValues(alarm);
        contentResolver.update(getContentUri(alarm.id), values, null, null);
    }

    public static boolean deleteAlarm(ContentResolver contentResolver, long alarmId) {
        if (alarmId == INVALID_ID) return false;
        int deletedRows = contentResolver.delete(getContentUri(alarmId), "", null);
        return deletedRows == 1;
    }

    public String getLabelOrDefault(Context context) {
        return label.isEmpty() ? context.getString(R.string.default_label) : label;
    }

    public boolean canPreemptivelyDismiss() {
        return instanceState == AlarmInstance.SNOOZE_STATE || instanceState == AlarmInstance.NOTIFICATION_STATE;
    }

    public static boolean isTomorrow(Alarm alarm, Calendar now) {
        if (alarm.instanceState == AlarmInstance.SNOOZE_STATE) {
            return false;
        }
        final int totalAlarmMinutes = alarm.hour * 60 + alarm.minutes;
        final int totalNowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return totalAlarmMinutes <= totalNowMinutes;
    }

    public boolean isDateInThePast() {
        Calendar alarmCalendar = Calendar.getInstance();
        alarmCalendar.set(year, month, day);
        alarmCalendar.set(Calendar.MILLISECOND, 0);
        Calendar currentCalendar = Calendar.getInstance();
        long alarmTimeInMillis = alarmCalendar.getTimeInMillis();
        long currentTimeInMillis = currentCalendar.getTimeInMillis();
        return alarmTimeInMillis < currentTimeInMillis;
    }

    public boolean isSpecifiedDate() {
        Calendar now = Calendar.getInstance();
        int currentMonth = now.get(Calendar.MONTH);
        return year != now.get(Calendar.YEAR)
                || month != currentMonth
                || day != now.get(Calendar.DAY_OF_MONTH);
    }

    public static boolean isSpecifiedDateTomorrow(int alarmYear, int alarmMonth, int alarmDayOfMonth) {
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        int nextDayMonth = tomorrow.get(Calendar.MONTH);
        return alarmYear == tomorrow.get(Calendar.YEAR) &&
                alarmMonth == nextDayMonth &&
                alarmDayOfMonth == tomorrow.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public void writeToParcel(Parcel p, int flags) {
        p.writeLong(id);
        p.writeInt(enabled ? 1 : 0);
        p.writeInt(year);
        p.writeInt(month);
        p.writeInt(day);
        p.writeInt(hour);
        p.writeInt(minutes);
        p.writeInt(daysOfWeek.getBits());
        p.writeInt(vibrate ? 1 : 0);
        p.writeInt(flash ? 1 : 0);
        p.writeString(label);
        p.writeParcelable(alert, flags);
        p.writeInt(deleteAfterUse ? 1 : 0);
        p.writeInt(autoSilenceDuration);
        p.writeInt(snoozeDuration);
        p.writeInt(crescendoDuration);
        p.writeInt(alarmVolume);
        p.writeInt(holidayOption); // FIX: Write to parcel
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public AlarmInstance createInstanceAfter(Calendar time) {
        Calendar nextInstanceTime = getNextAlarmTime(time);
        AlarmInstance result = new AlarmInstance(nextInstanceTime, id);
        result.mVibrate = vibrate;
        result.mFlash = flash;
        result.mLabel = label;
        result.mRingtone = RingtoneUtils.isRandomRingtone(alert)
                ? RingtoneUtils.getRandomRingtoneUri()
                : RingtoneUtils.isRandomCustomRingtone(alert)
                ? RingtoneUtils.getRandomCustomRingtoneUri()
                : alert;
        result.mAutoSilenceDuration = autoSilenceDuration;
        result.mSnoozeDuration = snoozeDuration;
        result.mCrescendoDuration = crescendoDuration;
        result.mAlarmVolume = alarmVolume;
        // You might want to copy the holiday option to the instance as well
        // result.mHolidayOption = holidayOption;
        return result;
    }

    public Calendar getPreviousAlarmTime(Calendar currentTime) {
        final Calendar previousInstanceTime = Calendar.getInstance(currentTime.getTimeZone());
        previousInstanceTime.set(Calendar.YEAR, year);
        previousInstanceTime.set(Calendar.MONTH, month);
        previousInstanceTime.set(Calendar.DAY_OF_MONTH, day);
        previousInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
        previousInstanceTime.set(Calendar.MINUTE, minutes);
        previousInstanceTime.set(Calendar.SECOND, 0);
        previousInstanceTime.set(Calendar.MILLISECOND, 0);

        final int subtractDays = daysOfWeek.getDistanceToPreviousDay(previousInstanceTime);
        if (subtractDays > 0) {
            previousInstanceTime.add(Calendar.DAY_OF_WEEK, -subtractDays);
            return previousInstanceTime;
        } else {
            return null;
        }
    }

    public Calendar getNextAlarmTime(Calendar currentTime) {
        final Calendar nextInstanceTime = Calendar.getInstance(currentTime.getTimeZone());
        nextInstanceTime.set(Calendar.SECOND, 0);
        nextInstanceTime.set(Calendar.MILLISECOND, 0);

        if (daysOfWeek.isRepeating()) {
            nextInstanceTime.setTimeInMillis(currentTime.getTimeInMillis());
            nextInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
            nextInstanceTime.set(Calendar.MINUTE, minutes);

            if (nextInstanceTime.getTimeInMillis() <= currentTime.getTimeInMillis()) {
                nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1);
            }

            final int addDays = daysOfWeek.getDistanceToNextDay(nextInstanceTime);
            if (addDays > 0) {
                nextInstanceTime.add(Calendar.DAY_OF_WEEK, addDays);
            }

            nextInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
            nextInstanceTime.set(Calendar.MINUTE, minutes);
        } else {
            nextInstanceTime.set(Calendar.YEAR, year);
            nextInstanceTime.set(Calendar.MONTH, month);
            nextInstanceTime.set(Calendar.DAY_OF_MONTH, day);
            nextInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
            nextInstanceTime.set(Calendar.MINUTE, minutes);

            if (nextInstanceTime.getTimeInMillis() <= currentTime.getTimeInMillis()) {
                nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1);
            }
        }

        return nextInstanceTime;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof final Alarm other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(id).hashCode();
    }

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
                ", holidayOption=" + holidayOption + // FIX: Added to toString()
                '}';
    }
}