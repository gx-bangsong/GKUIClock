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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HolidayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Holiday> holidays);

    @Query("SELECT * FROM holiday WHERE SUBSTR(startDate, 1, 4) = :year")
    List<Holiday> getHolidaysByYear(String year);

    @Query("SELECT * FROM holiday WHERE :date BETWEEN startDate AND endDate")
    Holiday getHolidayByDate(String date);

    @Query("SELECT * FROM holiday WHERE compDays LIKE '%' || :date || '%'")
    Holiday getWorkdayByDate(String date);
}
