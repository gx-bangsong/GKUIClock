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
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface HolidayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Holiday> holidays);

    @Query("SELECT * FROM holiday WHERE SUBSTR(startDate, 1, 4) = :year "
            + "AND (:countryCode = '' OR countryCode IS NULL OR countryCode = '' "
            + "OR UPPER(countryCode) = UPPER(:countryCode))")
    List<Holiday> getHolidaysByYear(String year, String countryCode);

    @Query("SELECT * FROM holiday WHERE :date BETWEEN startDate AND endDate "
            + "AND (:countryCode = '' OR countryCode IS NULL OR countryCode = '' "
            + "OR UPPER(countryCode) = UPPER(:countryCode)) LIMIT 1")
    Holiday getHolidayByDate(String date, String countryCode);

    @Query("SELECT * FROM holiday WHERE compDays LIKE '%' || :date || '%' "
            + "AND (:countryCode = '' OR countryCode IS NULL OR countryCode = '' "
            + "OR UPPER(countryCode) = UPPER(:countryCode)) LIMIT 1")
    Holiday getCompDayByDate(String date, String countryCode);

    @Query("SELECT * FROM holiday")
    List<Holiday> getAllHolidays();

    @Query("DELETE FROM holiday")
    void deleteAll();

    /** Replaces the active data atomically, so a malformed import never destroys valid data. */
    @Transaction
    default void replaceAll(List<Holiday> holidays) {
        deleteAll();
        insertAll(holidays);
    }
}
