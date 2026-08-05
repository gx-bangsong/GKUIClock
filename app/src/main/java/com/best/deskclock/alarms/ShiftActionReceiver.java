/*
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.utils.LogUtils;

/** Handles the notification action that skips one calendar-backed shift occurrence. */
public final class ShiftActionReceiver extends BroadcastReceiver {

    public static final String ACTION_SKIP_SHIFT = "com.best.deskclock.action.SKIP_SHIFT";
    public static final String EXTRA_INSTANCE_ID = "instance_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_SKIP_SHIFT.equals(intent.getAction())) {
            skipShift(context, intent.getLongExtra(EXTRA_INSTANCE_ID, AlarmInstance.INVALID_ID));
        }
    }

    public static boolean skipShift(Context context, long instanceId) {
        if (instanceId == AlarmInstance.INVALID_ID) {
            return false;
        }

        final AlarmInstance instance = AlarmInstance.getInstance(
                context.getContentResolver(), instanceId);
        if (instance == null || !instance.isCalendarShift()) {
            LogUtils.w("Ignoring skip request for non-calendar instance " + instanceId);
            return false;
        }

        instance.mSyncState = AlarmInstance.SYNC_STATE_SKIPPED;
        AlarmStateManager.setPreDismissState(context, instance);
        LogUtils.i("Skipped calendar shift instance " + instanceId);
        return true;
    }
}
