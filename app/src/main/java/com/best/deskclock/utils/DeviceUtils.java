package com.best.deskclock.utils;
import android.content.Context;
import android.os.Vibrator;
public class DeviceUtils {
    public static boolean hasVibrator(Context context) {
        Vibrator v = context.getSystemService(Vibrator.class);
        return v != null && v.hasVibrator();
    }
}
