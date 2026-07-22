package com.best.deskclock.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.best.deskclock.utils.LogUtils;

public class ShiftSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "ShiftSyncReceiver";
    public static final String ACTION_SYNC_CALENDAR_SHIFTS = "com.best.deskclock.action.SYNC_CALENDAR_SHIFTS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SYNC_CALENDAR_SHIFTS.equals(intent.getAction())) {
            LogUtils.i(TAG, "Received broadcast to sync calendar shift alarms.");
            ShiftCalendarManager.getInstance(context).updateShiftAlarms();
        }
    }
}
