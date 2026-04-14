package com.best.deskclock.timer.quick;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {QuickTimer.class}, version = 1, exportSchema = false)
public abstract class QuickTimerDatabase extends RoomDatabase {
    public abstract QuickTimerDao quickTimerDao();

    private static volatile QuickTimerDatabase INSTANCE;

    public static QuickTimerDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (QuickTimerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    QuickTimerDatabase.class, "quick_timer_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
