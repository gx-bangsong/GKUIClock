package com.best.deskclock.alarms;

import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;

import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.holiday.HolidayUtils;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.provider.ClockContract;
import com.best.deskclock.utils.LogUtils;
import com.best.deskclock.utils.ShiftAlarmUtils;

import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;

public final class ShiftCalendarManager {

    private static final String TAG = "ShiftCalendarManager";

    @SuppressLint("StaticFieldLeak")
    private static ShiftCalendarManager sInstance;
    private final Context mContext;
    private final ShiftCalendarObserver mObserver;
    private boolean mIsObserverRegistered = false;

    private ShiftCalendarManager(Context context) {
        mContext = context.getApplicationContext();
        mObserver = new ShiftCalendarObserver(new Handler(Looper.getMainLooper()));
    }

    public static ShiftCalendarManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ShiftCalendarManager.class) {
                if (sInstance == null) {
                    sInstance = new ShiftCalendarManager(context);
                }
            }
        }
        return sInstance;
    }

    public void registerObserver() {
        if (mIsObserverRegistered) return;
        try {
            mContext.getContentResolver().registerContentObserver(
                    CalendarContract.Events.CONTENT_URI, true, mObserver);
            mIsObserverRegistered = true;
            LogUtils.i(TAG, "ShiftCalendarObserver registered");
        } catch (SecurityException e) {
            LogUtils.e(TAG, "Failed to register ShiftCalendarObserver: missing permissions", e);
        }
    }

    public synchronized void updateShiftAlarms() {
        SharedPreferences prefs = getDefaultSharedPreferences(mContext);
        boolean enabled = SettingsDAO.isCalendarShiftSyncEnabled(prefs);

        ContentResolver cr = mContext.getContentResolver();

        // Always clean up existing ephemeral instances first
        List<AlarmInstance> currentInstances = AlarmInstance.getInstances(cr, null);
        for (AlarmInstance instance : currentInstances) {
            if (ShiftAlarmUtils.isEphemeralId(instance.mId)) {
                LogUtils.i(TAG, "Cleaning up old ephemeral instance: " + instance.mId);
                AlarmStateManager.unregisterInstance(mContext, instance);
                AlarmInstance.deleteInstance(cr, instance.mId);
            }
        }

        if (!enabled) {
            RotationScheduler.scheduleRotationAlarms(mContext);
            LogUtils.i(TAG, "Shift sync disabled, cleaned up instances.");
            return;
        }

        LogUtils.i(TAG, "Updating shift alarms from calendar...");
        RotationScheduler.scheduleRotationAlarms(mContext);

        // 2. Query Calendar Instances for the next 7 days
        long now = System.currentTimeMillis();
        long end = now + (7 * 24 * 60 * 60 * 1000L);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, end);

        String[] projection = {
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
        };

        try (Cursor cursor = cr.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return;

            while (cursor.moveToNext()) {
                long instanceId = cursor.getLong(0);
                String title = cursor.getString(1);
                String description = cursor.getString(2);
                long beginTime = cursor.getLong(3);

                if (title == null) continue;

                Matcher matcher = ShiftAlarmUtils.SHIFT_TITLE_PATTERN.matcher(title.trim());
                if (matcher.matches()) {
                    processShiftEvent(instanceId, title, description, beginTime);
                }
            }
        } catch (SecurityException e) {
            LogUtils.e(TAG, "Missing READ_CALENDAR permission during update", e);
        } catch (Exception e) {
            LogUtils.e(TAG, "Error querying calendar", e);
        }
    }

    private void processShiftEvent(long calendarInstanceId, String title, String description, long beginTime) {
        // Calculate trigger time
        int offsetMinutes = getOffsetMinutes(description);
        Calendar alarmTime = Calendar.getInstance();
        alarmTime.setTimeInMillis(beginTime);
        alarmTime.add(Calendar.MINUTE, offsetMinutes);

        if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }

        // Holiday Check
        boolean ignoreHoliday = description != null && description.contains(ShiftAlarmUtils.IGNORE_HOLIDAY_KEYWORD);
        if (!ignoreHoliday) {
            // Fail-safe to ring: If checking fails, default to true.
            try {
                if (!HolidayUtils.shouldAlarmRing(mContext, HolidayUtils.HOLIDAY_OPTION_SKIP_HOLIDAY, new Weekdays(0x7F), alarmTime)) {
                    LogUtils.i(TAG, "Skipping shift alarm due to holiday: " + title);
                    return;
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "Holiday check failed, defaulting to ring", e);
            }
        }

        // Conflict Resolution
        if (hasConflictingManualAlarm(alarmTime)) {
            LogUtils.i(TAG, "Shift alarm suppressed by manual alarm: " + title);
            return;
        }

        // Create Ephemeral Alarm Instance
        // Strictly negative ID range: [-10000 to -19999]
        long ephemeralId = ShiftAlarmUtils.EPHEMERAL_ID_START - (calendarInstanceId % 10000);
        AlarmInstance instance = new AlarmInstance(alarmTime);
        instance.mId = ephemeralId;
        instance.mLabel = title.trim();
        if (!instance.mLabel.endsWith("闹钟")) {
            instance.mLabel += "闹钟";
        }
        instance.mAlarmId = null;

        inheritSettings(instance);

        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = instance.createContentValues();
        try {
            cr.insert(ClockContract.InstancesColumns.CONTENT_URI, values);
            AlarmStateManager.registerInstance(mContext, instance, true);
            LogUtils.i(TAG, "Successfully scheduled shift alarm: " + instance.mLabel + " ID: " + ephemeralId);
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to insert shift alarm instance", e);
        }
    }

    private int getOffsetMinutes(String description) {
        if (description != null) {
            Matcher matcher = ShiftAlarmUtils.OFFSET_DESCRIPTION_PATTERN.matcher(description);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        try {
            return Settings.Secure.getInt(mContext.getContentResolver(),
                ShiftAlarmUtils.SETTINGS_SECURE_SHIFT_ALARM_OFFSET,
                -ShiftAlarmUtils.DEFAULT_OFFSET_MINUTES);
        } catch (Exception e) {
            return -ShiftAlarmUtils.DEFAULT_OFFSET_MINUTES;
        }
    }

    private boolean hasConflictingManualAlarm(Calendar shiftAlarmTime) {
        ContentResolver cr = mContext.getContentResolver();
        List<AlarmInstance> instances = AlarmInstance.getInstances(cr, null);
        long shiftMillis = shiftAlarmTime.getTimeInMillis();
        long bufferMillis = ShiftAlarmUtils.CONFLICT_RESOLUTION_BUFFER_MINUTES * 60 * 1000L;

        for (AlarmInstance instance : instances) {
            if (ShiftAlarmUtils.isEphemeralId(instance.mId)) continue;

            long instanceMillis = instance.getAlarmTime().getTimeInMillis();
            if (Math.abs(instanceMillis - shiftMillis) <= bufferMillis) {
                return true;
            }
        }
        return false;
    }

    private void inheritSettings(AlarmInstance instance) {
        ContentResolver cr = mContext.getContentResolver();
        List<Alarm> alarms = Alarm.getAlarms(cr, ClockContract.AlarmsColumns.ENABLED + "=1");
        if (!alarms.isEmpty()) {
            Alarm best = alarms.get(0);
            for (Alarm a : alarms) {
                if (a.id > best.id) best = a;
            }
            instance.mRingtone = best.alert;
            instance.mVibrate = best.vibrate;
            instance.mVibrationPattern = best.vibrationPattern;
            instance.mAlarmVolume = best.alarmVolume;
        } else {
            instance.mRingtone = DataModel.getDataModel().getAlarmRingtoneUriFromSettings();
            instance.mVibrate = true;
            instance.mAlarmVolume = 11;
        }
    }

    private class ShiftCalendarObserver extends ContentObserver {
        public ShiftCalendarObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            LogUtils.i(TAG, "Calendar changed, updating shift alarms");
            updateShiftAlarms();
        }
    }
}
