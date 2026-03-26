package com.best.deskclock.utils;

import android.app.NotificationManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.core.app.NotificationManagerCompat;

public class PermissionUtils {
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean areNotificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static boolean areFullScreenNotificationsEnabled(Context context) {
        // Simple implementation for now
        return true;
    }
}
