package com.best.deskclock.timer.quick;

import android.content.Context;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.best.deskclock.R;

@Entity(tableName = "quick_timers")
public class QuickTimer {
    public static final String DEFAULT_BRUSH_TEETH = "gkuiclock.default.brush_teeth";
    public static final String DEFAULT_INSTANT_NOODLES = "gkuiclock.default.instant_noodles";
    public static final String DEFAULT_STEAM_EGGS = "gkuiclock.default.steam_eggs";
    public static final String DEFAULT_FACE_MASK = "gkuiclock.default.face_mask";

    @PrimaryKey(autoGenerate = true)
    public int id;
    public long duration;
    public String label;

    public QuickTimer(long duration, String label) {
        this.duration = duration;
        this.label = label;
    }

    /** Resolves built-in labels at display time so they follow app language changes. */
    @Ignore
    public String getDisplayLabel(Context context) {
        if (label == null) {
            return "";
        }
        return switch (label) {
            case DEFAULT_BRUSH_TEETH -> context.getString(R.string.quick_timer_brush_teeth);
            case DEFAULT_INSTANT_NOODLES -> context.getString(R.string.quick_timer_instant_noodles);
            case DEFAULT_STEAM_EGGS -> context.getString(R.string.quick_timer_steam_eggs);
            case DEFAULT_FACE_MASK -> context.getString(R.string.quick_timer_face_mask);
            default -> label;
        };
    }
}
