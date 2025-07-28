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

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HolidayRepository {

    private static final String HOLIDAY_URL = "https://raw.githubusercontent.com/lanceliao/china-holiday-calender/master/holidayAPI.json";

    private final Context context;

    public HolidayRepository(Context context) {
        this.context = context;
    }

    public void downloadAndSaveHolidays() {
        new Thread(() -> {
            try {
                URL url = new URL(HOLIDAY_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    parseAndSaveHolidays(response.toString());
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void parseAndSaveHolidays(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject years = jsonObject.getJSONObject("Years");
            List<Holiday> holidays = new ArrayList<>();
            for (int i = 0; i < years.names().length(); i++) {
                String year = years.names().getString(i);
                JSONArray yearHolidays = years.getJSONArray(year);
                for (int j = 0; j < yearHolidays.length(); j++) {
                    JSONObject holidayObject = yearHolidays.getJSONObject(j);
                    String name = holidayObject.getString("Name");
                    String startDate = holidayObject.getString("StartDate");
                    String endDate = holidayObject.getString("EndDate");
                    JSONArray compDaysArray = holidayObject.getJSONArray("CompDays");
                    List<String> compDays = new ArrayList<>();
                    for (int k = 0; k < compDaysArray.length(); k++) {
                        compDays.add(compDaysArray.getString(k));
                    }
                    holidays.add(new Holiday(name, startDate, endDate, compDays));
                }
            }
            HolidayDatabase.getDatabase(context).holidayDao().insertAll(holidays);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Holiday> getHolidays(int year) {
        return HolidayDatabase.getDatabase(context).holidayDao().getHolidaysByYear(String.valueOf(year));
    }

    public boolean isHoliday(long timeInMillis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new java.util.Date(timeInMillis));
        Holiday holiday = HolidayDatabase.getDatabase(context).holidayDao().getHolidayByDate(date);
        return holiday != null;
    }

    public boolean isWorkday(long timeInMillis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new java.util.Date(timeInMillis));
        Holiday holiday = HolidayDatabase.getDatabase(context).holidayDao().getWorkdayByDate(date);
        return holiday != null;
    }
}
