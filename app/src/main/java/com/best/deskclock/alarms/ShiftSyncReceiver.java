/*
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.best.deskclock.utils.LogUtils;

/** Signature-protected integration point for GKUICalendar to request reconciliation. */
public final class ShiftSyncReceiver extends BroadcastReceiver {

    public static final String ACTION_SYNC_CALENDAR_SHIFTS =
            "com.best.deskclock.action.SYNC_CALENDAR_SHIFTS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_SYNC_CALENDAR_SHIFTS.equals(intent.getAction())) {
            LogUtils.i("Received calendar shift synchronization request");
            final ShiftCalendarManager manager = ShiftCalendarManager.getInstance(context);
            manager.registerObserver();
            manager.requestImmediateSync();
        }
    }
}
