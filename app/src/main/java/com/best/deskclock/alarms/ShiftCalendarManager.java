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

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 *
 * <p>The synchronization is diff based: a stable calendar occurrence key is stored with every
 * instance, unchanged instances retain their alarm state, and only changed or removed events are
 * rescheduled. PR #32's local rotation engine remains the owner of regular alarm templates; this
 * class only owns instances whose source is {@link AlarmInstance#SOURCE_TYPE_CALENDAR}.</p>
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

        // Include events just outside the alarm window because their signed offset can move the
        // actual alarm into the next seven days.
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
                final long originalInstanceTime = cursor.isNull(4) ? 0 : cursor.getLong(4);
                final String recurrenceRule = cursor.getString(5);

                if (!ShiftAlarmUtils.isShiftEvent(title, description)) {
                    continue;
                }

                final int offsetMinutes =
                        ShiftAlarmUtils.parseOffsetMinutes(description, fallbackOffset);
                final Calendar alarmTime = Calendar.getInstance();
                alarmTime.setTimeInMillis(beginTime);
                alarmTime.add(Calendar.MINUTE, offsetMinutes);
                alarmTime.set(Calendar.SECOND, 0);
                alarmTime.set(Calendar.MILLISECOND, 0);

                final long alarmMillis = alarmTime.getTimeInMillis();
                if (alarmMillis <= now || alarmMillis > windowEnd) {
                    continue;
                }

                final Calendar shiftDate = Calendar.getInstance();
                shiftDate.setTimeInMillis(beginTime);
                if (!ShiftAlarmUtils.shouldIgnoreHoliday(description)
                        && !shouldRingOnShiftDate(shiftDate)) {
                    continue;
                }

                if (hasConflictingStandardAlarm(standardInstances, alarmMillis)) {
                    continue;
                }

                // Non-recurring events keep the same key when DTSTART changes. Recurring events
                // need an occurrence discriminator; moved exceptions expose their original time.
                final long occurrenceTime = originalInstanceTime > 0
                        ? originalInstanceTime
                        : (recurrenceRule == null || recurrenceRule.isEmpty() ? 0 : beginTime);
                String syncKey = ShiftAlarmUtils.createSyncKey(
                        eventId, occurrenceTime, description);
                // A malformed provider should not collapse two occurrences into one row.
                if (desired.containsKey(syncKey)) {
                    syncKey += ":" + beginTime;
                }
                final String label = title == null || title.trim().isEmpty()
                        ? mContext.getString(R.string.calendar_shift_default_label)
                        : title.trim();
                final String legacySyncKey = eventId + "_" + beginTime;
                desired.put(syncKey,
                        new DesiredShift(syncKey, legacySyncKey, label, alarmTime));
            }
        }
        return desired;
    }

    private boolean shouldRingOnShiftDate(Calendar shiftDate) {
        try {
            return HolidayUtils.shouldAlarmRing(mContext,
                    HolidayUtils.HOLIDAY_OPTION_SKIP_HOLIDAY,
                    Weekdays.fromBits(0x7f), shiftDate);
        } catch (RuntimeException e) {
            // Calendar alarms are safety-sensitive. Missing holiday data must not silence one.
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
                // Preserve state from PR #31's eventId_beginTime key while upgrading it to the
                // stable key used by this integration.
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
}
