package com.best.deskclock.timer.quick;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quick_timers")
public class QuickTimer {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long duration;
    public String label;

    public QuickTimer(long duration, String label) {
        this.duration = duration;
        this.label = label;
    }
}
