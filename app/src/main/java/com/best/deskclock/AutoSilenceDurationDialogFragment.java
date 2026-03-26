package com.best.deskclock;
import android.os.Bundle;
import com.best.deskclock.provider.Alarm;
public class AutoSilenceDurationDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "auto_silence_request_key";
    public static final String RESULT_PREF_KEY = "auto_silence_pref_key";
    public static final String AUTO_SILENCE_DURATION_VALUE = "auto_silence_duration_value";
    public static AutoSilenceDurationDialogFragment newInstance(String prefKey, int minutes, boolean isSnooze, boolean isTimer) {
        AutoSilenceDurationDialogFragment f = new AutoSilenceDurationDialogFragment();
        Bundle a = new Bundle(); a.putString("arg_pref_key", prefKey); a.putInt("arg_edit_auto_silence_minutes", minutes); f.setArguments(a); return f;
    }
    public static AutoSilenceDurationDialogFragment newInstance(Alarm alarm, int minutes, boolean isTimer, boolean isSnooze, String tag) {
        AutoSilenceDurationDialogFragment f = new AutoSilenceDurationDialogFragment();
        Bundle a = new Bundle(); a.putParcelable("arg_alarm", alarm); a.putInt("arg_edit_auto_silence_minutes", minutes); a.putBoolean("arg_is_timer", isTimer); a.putString("arg_tag", tag); f.setArguments(a); return f;
    }
    public static AutoSilenceDurationDialogFragment newInstance(String prefKey, int minutes, String tag) {
        return newInstance(prefKey, minutes, false, false);
    }
    public static void show(androidx.fragment.app.FragmentManager fm, AutoSilenceDurationDialogFragment f) { f.show(fm, "auto_silence"); }
    public interface AutoSilenceDurationDialogHandler {
        void onDialogAutoSilenceDurationSet(Alarm alarm, int autoSilenceDuration, String tag);
    }
}
