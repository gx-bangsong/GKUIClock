package com.best.deskclock;
import android.os.Bundle;
import com.best.deskclock.provider.Alarm;
public class AlarmSnoozeDurationDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "alarm_snooze_request_key";
    public static final String RESULT_PREF_KEY = "alarm_snooze_pref_key";
    public static final String ALARM_SNOOZE_DURATION_VALUE = "alarm_snooze_duration_value";
    public static AlarmSnoozeDurationDialogFragment newInstance(String prefKey, int minutes, boolean isTimer) {
        AlarmSnoozeDurationDialogFragment f = new AlarmSnoozeDurationDialogFragment();
        Bundle a = new Bundle(); a.putString("arg_pref_key", prefKey); a.putInt("arg_edit_snooze_minutes", minutes); f.setArguments(a); return f;
    }
    public static AlarmSnoozeDurationDialogFragment newInstance(Alarm alarm, int minutes, boolean isTimer, String tag) {
        AlarmSnoozeDurationDialogFragment f = new AlarmSnoozeDurationDialogFragment();
        Bundle a = new Bundle(); a.putParcelable("arg_alarm", alarm); a.putInt("arg_edit_snooze_minutes", minutes); a.putBoolean("arg_is_timer", isTimer); a.putString("arg_tag", tag); f.setArguments(a); return f;
    }
    public static AlarmSnoozeDurationDialogFragment newInstance(String prefKey, int minutes, String tag) {
        return newInstance(prefKey, minutes, false);
    }
    public static void show(androidx.fragment.app.FragmentManager fm, AlarmSnoozeDurationDialogFragment f) { f.show(fm, "alarm_snooze"); }
    public interface SnoozeDurationDialogHandler {
        void onDialogSnoozeDurationSet(Alarm alarm, int snoozeDuration, String tag);
    }
}
