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

import android.app.Application;

import com.best.deskclock.data.DataModel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HolidayRepository {

    private final HolidayDao mHolidayDao;
    private final ExecutorService mExecutorService;

    public HolidayRepository(Application application) {
        HolidayDatabase db = HolidayDatabase.getDatabase(application);
        mHolidayDao = db.holidayDao();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public void updateWorkdayData() {
        mExecutorService.execute(() -> {
            try {
                URL url = new URL(DataModel.getDataModel().getHolidayDataUrl());
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                Type listType = new TypeToken<List<Holiday>>() {}.getType();
                List<Holiday> holidays = new Gson().fromJson(in, listType);
                mHolidayDao.insertAll(holidays);
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Holiday getHolidayByDate(String date) {
        return mHolidayDao.getHolidayByDate(date);
    }

    public Holiday getCompDayByDate(String date) {
        return mHolidayDao.getCompDayByDate(date);
    }
}
