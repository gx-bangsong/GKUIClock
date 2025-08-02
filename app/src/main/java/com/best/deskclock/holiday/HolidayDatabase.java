/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.best.deskclock.holiday;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

@Database(entities = {Holiday.class}, version = 1)
@androidx.room.TypeConverters({Converters.class})
public abstract class HolidayDatabase extends RoomDatabase {

    public abstract HolidayDao holidayDao();

    private static volatile HolidayDatabase INSTANCE;

    public static HolidayDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (HolidayDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            HolidayDatabase.class, "holiday_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
