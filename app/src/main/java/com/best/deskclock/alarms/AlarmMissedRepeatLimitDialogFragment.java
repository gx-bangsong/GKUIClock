package com.best.deskclock.alarms;
import android.os.Bundle;
import com.best.deskclock.provider.Alarm;
public class AlarmMissedRepeatLimitDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "alarm_missed_repeat_limit_request_key";
    public static final String RESULT_PREF_KEY = "alarm_missed_repeat_limit_pref_key";
    public static final String MISSED_ALARM_REPEAT_LIMIT_VALUE = "missed_alarm_repeat_limit_value";
    public static AlarmMissedRepeatLimitDialogFragment newInstance(Alarm alarm, int repeatLimit, String tag) {
        AlarmMissedRepeatLimitDialogFragment f = new AlarmMissedRepeatLimitDialogFragment();
        Bundle a = new Bundle(); a.putParcelable("arg_alarm", alarm); a.putInt("arg_repeat_limit", repeatLimit); a.putString("arg_tag", tag); f.setArguments(a); return f;
    }
    public static void show(androidx.fragment.app.FragmentManager fm, AlarmMissedRepeatLimitDialogFragment f) { f.show(fm, "missed_repeat_limit"); }
}
