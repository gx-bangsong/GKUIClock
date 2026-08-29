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

import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;
import static com.best.deskclock.settings.PreferencesKeys.KEY_USE_IMPORTED_HOLIDAY_DATA;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.best.deskclock.alarms.ShiftCalendarManager;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.utils.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provides holiday lookups and atomic URL/file imports. */
public class HolidayRepository {
    private static final int NETWORK_TIMEOUT_MILLIS = 15_000;

    private static volatile HolidayRepository sInstance;

    private final Context mContext;
    private final HolidayDao mHolidayDao;
    private final ExecutorService mExecutorService;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Holiday> mHolidayCache = new ConcurrentHashMap<>();
    private final Map<String, Holiday> mCompDayCache = new ConcurrentHashMap<>();

    private HolidayRepository(Context context) {
        mContext = context.getApplicationContext();
        HolidayDatabase db = HolidayDatabase.getDatabase(mContext);
        mHolidayDao = db.holidayDao();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public static HolidayRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (HolidayRepository.class) {
                if (sInstance == null) {
                    sInstance = new HolidayRepository(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Refreshes the configured URL in the background. Automatic startup/boot refreshes do not
     * overwrite a data set explicitly imported from a local file.
     */
    public void updateWorkdayData() {
        final SharedPreferences prefs = getDefaultSharedPreferences(mContext);
        if (prefs.getBoolean(KEY_USE_IMPORTED_HOLIDAY_DATA, false)) {
            return;
        }
        updateFromUrl(null, false);
    }

    /** Refreshes from the configured URL because the user explicitly requested it. */
    public void updateWorkdayData(UpdateListener listener) {
        updateFromUrl(listener, true);
    }

    private void updateFromUrl(UpdateListener listener, boolean userInitiated) {
        mExecutorService.execute(() -> {
            try {
                final String urlString = DataModel.getDataModel().getHolidayDataUrl();
                if (urlString == null || urlString.trim().isEmpty()) {
                    throw new IOException("Holiday data URL is empty");
                }
                final URLConnection connection = new URL(urlString).openConnection();
                connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
                connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(
                             stream, StandardCharsets.UTF_8))) {
                    final ImportSummary summary = replaceData(reader);
                    if (userInitiated) {
                        getDefaultSharedPreferences(mContext).edit()
                                .putBoolean(KEY_USE_IMPORTED_HOLIDAY_DATA, false).apply();
                    }
                    notifySuccess(listener, summary);
                }
            } catch (Exception exception) {
                LogUtils.e("Error updating holiday data: " + exception.getMessage());
                notifyError(listener, exception);
            }
        });
    }

    /** Imports a local JSON document and makes it the active source until a manual URL refresh. */
    public void importHolidayData(Uri uri, UpdateListener listener) {
        mExecutorService.execute(() -> {
            try (InputStream stream = mContext.getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    throw new IOException("Unable to open holiday data file");
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        stream, StandardCharsets.UTF_8))) {
                    final ImportSummary summary = replaceData(reader);
                    getDefaultSharedPreferences(mContext).edit()
                            .putBoolean(KEY_USE_IMPORTED_HOLIDAY_DATA, true).apply();
                    notifySuccess(listener, summary);
                }
            } catch (Exception exception) {
                LogUtils.e("Error importing holiday data: " + exception.getMessage());
                notifyError(listener, exception);
            }
        });
    }

    private ImportSummary replaceData(BufferedReader reader) {
        final HolidayJsonParser.Result result = HolidayJsonParser.parse(reader);
        mHolidayDao.replaceAll(result.getHolidays());
        mHolidayCache.clear();
        mCompDayCache.clear();
        ShiftCalendarManager.getInstance(mContext).requestSync();
        return new ImportSummary(result.getHolidays().size(), result.getCountryCodes());
    }

    public Holiday getHolidayByDate(String date, String countryCode) {
        final String key = cacheKey(date, countryCode);
        Holiday holiday = mHolidayCache.get(key);
        if (holiday == null && Looper.myLooper() != Looper.getMainLooper()) {
            holiday = mHolidayDao.getHolidayByDate(date, nullToEmpty(countryCode));
            if (holiday != null) {
                mHolidayCache.put(key, holiday);
            }
        }
        return holiday;
    }

    public Holiday getCompDayByDate(String date, String countryCode) {
        final String key = cacheKey(date, countryCode);
        Holiday holiday = mCompDayCache.get(key);
        if (holiday == null && Looper.myLooper() != Looper.getMainLooper()) {
            holiday = mHolidayDao.getCompDayByDate(date, nullToEmpty(countryCode));
            if (holiday != null) {
                mCompDayCache.put(key, holiday);
            }
        }
        return holiday;
    }

    /** Compatibility overload for callers that intentionally want all imported countries. */
    public Holiday getHolidayByDate(String date) {
        return getHolidayByDate(date, "");
    }

    /** Compatibility overload for callers that intentionally want all imported countries. */
    public Holiday getCompDayByDate(String date) {
        return getCompDayByDate(date, "");
    }

    public List<Holiday> getAllHolidays() {
        return mHolidayDao.getAllHolidays();
    }

    private void notifySuccess(UpdateListener listener, ImportSummary summary) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onSuccess(summary));
        }
    }

    private void notifyError(UpdateListener listener, Exception exception) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onError(exception));
        }
    }

    private static String cacheKey(String date, String countryCode) {
        return nullToEmpty(countryCode).toUpperCase(Locale.US) + '|' + date;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public interface UpdateListener {
        void onSuccess(ImportSummary summary);

        void onError(Exception exception);
    }

    public static final class ImportSummary {
        private final int mHolidayCount;
        private final Set<String> mCountryCodes;

        private ImportSummary(int holidayCount, Set<String> countryCodes) {
            mHolidayCount = holidayCount;
            mCountryCodes = Collections.unmodifiableSet(countryCodes);
        }

        public int getHolidayCount() {
            return mHolidayCount;
        }

        public Set<String> getCountryCodes() {
            return mCountryCodes;
        }
    }
}
