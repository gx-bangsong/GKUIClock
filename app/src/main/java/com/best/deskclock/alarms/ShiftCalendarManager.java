/*
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.alarms;

import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.best.deskclock.R;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.holiday.HolidayUtils;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.utils.LogUtils;
import com.best.deskclock.utils.RingtoneUtils;
import com.best.deskclock.utils.ShiftAlarmUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors compatible system-calendar events into standalone {@link AlarmInstance} rows.
 * Also synchronizes matching calendar events into the Advanced Shift Panel (ASP) overrides.
 */
public final class ShiftCalendarManager {

    private static final String TAG = "ShiftCalendarManager";
    private static final long SYNC_DEBOUNCE_SECONDS = 2;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    public interface SyncListener {
        void onShiftSyncChanged();
    }

    @SuppressLint("StaticFieldLeak")
    private static volatile ShiftCalendarManager sInstance;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ShiftCalendarObserver mObserver;
    private final ScheduledExecutorService mExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final Set<SyncListener> mListeners = new CopyOnWriteArraySet<>();

    private ScheduledFuture<?> mPendingSync;
    private boolean mObserverRegistered;

    private volatile long mLastSyncTime;
    private volatile boolean mLastSyncSuccess;
    private volatile boolean mSyncInProgress;
    private volatile int mLastSyncCount;
    private volatile String mLastSyncError;

    public static class CalendarInfo {
        public String id;
        public String displayName;
        public String accountName;
        public int color;

        public CalendarInfo(String id, String displayName, String accountName, int color) {
            this.id = id;
            this.displayName = displayName;
            this.accountName = accountName;
            this.color = color;
        }
    }

    private ShiftCalendarManager(Context context) {
        mContext = context.getApplicationContext();
        mObserver = new ShiftCalendarObserver(mMainHandler);
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

    public void addListener(SyncListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(SyncListener listener) {
        mListeners.remove(listener);
    }

    public synchronized void registerObserver() {
        if (!isEnabled() || !hasCalendarPermission()) {
            unregisterObserver();
            ShiftCalendarSyncJobService.cancel(mContext);
            return;
        }

        ShiftCalendarSyncJobService.schedule(mContext);
        if (mObserverRegistered) {
            return;
        }

        try {
            mContext.getContentResolver().registerContentObserver(
                    CalendarContract.Events.CONTENT_URI, true, mObserver);
            mObserverRegistered = true;
            LogUtils.i(TAG + ": Calendar shift observer registered");
        } catch (SecurityException e) {
            LogUtils.e(TAG + ": Unable to register calendar observer", e);
        }
    }

    public synchronized void unregisterObserver() {
        ShiftCalendarSyncJobService.cancel(mContext);
        if (!mObserverRegistered) {
            return;
        }
        try {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        } catch (RuntimeException e) {
            LogUtils.e(TAG + ": Unable to unregister calendar observer", e);
        } finally {
            mObserverRegistered = false;
        }
    }

    /** Requests a coalesced background synchronization. */
    public synchronized void requestSync() {
        scheduleSync(SYNC_DEBOUNCE_SECONDS);
    }

    /** Requests an immediate background synchronization, typically from explicit user action. */
    public synchronized void requestImmediateSync() {
        scheduleSync(0);
    }

    /** Backward-compatible name used by lifecycle integration. */
    public void updateShiftAlarms() {
        requestSync();
    }

    private void scheduleSync(long delaySeconds) {
        if (mPendingSync != null) {
            mPendingSync.cancel(false);
        }
        mPendingSync = mExecutor.schedule(this::performSync, delaySeconds, TimeUnit.SECONDS);
    }

    public long getLastSyncTime() {
        return mLastSyncTime;
    }

    public boolean isLastSyncSuccess() {
        return mLastSyncSuccess;
    }

    public boolean isSyncInProgress() {
        return mSyncInProgress;
    }

    public int getLastSyncCount() {
        return mLastSyncCount;
    }

    public String getLastSyncError() {
        return mLastSyncError;
    }

    private void performSync() {
        mSyncInProgress = true;
        dispatchStatusChanged();

        try {
            // Also run background sync for all rotation-based ASP calendar-synced alarms
            triggerSyncAllAlarms();

            if (!isEnabled()) {
                unregisterObserver();
                removeAllCalendarInstances();
                setSuccessfulStatus(0);
                return;
            }

            if (!hasCalendarPermission()) {
                unregisterObserver();
                setFailedStatus("Missing calendar permission");
                return;
            }

            final ContentResolver resolver = mContext.getContentResolver();
            final List<AlarmInstance> existing = AlarmInstance.getInstances(resolver,
                    AlarmInstance.SOURCE_TYPE + " = ?",
                    String.valueOf(AlarmInstance.SOURCE_TYPE_CALENDAR));
            final List<AlarmInstance> standardInstances = AlarmInstance.getInstances(resolver,
                    AlarmInstance.SOURCE_TYPE + " = ?",
                    String.valueOf(AlarmInstance.SOURCE_TYPE_STANDARD));
            final Alarm inheritedSettings = findSettingsTemplate(resolver);
            final Map<String, DesiredShift> desired = queryDesiredShifts(standardInstances);

            reconcile(existing, desired, inheritedSettings);
            setSuccessfulStatus(desired.size());
        } catch (SecurityException e) {
            LogUtils.e(TAG + ": Calendar permission was revoked during synchronization", e);
            unregisterObserver();
            setFailedStatus("Missing calendar permission");
        } catch (Exception e) {
            LogUtils.e(TAG + ": Calendar shift synchronization failed", e);
            final String message = e.getMessage();
            setFailedStatus(message == null || message.isEmpty()
                    ? e.getClass().getSimpleName()
                    : message);
        } finally {
            mSyncInProgress = false;
            mLastSyncTime = System.currentTimeMillis();
            dispatchStatusChanged();
        }
    }

    private Map<String, DesiredShift> queryDesiredShifts(List<AlarmInstance> standardInstances) {
        final long now = System.currentTimeMillis();
        final long windowEnd = now + ShiftAlarmUtils.SYNC_WINDOW_DAYS * MILLIS_PER_DAY;
        final long maximumOffsetMillis =
                ShiftAlarmUtils.MAX_ABSOLUTE_OFFSET_MINUTES * 60L * 1000L;

        final Uri.Builder uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uriBuilder, now - maximumOffsetMillis);
        ContentUris.appendId(uriBuilder, windowEnd + maximumOffsetMillis);

        final String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Instances.RRULE
        };

        final int fallbackOffset = readDefaultOffset();
        final Map<String, DesiredShift> desired = new HashMap<>();
        try (Cursor cursor = mContext.getContentResolver().query(
                uriBuilder.build(), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) {
                throw new IllegalStateException("Calendar provider returned no result");
            }

            while (cursor.moveToNext()) {
                final String title = cursor.getString(0);
                final String description = cursor.getString(1);
                final long beginTime = cursor.getLong(2);
                final long eventId = cursor.getLong(3);
                final long originalInstanceTime = cursor.getLong(4);
                final String rrule = cursor.getString(5);

                if (!ShiftAlarmUtils.isShiftEvent(title, description)) {
                    continue;
                }

                final int offsetMinutes =
                        ShiftAlarmUtils.parseOffsetMinutes(description, fallbackOffset);
                final long shiftAlarmMillis = beginTime + offsetMinutes * 60L * 1000L;
                if (shiftAlarmMillis <= now || shiftAlarmMillis > windowEnd) {
                    continue;
                }

                final Calendar alarmTime = Calendar.getInstance();
                alarmTime.setTimeInMillis(shiftAlarmMillis);

                if (!shouldRingOnHoliday(description, alarmTime)) {
                    continue;
                }
                if (hasConflictingStandardAlarm(standardInstances, shiftAlarmMillis)) {
                    continue;
                }

                final String label = title.trim();
                final String syncKey = ShiftAlarmUtils.createSyncKey(eventId, originalInstanceTime,
                        description);
                final String legacySyncKey = eventId + "_" + beginTime;

                desired.put(syncKey, new DesiredShift(syncKey, legacySyncKey, label, alarmTime));
            }
        }
        return desired;
    }

    private boolean shouldRingOnHoliday(String description, Calendar alarmTime) {
        if (ShiftAlarmUtils.shouldIgnoreHoliday(description)) {
            return true;
        }
        try {
            return HolidayUtils.shouldAlarmRing(mContext,
                    HolidayUtils.HOLIDAY_OPTION_SKIP_HOLIDAY, Weekdays.fromBits(0x7F),
                    alarmTime);
        } catch (Exception e) {
            LogUtils.e(TAG + ": Holiday lookup failed; calendar shift will still ring", e);
            return true;
        }
    }

    private static boolean hasConflictingStandardAlarm(List<AlarmInstance> standardInstances,
                                                        long shiftAlarmMillis) {
        final long bufferMillis =
                ShiftAlarmUtils.CONFLICT_RESOLUTION_BUFFER_MINUTES * 60L * 1000L;
        for (AlarmInstance instance : standardInstances) {
            if (instance.mAlarmState == AlarmInstance.DISMISSED_STATE
                    || instance.mAlarmState == AlarmInstance.PREDISMISSED_STATE
                    || instance.mAlarmState == AlarmInstance.MISSED_STATE) {
                continue;
            }
            if (Math.abs(instance.getAlarmTime().getTimeInMillis() - shiftAlarmMillis)
                    <= bufferMillis) {
                return true;
            }
        }
        return false;
    }

    private void reconcile(List<AlarmInstance> existing,
                           Map<String, DesiredShift> desired,
                           Alarm inheritedSettings) {
        final ContentResolver resolver = mContext.getContentResolver();
        final Map<String, AlarmInstance> existingByKey = new HashMap<>();
        final Set<Long> duplicateIds = new HashSet<>();

        for (AlarmInstance instance : existing) {
            if (instance.mSyncKey == null || existingByKey.containsKey(instance.mSyncKey)) {
                duplicateIds.add(instance.mId);
            } else {
                existingByKey.put(instance.mSyncKey, instance);
            }
        }

        for (DesiredShift target : desired.values()) {
            AlarmInstance instance = existingByKey.remove(target.syncKey);
            if (instance == null) {
                instance = existingByKey.remove(target.legacySyncKey);
            }
            if (instance == null) {
                insertInstance(target, inheritedSettings);
            } else {
                updateInstanceIfNeeded(instance, target, inheritedSettings);
            }
        }

        for (AlarmInstance obsolete : existingByKey.values()) {
            removeUnlessActive(obsolete);
        }
        for (AlarmInstance instance : existing) {
            if (duplicateIds.contains(instance.mId)) {
                removeUnlessActive(instance);
            }
        }

        AlarmStateManager.refreshNextAlarm(mContext);
    }

    private void insertInstance(DesiredShift target, Alarm inheritedSettings) {
        final AlarmInstance instance = new AlarmInstance(target.alarmTime);
        instance.mLabel = target.label;
        instance.mAlarmId = null;
        instance.mSourceType = AlarmInstance.SOURCE_TYPE_CALENDAR;
        instance.mSyncKey = target.syncKey;
        instance.mSyncState = AlarmInstance.SYNC_STATE_NONE;
        applyInheritedSettings(instance, inheritedSettings);

        instance.addInstance(mContext.getContentResolver());
        AlarmStateManager.registerInstance(mContext, instance, false);
        LogUtils.i(TAG + ": Created calendar shift instance " + instance.mId +
                " for " + target.syncKey);
    }

    private void updateInstanceIfNeeded(AlarmInstance instance, DesiredShift target,
                                        Alarm inheritedSettings) {
        if (instance.mAlarmState == AlarmInstance.FIRED_STATE
                || instance.mAlarmState == AlarmInstance.SNOOZE_STATE
                || instance.mAlarmState == AlarmInstance.PREDISMISSED_STATE
                || instance.mAlarmState == AlarmInstance.DISMISSED_STATE
                || instance.mAlarmState == AlarmInstance.MISSED_STATE) {
            return;
        }

        final AlarmInstance updated = new AlarmInstance(instance);
        updated.setAlarmTime(target.alarmTime);
        updated.mLabel = target.label;
        updated.mSourceType = AlarmInstance.SOURCE_TYPE_CALENDAR;
        updated.mSyncKey = target.syncKey;
        applyInheritedSettings(updated, inheritedSettings);

        if (hasSameScheduleAndSettings(instance, updated)) {
            return;
        }

        updated.updateInstance(mContext.getContentResolver());
        AlarmStateManager.registerInstance(mContext, updated, false);
        LogUtils.i(TAG + ": Updated calendar shift instance " + updated.mId);
    }

    private static boolean hasSameScheduleAndSettings(AlarmInstance left,
                                                       AlarmInstance right) {
        return left.getAlarmTime().getTimeInMillis() == right.getAlarmTime().getTimeInMillis()
                && Objects.equals(left.mLabel, right.mLabel)
                && Objects.equals(left.mRingtone, right.mRingtone)
                && left.mVibrate == right.mVibrate
                && Objects.equals(left.mVibrationPattern, right.mVibrationPattern)
                && left.mFlash == right.mFlash
                && left.mAutoSilenceDuration == right.mAutoSilenceDuration
                && left.mSnoozeDuration == right.mSnoozeDuration
                && left.mCrescendoDuration == right.mCrescendoDuration
                && left.mAlarmVolume == right.mAlarmVolume
                && left.mMissedAlarmRepeatLimit == right.mMissedAlarmRepeatLimit;
    }

    private static void applyInheritedSettings(AlarmInstance instance, Alarm alarm) {
        if (alarm == null) {
            instance.mRingtone = DataModel.getDataModel().getAlarmRingtoneUriFromSettings();
            instance.mVibrate = true;
            return;
        }

        instance.mRingtone = RingtoneUtils.isRandomRingtone(alarm.alert)
                ? RingtoneUtils.getRandomRingtoneUri()
                : RingtoneUtils.isRandomCustomRingtone(alarm.alert)
                ? RingtoneUtils.getRandomCustomRingtoneUri()
                : alarm.alert;
        instance.mVibrate = alarm.vibrate;
        instance.mVibrationPattern = alarm.vibrationPattern;
        instance.mFlash = alarm.flash;
        instance.mAutoSilenceDuration = alarm.autoSilenceDuration;
        instance.mSnoozeDuration = alarm.snoozeDuration;
        instance.mMissedAlarmRepeatLimit = alarm.missedAlarmRepeatLimit;
        instance.mCrescendoDuration = alarm.crescendoDuration;
        instance.mAlarmVolume = alarm.alarmVolume;
    }

    private static Alarm findSettingsTemplate(ContentResolver resolver) {
        final List<Alarm> alarms = Alarm.getAlarms(resolver,
                Alarm.ENABLED + " = ?", "1");
        Alarm latest = null;
        for (Alarm alarm : alarms) {
            if (latest == null || alarm.id > latest.id) {
                latest = alarm;
            }
        }
        return latest;
    }

    private void removeUnlessActive(AlarmInstance instance) {
        if (instance.mAlarmState == AlarmInstance.FIRED_STATE
                || instance.mAlarmState == AlarmInstance.SNOOZE_STATE) {
            return;
        }
        AlarmStateManager.unregisterInstance(mContext, instance);
        AlarmInstance.deleteInstance(mContext.getContentResolver(), instance.mId);
    }

    private void removeAllCalendarInstances() {
        final List<AlarmInstance> instances = AlarmInstance.getInstances(
                mContext.getContentResolver(), AlarmInstance.SOURCE_TYPE + " = ?",
                String.valueOf(AlarmInstance.SOURCE_TYPE_CALENDAR));
        for (AlarmInstance instance : instances) {
            AlarmStateManager.deleteInstanceAndUpdateParent(mContext, instance);
        }
        AlarmStateManager.refreshNextAlarm(mContext);
    }

    private int readDefaultOffset() {
        try {
            return Settings.Secure.getInt(mContext.getContentResolver(),
                    ShiftAlarmUtils.SETTINGS_SECURE_SHIFT_ALARM_OFFSET,
                    ShiftAlarmUtils.DEFAULT_OFFSET_MINUTES);
        } catch (RuntimeException e) {
            return ShiftAlarmUtils.DEFAULT_OFFSET_MINUTES;
        }
    }

    private boolean isEnabled() {
        final SharedPreferences preferences = getDefaultSharedPreferences(mContext);
        return SettingsDAO.isCalendarShiftSyncEnabled(preferences);
    }

    private boolean hasCalendarPermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void setSuccessfulStatus(int count) {
        mLastSyncSuccess = true;
        mLastSyncCount = count;
        mLastSyncError = null;
    }

    private void setFailedStatus(String error) {
        mLastSyncSuccess = false;
        mLastSyncCount = 0;
        mLastSyncError = error;
    }

    private void dispatchStatusChanged() {
        mMainHandler.post(() -> {
            for (SyncListener listener : mListeners) {
                listener.onShiftSyncChanged();
            }
        });
    }

    private final class ShiftCalendarObserver extends ContentObserver {
        ShiftCalendarObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            LogUtils.i(TAG + ": Calendar changed; scheduling shift reconciliation");
            requestSync();
        }
    }

    private static final class DesiredShift {
        final String syncKey;
        final String legacySyncKey;
        final String label;
        final Calendar alarmTime;

        DesiredShift(String syncKey, String legacySyncKey, String label,
                     Calendar alarmTime) {
            this.syncKey = syncKey;
            this.legacySyncKey = legacySyncKey;
            this.label = label;
            this.alarmTime = (Calendar) alarmTime.clone();
        }
    }

    // =========================================================================
    // Integrated ASP (Advanced Shift Panel) Sync Methods
    // =========================================================================

    public List<CalendarInfo> getSystemCalendars() {
        List<CalendarInfo> calendars = new ArrayList<>();
        try {
            Uri uri = CalendarContract.Calendars.CONTENT_URI;
            String[] projection = new String[]{
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR
            };
            Cursor cursor = mContext.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String accountName = cursor.getString(2);
                    int color = cursor.getInt(3);
                    calendars.add(new CalendarInfo(id, displayName, accountName, color));
                }
                cursor.close();
            }
        } catch (Exception e) {
            LogUtils.e(TAG + ": Failed to query system calendars", e);
        }
        return calendars;
    }

    public synchronized boolean syncAlarmWithCalendar(Alarm alarm) {
        if (alarm == null || TextUtils.isEmpty(alarm.rotationPayload)) {
            return false;
        }

        String[] parts = alarm.rotationPayload.split("\\|");
        if (parts.length < 6) {
            return false;
        }

        boolean calendarSyncEnabled = false;
        String targetCalendarId = "all";

        if (parts.length >= 8) {
            calendarSyncEnabled = Boolean.parseBoolean(parts[6]);
            targetCalendarId = parts[7];
        }

        if (!calendarSyncEnabled) {
            return false;
        }

        LogUtils.i(TAG + ": Syncing Alarm ID " + alarm.id + " with calendar source: " + targetCalendarId);

        try {
            int cycleLength = Integer.parseInt(parts[1]);
            long anchorDateMs = Long.parseLong(parts[2]);
            boolean holidaySkip = Boolean.parseBoolean(parts[3]);
            String minutesPerDayStr = parts[4];
            
            Type type = new TypeToken<Map<String, Double>>(){}.getType();
            Map<String, Double> overrides = new Gson().fromJson(parts[5], type);
            if (overrides == null) {
                overrides = new HashMap<>();
            }

            long now = System.currentTimeMillis();
            long end = now + (30L * 24 * 60 * 60 * 1000L);

            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, now);
            ContentUris.appendId(builder, end);

            String[] projection = {
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.CALENDAR_ID
            };

            ContentResolver cr = mContext.getContentResolver();
            try (Cursor cursor = cr.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String title = cursor.getString(0);
                        String description = cursor.getString(1);
                        long beginTime = cursor.getLong(2);
                        String calendarId = cursor.getString(3);

                        if (title == null) continue;

                        if (!"all".equals(targetCalendarId) && !targetCalendarId.equals(calendarId)) {
                            continue;
                        }

                        if (ShiftAlarmUtils.isShiftEvent(title, description)) {
                            int offsetMinutes = ShiftAlarmUtils.parseOffsetMinutes(description, -90);
                            long alarmTimeMs = beginTime + (offsetMinutes * 60 * 1000L);

                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(alarmTimeMs);

                            String dateKey = String.format(Locale.US, "%04d-%02d-%02d",
                                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

                            int minuteVal = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                            overrides.put(dateKey, (double) minuteVal);
                            LogUtils.d(TAG + ": Synced calendar event \"" + title + "\" -> " + dateKey + " " + cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE));
                        }
                    }
                }
            }

            String newPayload = String.format(Locale.US, "SHIFT_ROTATION_V2|%d|%d|%b|%s|%s|%b|%s",
                    cycleLength, anchorDateMs, holidaySkip, minutesPerDayStr, new Gson().toJson(overrides), calendarSyncEnabled, targetCalendarId);

            if (!newPayload.equals(alarm.rotationPayload)) {
                alarm.rotationPayload = newPayload;
                return true;
            }

        } catch (Exception e) {
            LogUtils.e(TAG + ": Error syncing alarm with calendar", e);
        }

        return false;
    }

    public synchronized void triggerSyncAllAlarms() {
        new Thread(() -> {
            try {
                ContentResolver cr = mContext.getContentResolver();
                List<Alarm> alarms = Alarm.getAlarms(cr, null);
                boolean updatedAny = false;
                for (Alarm alarm : alarms) {
                    if (alarm.enabled && !TextUtils.isEmpty(alarm.rotationPayload) && alarm.rotationPayload.startsWith("SHIFT_ROTATION_V2")) {
                        String[] parts = alarm.rotationPayload.split("\\|");
                        if (parts.length >= 8 && Boolean.parseBoolean(parts[6])) {
                            boolean changed = syncAlarmWithCalendar(alarm);
                            if (changed) {
                                cr.update(Alarm.getContentUri(alarm.id), alarm.createContentValues(), null, null);
                                updatedAny = true;
                            }
                        }
                    }
                }
                if (updatedAny) {
                    LogUtils.i(TAG + ": Background sync completed, updated rotation alarms.");
                    mMainHandler.post(() -> AlarmStateManager.refreshNextAlarm(mContext));
                }
            } catch (Exception e) {
                LogUtils.e(TAG + ": Error during background sync of all alarms", e);
            }
        }).start();
    }
}
