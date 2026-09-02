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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Synchronizes compatible system-calendar events with standalone calendar alarms and optionally
 * with individual advanced rotation alarms.
 *
 * <p>Standalone synchronization is diff based: a stable calendar occurrence key is stored with
 * every instance, unchanged instances retain their alarm state, and only changed or removed events
 * are rescheduled. Per-rotation synchronization stores upcoming event times as date overrides in
 * that alarm's existing {@code SHIFT_ROTATION_V2} payload.</p>
 */
public final class ShiftCalendarManager {

    private static final String TAG = "ShiftCalendarManager";
    private static final long SYNC_DEBOUNCE_SECONDS = 2;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final int ROTATION_SYNC_WINDOW_DAYS = 30;
    private static final String ROTATION_PAYLOAD_PREFIX = "SHIFT_ROTATION_V2";
    private static final String ALL_CALENDARS = "all";
    private static final Gson GSON = new Gson();
    private static final Type OVERRIDE_MAP_TYPE =
            new TypeToken<Map<String, Double>>() { }.getType();
    private static final Type DATE_SET_TYPE =
            new TypeToken<Set<String>>() { }.getType();

    public interface SyncListener {
        void onShiftSyncChanged();
    }

    /** A selectable system calendar exposed to the advanced shift alarm editor. */
    public static final class CalendarInfo {
        public final String id;
        public final String displayName;
        public final String accountName;
        public final int color;

        CalendarInfo(String id, String displayName, String accountName, int color) {
            this.id = id;
            this.displayName = displayName;
            this.accountName = accountName;
            this.color = color;
        }
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
        // Whether synchronization is enabled is resolved on the worker because an individual
        // rotation alarm can opt in even when standalone calendar alarms are disabled globally.
        if (!hasCalendarPermission()) {
            unregisterObserver();
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
        } catch (RuntimeException e) {
            // CalendarProvider may still be credential-encrypted during locked boot.
            LogUtils.e(TAG + ": Calendar provider is not available yet", e);
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
            final ContentResolver resolver = mContext.getContentResolver();
            final List<Alarm> alarms = Alarm.getAlarms(resolver, null);
            final boolean standaloneSyncEnabled = isEnabled();
            final boolean rotationSyncEnabled = hasEnabledRotationCalendarSync(alarms);

            if (!standaloneSyncEnabled && !rotationSyncEnabled) {
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

            registerObserver();
            final int updatedRotationAlarms = synchronizeRotationAlarms(alarms);

            if (!standaloneSyncEnabled) {
                removeAllCalendarInstances();
                setSuccessfulStatus(updatedRotationAlarms);
                return;
            }

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

    /** Returns system calendars that can be selected by an individual rotation alarm. */
    public List<CalendarInfo> getSystemCalendars() {
        final List<CalendarInfo> calendars = new ArrayList<>();
        if (!hasCalendarPermission()) {
            return calendars;
        }

        final String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR
        };
        try (Cursor cursor = mContext.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                return calendars;
            }
            while (cursor.moveToNext()) {
                calendars.add(new CalendarInfo(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getInt(3)));
            }
        } catch (SecurityException e) {
            LogUtils.e(TAG + ": Calendar permission was revoked while listing calendars", e);
        } catch (RuntimeException e) {
            LogUtils.e(TAG + ": Failed to query system calendars", e);
        }
        return calendars;
    }

    private static boolean hasEnabledRotationCalendarSync(List<Alarm> alarms) {
        for (Alarm alarm : alarms) {
            if (!alarm.enabled || TextUtils.isEmpty(alarm.rotationPayload)
                    || !alarm.rotationPayload.startsWith(ROTATION_PAYLOAD_PREFIX)) {
                continue;
            }
            final String[] parts = alarm.rotationPayload.split("\\|", -1);
            if (parts.length >= 8 && (Boolean.parseBoolean(parts[6])
                    || parts.length == 8 || !readManagedCalendarDates(parts).isEmpty())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Synchronizes the 30-day calendar window into enabled rotation alarms. Calendar-managed
     * overrides are tracked separately in payload part 8 so stale events can be removed without
     * deleting annual-leave pauses or future manual overrides.
     */
    private int synchronizeRotationAlarms(List<Alarm> alarms) {
        int updatedCount = 0;
        for (Alarm alarm : alarms) {
            if (!alarm.enabled || TextUtils.isEmpty(alarm.rotationPayload)
                    || !alarm.rotationPayload.startsWith(ROTATION_PAYLOAD_PREFIX)) {
                continue;
            }
            final String[] parts = alarm.rotationPayload.split("\\|", -1);
            if (parts.length < 8) {
                continue;
            }
            if (synchronizeRotationAlarm(alarm, parts)) {
                new AlarmUpdateHandler(mContext, null, null)
                        .asyncUpdateAlarm(alarm, false, false);
                updatedCount++;
            }
        }
        return updatedCount;
    }

    private boolean synchronizeRotationAlarm(Alarm alarm, String[] parts) {
        final boolean syncEnabled = Boolean.parseBoolean(parts[6]);
        final String targetCalendarId = TextUtils.isEmpty(parts[7])
                ? ALL_CALENDARS : parts[7];
        final Map<String, Double> overrides = readOverrides(parts[5]);
        final Set<String> oldManagedDates = readManagedCalendarDates(parts);

        // Payloads created by the source feature did not identify which positive overrides came
        // from Calendar. Since that UI exposed only annual-leave (-1) overrides, migrate its
        // positive values into the managed set so deleted events do not remain scheduled forever.
        if (parts.length == 8) {
            for (Map.Entry<String, Double> entry : overrides.entrySet()) {
                if (entry.getValue() != null && entry.getValue() >= 0) {
                    oldManagedDates.add(entry.getKey());
                }
            }
        }

        for (String date : oldManagedDates) {
            final Double value = overrides.get(date);
            if (value != null && value >= 0) {
                overrides.remove(date);
            }
        }

        final Set<String> newManagedDates = new LinkedHashSet<>();
        if (syncEnabled) {
            queryRotationCalendarOverrides(targetCalendarId, overrides, newManagedDates);
        }

        final String payload = String.format(Locale.US,
                "%s|%s|%s|%s|%s|%s|%b|%s|%s",
                ROTATION_PAYLOAD_PREFIX, parts[1], parts[2], parts[3], parts[4],
                GSON.toJson(overrides), syncEnabled, targetCalendarId,
                GSON.toJson(newManagedDates));
        if (payload.equals(alarm.rotationPayload)) {
            return false;
        }
        alarm.rotationPayload = payload;
        return true;
    }

    private void queryRotationCalendarOverrides(String targetCalendarId,
                                                Map<String, Double> overrides,
                                                Set<String> managedDates) {
        final long now = System.currentTimeMillis();
        final long windowEnd = now + ROTATION_SYNC_WINDOW_DAYS * MILLIS_PER_DAY;
        final long maximumOffsetMillis =
                ShiftAlarmUtils.MAX_ABSOLUTE_OFFSET_MINUTES * 60L * 1000L;
        final Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now - maximumOffsetMillis);
        ContentUris.appendId(builder, windowEnd + maximumOffsetMillis);

        final String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.CALENDAR_ID
        };
        final int fallbackOffset = readDefaultOffset();
        try (Cursor cursor = mContext.getContentResolver().query(builder.build(), projection,
                null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) {
                throw new IllegalStateException("Calendar provider returned no result");
            }
            while (cursor.moveToNext()) {
                final String title = cursor.getString(0);
                final String description = cursor.getString(1);
                final long beginTime = cursor.getLong(2);
                final String calendarId = cursor.getString(3);
                if (!ALL_CALENDARS.equals(targetCalendarId)
                        && !targetCalendarId.equals(calendarId)) {
                    continue;
                }
                if (!ShiftAlarmUtils.isShiftEvent(title, description)) {
                    continue;
                }

                final Calendar alarmTime = Calendar.getInstance();
                alarmTime.setTimeInMillis(beginTime);
                alarmTime.add(Calendar.MINUTE,
                        ShiftAlarmUtils.parseOffsetMinutes(description, fallbackOffset));
                alarmTime.set(Calendar.SECOND, 0);
                alarmTime.set(Calendar.MILLISECOND, 0);
                if (alarmTime.getTimeInMillis() <= now
                        || alarmTime.getTimeInMillis() > windowEnd) {
                    continue;
                }

                final String date = String.format(Locale.US, "%04d-%02d-%02d",
                        alarmTime.get(Calendar.YEAR), alarmTime.get(Calendar.MONTH) + 1,
                        alarmTime.get(Calendar.DAY_OF_MONTH));
                final Double existingValue = overrides.get(date);
                if (existingValue != null && existingValue < 0) {
                    // An explicit annual-leave pause always wins over calendar synchronization.
                    continue;
                }
                final int minuteOfDay = alarmTime.get(Calendar.HOUR_OF_DAY) * 60
                        + alarmTime.get(Calendar.MINUTE);
                overrides.put(date, (double) minuteOfDay);
                managedDates.add(date);
            }
        }
    }

    private static Map<String, Double> readOverrides(String json) {
        try {
            final Map<String, Double> parsed = GSON.fromJson(json, OVERRIDE_MAP_TYPE);
            return parsed == null ? new TreeMap<>() : new TreeMap<>(parsed);
        } catch (RuntimeException e) {
            return new TreeMap<>();
        }
    }

    private static Set<String> readManagedCalendarDates(String[] parts) {
        if (parts.length < 9 || TextUtils.isEmpty(parts[8])) {
            return new LinkedHashSet<>();
        }
        try {
            final Set<String> parsed = GSON.fromJson(parts[8], DATE_SET_TYPE);
            return parsed == null ? new LinkedHashSet<>() : new LinkedHashSet<>(parsed);
        } catch (RuntimeException e) {
            return new LinkedHashSet<>();
        }
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
