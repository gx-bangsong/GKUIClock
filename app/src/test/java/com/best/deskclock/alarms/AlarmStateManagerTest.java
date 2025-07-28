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

package com.best.deskclock.alarms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContentResolver;

import com.best.deskclock.holiday.HolidayRepository;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Calendar;

@RunWith(MockitoJUnitRunner.class)
public class AlarmStateManagerTest {

    @Mock
    private Context mockContext;

    @Mock
    private ContentResolver mockContentResolver;

    @Mock
    private HolidayRepository mockHolidayRepository;

    @Mock
    private AlarmStateManager.StateChangeScheduler mockScheduler;

    @Before
    public void setUp() {
        when(mockContext.getContentResolver()).thenReturn(mockContentResolver);
    }

    @Test
    public void testScheduleInstanceStateChange_holiday_skip() {
        // Given an alarm that should skip holidays
        Alarm alarm = new Alarm();
        alarm.holidayOption = 0;
        AlarmInstance instance = new AlarmInstance(Calendar.getInstance(), alarm.id);
        when(Alarm.getAlarm(mockContentResolver, instance.mAlarmId)).thenReturn(alarm);
        when(mockHolidayRepository.isHoliday(anyLong())).thenReturn(true);

        // When scheduling an instance state change
        AlarmStateManager.scheduleInstanceStateChange(mockContext, instance.getAlarmTime(), instance, AlarmInstance.FIRED_STATE);

        // Then the state change should not be scheduled
        verify(mockScheduler, never()).scheduleInstanceStateChange(any(Context.class), any(Calendar.class), any(AlarmInstance.class), anyInt());
    }

    @Test
    public void testScheduleInstanceStateChange_workday() {
        // Given an alarm on a workday
        Alarm alarm = new Alarm();
        alarm.holidayOption = 0;
        AlarmInstance instance = new AlarmInstance(Calendar.getInstance(), alarm.id);
        when(Alarm.getAlarm(mockContentResolver, instance.mAlarmId)).thenReturn(alarm);
        when(mockHolidayRepository.isWorkday(anyLong())).thenReturn(true);

        // When scheduling an instance state change
        AlarmStateManager.scheduleInstanceStateChange(mockContext, instance.getAlarmTime(), instance, AlarmInstance.FIRED_STATE);

        // Then the state change should be scheduled
        verify(mockScheduler).scheduleInstanceStateChange(any(Context.class), any(Calendar.class), any(AlarmInstance.class), anyInt());
    }
}
