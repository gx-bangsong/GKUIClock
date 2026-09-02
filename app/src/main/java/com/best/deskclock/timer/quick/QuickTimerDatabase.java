package com.best.deskclock.timer.quick;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Stores user-defined quick timers and installs a small useful starter set. */
@Database(entities = {QuickTimer.class}, version = 3, exportSchema = false)
public abstract class QuickTimerDatabase extends RoomDatabase {
    private static final long MINUTE_IN_MILLIS = 60_000L;
    private static final long[] DEFAULT_DURATIONS = {
            2 * MINUTE_IN_MILLIS,
            3 * MINUTE_IN_MILLIS,
            10 * MINUTE_IN_MILLIS,
            15 * MINUTE_IN_MILLIS
    };
    private static final String[] DEFAULT_LABELS = {
            QuickTimer.DEFAULT_BRUSH_TEETH,
            QuickTimer.DEFAULT_INSTANT_NOODLES,
            QuickTimer.DEFAULT_STEAM_EGGS,
            QuickTimer.DEFAULT_FACE_MASK
    };

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Do not mix defaults into an existing customized list. Existing users whose list is
            // still empty receive the same starter set as a fresh installation.
            try (Cursor cursor = database.query("SELECT COUNT(*) FROM quick_timers")) {
                if (cursor.moveToFirst() && cursor.getInt(0) == 0) {
                    insertDefaults(database);
                }
            }
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Replace only the anonymous duration-only starter set from version 2. Any customized
            // list, including a list with the same durations but user labels, is left untouched.
            try (Cursor cursor = database.query("SELECT COUNT(*), "
                    + "SUM(CASE WHEN label IS NULL AND duration IN (60000, 180000, 300000, 600000) "
                    + "THEN 1 ELSE 0 END) FROM quick_timers")) {
                if (cursor.moveToFirst() && cursor.getInt(0) == 4 && cursor.getInt(1) == 4) {
                    database.execSQL("DELETE FROM quick_timers");
                    insertDefaults(database);
                }
            }
        }
    };

    private static final Callback CREATE_CALLBACK = new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase database) {
            super.onCreate(database);
            insertDefaults(database);
        }
    };

    private static volatile QuickTimerDatabase INSTANCE;

    public abstract QuickTimerDao quickTimerDao();

    public static QuickTimerDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (QuickTimerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    QuickTimerDatabase.class, "quick_timer_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .addCallback(CREATE_CALLBACK)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void insertDefaults(SupportSQLiteDatabase database) {
        for (int index = 0; index < DEFAULT_DURATIONS.length; index++) {
            database.execSQL("INSERT INTO quick_timers (duration, label) VALUES (?, ?)",
                    new Object[]{DEFAULT_DURATIONS[index], DEFAULT_LABELS[index]});
        }
    }
}
