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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public final class ShiftCalendarManager {

    private static final String TAG = "ShiftCalendarManager";

    @SuppressLint("StaticFieldLeak")
    private static ShiftCalendarManager sInstance;
    private final Context mContext;
    private final ShiftCalendarObserver mObserver;
    private final ScheduledExecutorService mExecutorService;
    private ScheduledFuture<?> mPendingSyncTask = null;
    private boolean mIsObserverRegistered = false;

    // Last sync status info
    private long mLastSyncTime = 0;
    private boolean mLastSyncSuccess = false;
    private int mLastSyncCount = 0;
    private String mLastSyncError = null;

    private ShiftCalendarManager(Context context) {
        mContext = context.getApplicationContext();
        mObserver = new ShiftCalendarObserver(new Handler(Looper.getMainLooper()));
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
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

    public synchronized void registerObserver() {
        SharedPreferences prefs = getDefaultSharedPreferences(mContext);
        boolean enabled = SettingsDAO.isCalendarShiftSyncEnabled(prefs);
        if (!enabled) {
            unregisterObserver();
            return;
        }

        if (mIsObserverRegistered) return;
        try {
            mContext.getContentResolver().registerContentObserver(
                    CalendarContract.Events.CONTENT_URI, true, mObserver);
            mIsObserverRegistered = true;
            LogUtils.i(TAG, "ShiftCalendarObserver registered dynamically");
        } catch (SecurityException e) {
            LogUtils.e(TAG, "Failed to register ShiftCalendarObserver: missing permissions", e);
        }
    }

    public synchronized void unregisterObserver() {
        if (!mIsObserverRegistered) return;
        try {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mIsObserverRegistered = false;
            LogUtils.i(TAG, "ShiftCalendarObserver unregistered dynamically");
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to unregister ShiftCalendarObserver", e);
        }
    }

    public synchronized void updateShiftAlarms() {
        triggerSyncDebounced();
    }

    public synchronized void triggerSyncDebounced() {
        if (mPendingSyncTask != null) {
            mPendingSyncTask.cancel(false);
        }
        mPendingSyncTask = mExecutorService.schedule(this::performSync, 2, TimeUnit.SECONDS);
        LogUtils.i(TAG, "Calendar Shift Sync debounced by 2 seconds.");
    }

    public long getLastSyncTime() {
        return mLastSyncTime;
    }

    public boolean isLastSyncSuccess() {
        return mLastSyncSuccess;
    }

    public int getLastSyncCount() {
        return mLastSyncCount;
    }

    public String getLastSyncError() {
        return mLastSyncError;
    }

    private synchronized void performSync() {
        SharedPreferences prefs = getDefaultSharedPreferences(mContext);
        boolean enabled = SettingsDAO.isCalendarShiftSyncEnabled(prefs);
        ContentResolver cr = mContext.getContentResolver();

        // 1. Clean up if disabled
        if (!enabled) {
            try {
                List<AlarmInstance> currentInstances = AlarmInstance.getInstances(cr, ClockContract.InstancesColumns.SOURCE_TYPE + "=1");
                for (AlarmInstance instance : currentInstances) {
                    LogUtils.i(TAG, "Sync disabled. Cleaning up Calendar Shift instance: " + instance.mId);
                    AlarmStateManager.unregisterInstance(mContext, instance);
                    AlarmInstance.deleteInstance(cr, instance.mId);
                }
                mLastSyncSuccess = true;
                mLastSyncCount = 0;
                mLastSyncError = null;
            } catch (Exception e) {
                LogUtils.e(TAG, "Error cleaning up instances when disabled", e);
            }
            // Trigger rotation sync
            RotationScheduler.scheduleRotationAlarms(mContext);
            return;
        }

        LogUtils.i(TAG, "Executing background idempotent Calendar Shift Sync...");
        mLastSyncTime = System.currentTimeMillis();

        // Always run rotation scheduler sync too
        RotationScheduler.scheduleRotationAlarms(mContext);

        // 2. Query all existing Calendar Shift instances from DB (source_type = 1)
        List<AlarmInstance> existingInstances;
        try {
            existingInstances = AlarmInstance.getInstances(cr, ClockContract.InstancesColumns.SOURCE_TYPE + "=1");
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to query existing instances", e);
            mLastSyncSuccess = false;
            mLastSyncError = e.getMessage();
            return;
        }

        Map<String, AlarmInstance> existingMap = new HashMap<>();
        for (AlarmInstance inst : existingInstances) {
            if (inst.mSyncKey != null) {
                existingMap.put(inst.mSyncKey, inst);
            }
        }

        // 3. Query system Calendar Instances for the next 7 days
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
                CalendarContract.Instances.EVENT_ID
        };

        Set<String> processedSyncKeys = new HashSet<>();
        int successfullySyncedCount = 0;

        try (Cursor cursor = cr.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long instanceId = cursor.getLong(0);
                    String title = cursor.getString(1);
                    String description = cursor.getString(2);
                    long beginTime = cursor.getLong(3);
                    long eventId = cursor.getLong(4);

                    if (title == null) continue;

                    // Support title regex & structured tags
                    boolean isShift = false;
                    Matcher matcher = ShiftAlarmUtils.SHIFT_TITLE_PATTERN.matcher(title.trim());
                    if (matcher.matches()) {
                        isShift = true;
                    } else if (description != null && (description.contains("X-GKUI-SHIFT-ID:") || description.contains("X-GKUI-SHIFT:"))) {
                        isShift = true;
                    }

                    if (isShift) {
                        String syncKey = eventId + "_" + beginTime;
                        processedSyncKeys.add(syncKey);
                        boolean ok = processMatchedShiftEvent(syncKey, title, description, beginTime, existingMap);
                        if (ok) {
                            successfullySyncedCount++;
                        }
                    }
                }
            }
            mLastSyncSuccess = true;
            mLastSyncCount = successfullySyncedCount;
            mLastSyncError = null;
        } catch (SecurityException e) {
            LogUtils.e(TAG, "Missing READ_CALENDAR permission during sync", e);
            mLastSyncSuccess = false;
            mLastSyncError = "Missing Calendar Permission";
        } catch (Exception e) {
            LogUtils.e(TAG, "Error querying calendar instances during sync", e);
            mLastSyncSuccess = false;
            mLastSyncError = e.getMessage();
        }

        // 4. Delete existing local instances that are no longer present in calendar
        for (AlarmInstance inst : existingInstances) {
            if (inst.mSyncKey != null && !processedSyncKeys.contains(inst.mSyncKey)) {
                // Keep active / ringing / snoozing instances
                if (inst.mAlarmState == AlarmInstance.FIRED_STATE || inst.mAlarmState == AlarmInstance.SNOOZE_STATE) {
                    LogUtils.i(TAG, "Preserving active/snoozing obsolete shift instance: " + inst.mId);
                    continue;
                }
                LogUtils.i(TAG, "Deleting obsolete calendar shift instance (removed from calendar): " + inst.mId + ", SyncKey: " + inst.mSyncKey);
                AlarmStateManager.unregisterInstance(mContext, inst);
                AlarmInstance.deleteInstance(cr, inst.mId);
            }
        }
    }

    private boolean processMatchedShiftEvent(String syncKey, String title, String description, long beginTime, Map<String, AlarmInstance> existingMap) {
        int offsetMinutes = getOffsetMinutes(description);
        Calendar alarmTime = Calendar.getInstance();
        alarmTime.setTimeInMillis(beginTime);
        alarmTime.add(Calendar.MINUTE, offsetMinutes);

        if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) {
            return false;
        }

        // Holiday Check
        boolean ignoreHoliday = false;
        if (description != null) {
            if (description.contains(ShiftAlarmUtils.IGNORE_HOLIDAY_KEYWORD)
                    || description.contains("X-GKUI-IGNORE-HOLIDAY:true")
                    || description.contains("X-GKUI-IGNORE-HOLIDAY: true")) {
                ignoreHoliday = true;
            }
        }

        if (!ignoreHoliday) {
            try {
                if (!HolidayUtils.shouldAlarmRing(mContext, HolidayUtils.HOLIDAY_OPTION_SKIP_HOLIDAY, new Weekdays(0x7F), alarmTime)) {
                    LogUtils.i(TAG, "Skipping shift alarm due to holiday: " + title);
                    return false;
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "Holiday check failed, defaulting to ring", e);
            }
        }

        // Conflict Resolution
        if (hasConflictingManualAlarm(alarmTime)) {
            LogUtils.i(TAG, "Shift alarm suppressed by manual alarm: " + title);
            return false;
        }

        String label = title.trim();
        if (!label.endsWith("闹钟")) {
            label += "闹钟";
        }

        ContentResolver cr = mContext.getContentResolver();
        AlarmInstance existing = existingMap.get(syncKey);

        if (existing == null) {
            // INSERT: Create new instance using standard auto-increment positive ID!
            AlarmInstance instance = new AlarmInstance(alarmTime);
            instance.mLabel = label;
            instance.mAlarmId = null;
            instance.mSourceType = 1; // Calendar Shift
            instance.mSyncKey = syncKey;
            instance.mSyncState = 0;

            inheritSettings(instance);

            ContentValues values = instance.createContentValues();
            try {
                Uri newUri = cr.insert(ClockContract.InstancesColumns.CONTENT_URI, values);
                if (newUri != null) {
                    instance.mId = AlarmInstance.getId(newUri);
                    AlarmStateManager.registerInstance(mContext, instance, true);
                    LogUtils.i(TAG, "Scheduled new shift alarm: " + instance.mLabel + " ID: " + instance.mId + ", SyncKey: " + syncKey);
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "Failed to insert shift alarm instance", e);
                return false;
            }
        } else {
            // RECONCILE: check if time or label changed
            Calendar existingTime = existing.getAlarmTime();
            boolean timeChanged = existingTime.getTimeInMillis() != alarmTime.getTimeInMillis();
            boolean labelChanged = !existing.mLabel.equals(label);

            if (timeChanged || labelChanged) {
                if (existing.mAlarmState == AlarmInstance.FIRED_STATE || existing.mAlarmState == AlarmInstance.SNOOZE_STATE) {
                    LogUtils.i(TAG, "Shift alarm is currently active/snoozing. Skipping modification: " + existing.mId);
                    return true;
                }

                LogUtils.i(TAG, "Updating existing shift alarm ID " + existing.mId + " due to calendar event change. Time changed: " + timeChanged + ", Label changed: " + labelChanged);
                existing.setAlarmTime(alarmTime);
                existing.mLabel = label;
                existing.updateInstance(cr);

                AlarmStateManager.registerInstance(mContext, existing, true);
            } else {
                LogUtils.v(TAG, "Shift alarm ID " + existing.mId + " is already up to date.");
            }
        }
        return true;
    }

    private int getOffsetMinutes(String description) {
        if (description != null) {
            // Support structured tags first
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("X-GKUI-ALARM-OFFSET:\\s*(-?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(description);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
            // Fall back to standard Offset Description Pattern
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
            if (instance.mSourceType != 0) continue; // Skip shift/rotation instances

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
            LogUtils.i(TAG, "Calendar changed, triggering debounced shift sync");
            triggerSyncDebounced();
        }
    }
}
