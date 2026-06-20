package com.best.deskclock.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.utils.LogUtils;

public class ShiftActionReceiver extends BroadcastReceiver {
    public static final String ACTION_SKIP_SHIFT = "com.best.deskclock.action.SKIP_SHIFT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SKIP_SHIFT.equals(intent.getAction())) {
            long instanceId = intent.getLongExtra("instance_id", -1);
            if (instanceId != -1) {
                LogUtils.i("ShiftActionReceiver", "Skipping shift instance: " + instanceId);
                AlarmInstance instance = AlarmInstance.getInstance(context.getContentResolver(), instanceId);
                if (instance != null) {
                    AlarmStateManager.setPreDismissState(context, instance);
                }
            }
        }
    }
}
