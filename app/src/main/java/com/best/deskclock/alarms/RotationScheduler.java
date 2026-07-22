package com.best.deskclock.alarms;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.provider.ClockContract;
import com.best.deskclock.utils.LogUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RotationScheduler {

    private static final String TAG = "RotationScheduler";

    public static synchronized void scheduleRotationAlarms(Context context) {
        ContentResolver cr = context.getContentResolver();

        // 1. Query all existing rotation instances from DB (source_type = 2)
        List<AlarmInstance> existingInstances;
        try {
            existingInstances = AlarmInstance.getInstances(cr, ClockContract.InstancesColumns.SOURCE_TYPE + "=2");
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to query existing rotation instances", e);
            return;
        }

        Map<String, AlarmInstance> existingMap = new HashMap<>();
        for (AlarmInstance inst : existingInstances) {
            if (inst.mSyncKey != null) {
                existingMap.put(inst.mSyncKey, inst);
            }
        }

        Set<String> processedSyncKeys = new HashSet<>();

        // 2. Query enabled alarms to see if they have rotation payloads
        List<Alarm> alarms = Alarm.getAlarms(cr, ClockContract.AlarmsColumns.ENABLED + "=1");
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        for (Alarm alarm : alarms) {
            if (alarm.rotationPayload != null && !alarm.rotationPayload.isEmpty()) {
                generateRotationInstances(context, alarm, now, existingMap, processedSyncKeys);
            }
        }

        // 3. Delete obsolete local rotation instances that are no longer valid (e.g. parent alarm disabled/deleted)
        for (AlarmInstance inst : existingInstances) {
            if (inst.mSyncKey != null && !processedSyncKeys.contains(inst.mSyncKey)) {
                // Keep active or snoozing instances intact
                if (inst.mAlarmState == AlarmInstance.FIRED_STATE || inst.mAlarmState == AlarmInstance.SNOOZE_STATE) {
                    LogUtils.i(TAG, "Preserving active/snoozing obsolete rotation instance: " + inst.mId);
                    continue;
                }
                LogUtils.i(TAG, "Deleting obsolete rotation instance: " + inst.mId + ", SyncKey: " + inst.mSyncKey);
                AlarmStateManager.unregisterInstance(context, inst);
                AlarmInstance.deleteInstance(cr, inst.mId);
            }
        }
    }

    private static void generateRotationInstances(Context context, Alarm alarm, Calendar todayMidnight,
                                                  Map<String, AlarmInstance> existingMap, Set<String> processedSyncKeys) {
        String[] parts = alarm.rotationPayload.split(",");
        if (parts.length < 2) return;

        try {
            long anchorTimestamp = Long.parseLong(parts[0]);
            Calendar anchor = Calendar.getInstance();
            anchor.setTimeInMillis(anchorTimestamp);

            // Extract local dates for safe DST-resistant computation
            LocalDate anchorDate = LocalDate.of(anchor.get(Calendar.YEAR), anchor.get(Calendar.MONTH) + 1, anchor.get(Calendar.DAY_OF_MONTH));
            LocalDate baseDate = LocalDate.of(todayMidnight.get(Calendar.YEAR), todayMidnight.get(Calendar.MONTH) + 1, todayMidnight.get(Calendar.DAY_OF_MONTH));

            // Generate for the next 7 days
            for (int i = 0; i < 7; i++) {
                LocalDate targetDate = baseDate.plusDays(i);
                long daysDiff = ChronoUnit.DAYS.between(anchorDate, targetDate);
                if (daysDiff < 0) continue;

                // Dynamic Cycle Length (P1 #9)
                int cycleLength = parts.length - 1;
                if (cycleLength <= 0) continue;

                int cycleIndex = (int) (daysDiff % cycleLength);
                if (cycleIndex < 0) {
                    cycleIndex += cycleLength;
                }

                if (cycleIndex + 1 < parts.length) {
                    int rule = Integer.parseInt(parts[cycleIndex + 1]);
                    if (rule > 0) {
                        // Form unique Sync Key: "rotation_<alarmId>_<year>_<month>_<day>"
                        String syncKey = "rotation_" + alarm.id + "_" + targetDate.getYear() + "_" + targetDate.getMonthValue() + "_" + targetDate.getDayOfMonth();
                        processedSyncKeys.add(syncKey);

                        Calendar alarmTime = Calendar.getInstance();
                        alarmTime.set(Calendar.YEAR, targetDate.getYear());
                        alarmTime.set(Calendar.MONTH, targetDate.getMonthValue() - 1);
                        alarmTime.set(Calendar.DAY_OF_MONTH, targetDate.getDayOfMonth());
                        alarmTime.set(Calendar.HOUR_OF_DAY, alarm.hour);
                        alarmTime.set(Calendar.MINUTE, alarm.minutes);
                        alarmTime.set(Calendar.SECOND, 0);
                        alarmTime.set(Calendar.MILLISECOND, 0);

                        if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) {
                            continue;
                        }

                        reconcileRotationInstance(context, alarm, alarmTime, rule, syncKey, existingMap);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Error generating rotation instances for alarm: " + alarm.id, e);
        }
    }

    private static void reconcileRotationInstance(Context context, Alarm alarm, Calendar alarmTime,
                                                   int rule, String syncKey, Map<String, AlarmInstance> existingMap) {
        String shiftLabel = switch (rule) {
            case 1 -> " (早班)";
            case 2 -> " (中班)";
            case 3 -> " (夜班)";
            default -> " (轮班)";
        };

        String label = alarm.label + shiftLabel;
        ContentResolver cr = context.getContentResolver();
        AlarmInstance existing = existingMap.get(syncKey);

        if (existing == null) {
            // INSERT: Create new instance using standard positive database auto-increment ID
            AlarmInstance instance = new AlarmInstance(alarmTime);
            instance.mLabel = label;
            instance.mAlarmId = alarm.id;
            instance.mSourceType = 2; // Local Rotation
            instance.mSyncKey = syncKey;
            instance.mSyncState = 0;

            instance.mRingtone = alarm.alert;
            instance.mVibrate = alarm.vibrate;
            instance.mVibrationPattern = alarm.vibrationPattern;
            instance.mAlarmVolume = alarm.alarmVolume;

            ContentValues values = instance.createContentValues();
            try {
                Uri uri = cr.insert(ClockContract.InstancesColumns.CONTENT_URI, values);
                if (uri != null) {
                    instance.mId = AlarmInstance.getId(uri);
                    AlarmStateManager.registerInstance(context, instance, true);
                    LogUtils.i(TAG, "Scheduled new rotation instance ID: " + instance.mId + ", SyncKey: " + syncKey + " for " + alarmTime.getTime());
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "Failed to insert rotation instance", e);
            }
        } else {
            // RECONCILE: check if time or properties changed
            Calendar existingTime = existing.getAlarmTime();
            boolean timeChanged = existingTime.getTimeInMillis() != alarmTime.getTimeInMillis();
            boolean labelChanged = !existing.mLabel.equals(label);
            boolean ringtoneChanged = existing.mRingtone != null && !existing.mRingtone.equals(alarm.alert);

            if (timeChanged || labelChanged || ringtoneChanged) {
                if (existing.mAlarmState == AlarmInstance.FIRED_STATE || existing.mAlarmState == AlarmInstance.SNOOZE_STATE) {
                    LogUtils.i(TAG, "Rotation instance is currently active/snoozing. Skipping update: " + existing.mId);
                    return;
                }

                LogUtils.i(TAG, "Updating existing rotation instance ID: " + existing.mId + " due to template change.");
                existing.setAlarmTime(alarmTime);
                existing.mLabel = label;
                existing.mRingtone = alarm.alert;
                existing.mVibrate = alarm.vibrate;
                existing.mVibrationPattern = alarm.vibrationPattern;
                existing.mAlarmVolume = alarm.alarmVolume;
                existing.updateInstance(cr);

                AlarmStateManager.registerInstance(context, existing, true);
            } else {
                LogUtils.v(TAG, "Rotation instance ID " + existing.mId + " is already up to date.");
            }
        }
    }
}
