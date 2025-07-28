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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Calendar;

@RunWith(MockitoJUnitRunner.class)
public class HolidayRepositoryTest {

    @Mock
    private Context mockContext;

    private HolidayRepository holidayRepository;

    @Before
    public void setUp() {
        holidayRepository = new HolidayRepository(mockContext);
    }

    @Test
    public void testIsHoliday() {
        // Mock the database to return a holiday for a specific date
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.OCTOBER, 1);
        long timeInMillis = calendar.getTimeInMillis();
        Holiday holiday = new Holiday("National Day", "2023-10-01", "2023-10-03", null);
        when(HolidayDatabase.getDatabase(mockContext).holidayDao().getHolidayByDate("2023-10-01")).thenReturn(holiday);

        assertTrue(holidayRepository.isHoliday(timeInMillis));
    }

    @Test
    public void testIsWorkday() {
        // Mock the database to return a workday for a specific date
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.OCTOBER, 7);
        long timeInMillis = calendar.getTimeInMillis();
        Holiday holiday = new Holiday("National Day", "2023-10-01", "2023-10-03", java.util.Arrays.asList("2023-10-07"));
        when(HolidayDatabase.getDatabase(mockContext).holidayDao().getWorkdayByDate("2023-10-07")).thenReturn(holiday);

        assertTrue(holidayRepository.isWorkday(timeInMillis));
    }

    @Test
    public void testIsNotHoliday() {
        // Mock the database to return null for a specific date
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.NOVEMBER, 11);
        long timeInMillis = calendar.getTimeInMillis();
        when(HolidayDatabase.getDatabase(mockContext).holidayDao().getHolidayByDate("2023-11-11")).thenReturn(null);

        assertFalse(holidayRepository.isHoliday(timeInMillis));
    }
}
