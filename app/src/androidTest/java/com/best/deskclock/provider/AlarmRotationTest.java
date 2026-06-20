package com.best.deskclock.provider;
import static org.junit.Assert.assertEquals;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.TimeZone;
@RunWith(AndroidJUnit4.class)
public class AlarmRotationTest {
    @Test
    public void testRotationScheduling() {
        Calendar anchor = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        anchor.set(2023, Calendar.OCTOBER, 23, 0, 0, 0);
        anchor.set(Calendar.MILLISECOND, 0);
        Alarm alarm = new Alarm();
        alarm.rotationPayload = "SHIFT_ROTATION_V2|3|" + anchor.getTimeInMillis() + "|false|480,960,-1|{}";
        Calendar now = (Calendar) anchor.clone();
        now.set(Calendar.HOUR_OF_DAY, 7);
        Calendar next = alarm.getNextAlarmTime(now);
        assertEquals(23, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY));
        now.set(Calendar.HOUR_OF_DAY, 9);
        next = alarm.getNextAlarmTime(now);
        assertEquals(24, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(16, next.get(Calendar.HOUR_OF_DAY));
        now.set(2023, Calendar.OCTOBER, 25, 0, 0, 0);
        next = alarm.getNextAlarmTime(now);
        assertEquals(26, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY));
    }
}
