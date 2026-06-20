package com.best.deskclock.alarms;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.provider.ClockContract;
import com.best.deskclock.utils.LogUtils;
import com.best.deskclock.utils.ShiftAlarmUtils;

import java.util.Calendar;
import java.util.List;

public class RotationScheduler {

    private static final String TAG = "RotationScheduler";

    public static void scheduleRotationAlarms(Context context) {
        ContentResolver cr = context.getContentResolver();

        // Cleanup old rotation instances
        List<AlarmInstance> currentInstances = AlarmInstance.getInstances(cr, null);
        for (AlarmInstance instance : currentInstances) {
            if (ShiftAlarmUtils.isRotationId(instance.mId)) {
                AlarmStateManager.unregisterInstance(context, instance);
                AlarmInstance.deleteInstance(cr, instance.mId);
            }
        }

        List<Alarm> alarms = Alarm.getAlarms(cr, ClockContract.AlarmsColumns.ENABLED + "=1");
        for (Alarm alarm : alarms) {
            if (alarm.rotationPayload != null && !alarm.rotationPayload.isEmpty()) {
                generateInstancesForAlarm(context, alarm);
            }
        }
    }

    private static void generateInstancesForAlarm(Context context, Alarm alarm) {
        if (alarm.rotationPayload == null) return;
        String[] parts = alarm.rotationPayload.split(",");
        if (parts.length < 2) return;

        try {
            long anchorTimestamp = Long.parseLong(parts[0]);
            Calendar anchor = Calendar.getInstance();
            anchor.setTimeInMillis(anchorTimestamp);
            anchor.set(Calendar.HOUR_OF_DAY, 0);
            anchor.set(Calendar.MINUTE, 0);
            anchor.set(Calendar.SECOND, 0);
            anchor.set(Calendar.MILLISECOND, 0);

            Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);

            for (int i = 0; i < 7; i++) {
                Calendar targetDay = (Calendar) now.clone();
                targetDay.add(Calendar.DAY_OF_YEAR, i);

                long diffMillis = targetDay.getTimeInMillis() - anchor.getTimeInMillis();
                int daysDiff = (int) (diffMillis / (24 * 60 * 60 * 1000L));
                if (daysDiff < 0) continue;

                int cycleIndex = daysDiff % 64;
                if (cycleIndex + 1 < parts.length) {
                    int rule = Integer.parseInt(parts[cycleIndex + 1]);
                    if (rule > 0) {
                        createRotationInstance(context, alarm, targetDay, rule);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Error scheduling rotation", e);
        }
    }

    private static void createRotationInstance(Context context, Alarm alarm, Calendar targetDay, int rule) {
        Calendar alarmTime = (Calendar) targetDay.clone();
        alarmTime.set(Calendar.HOUR_OF_DAY, alarm.hour);
        alarmTime.set(Calendar.MINUTE, alarm.minutes);
        alarmTime.set(Calendar.SECOND, 0);
        alarmTime.set(Calendar.MILLISECOND, 0);

        // Custom offsets for different shift types if needed
        // For now, rule 1, 2, 3 all ring at alarm time but could be modified
        String shiftLabel = switch (rule) {
            case 1 -> " (早班)";
            case 2 -> " (中班)";
            case 3 -> " (夜班)";
            default -> " (轮班)";
        };

        if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) return;

        long ephemeralId = -20000L - (alarm.id * 100) - (targetDay.get(Calendar.DAY_OF_YEAR) % 100);

        AlarmInstance instance = new AlarmInstance(alarmTime);
        instance.mId = ephemeralId;
        instance.mLabel = alarm.label + shiftLabel;
        instance.mAlarmId = alarm.id;
        instance.mRingtone = alarm.alert;
        instance.mVibrate = alarm.vibrate;
        instance.mVibrationPattern = alarm.vibrationPattern;
        instance.mAlarmVolume = alarm.alarmVolume;

        ContentResolver cr = context.getContentResolver();
        cr.insert(ClockContract.InstancesColumns.CONTENT_URI, instance.createContentValues());
        AlarmStateManager.registerInstance(context, instance, true);
        LogUtils.i(TAG, "Scheduled rotation instance: " + instance.mId + " for " + alarmTime.getTime());
    }
}
