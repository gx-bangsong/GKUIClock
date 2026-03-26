package com.best.deskclock;
import android.os.Bundle;
import com.best.deskclock.provider.Alarm;
public class VolumeCrescendoDurationDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "volume_crescendo_request_key";
    public static final String RESULT_PREF_KEY = "volume_crescendo_pref_key";
    public static final String VOLUME_CRESCENDO_DURATION_VALUE = "volume_crescendo_duration_value";
    public static VolumeCrescendoDurationDialogFragment newInstance(String prefKey, int duration) {
        VolumeCrescendoDurationDialogFragment f = new VolumeCrescendoDurationDialogFragment();
        Bundle a = new Bundle(); a.putString("arg_pref_key", prefKey); a.putInt("arg_edit_crescendo_duration", duration); f.setArguments(a); return f;
    }
    public static VolumeCrescendoDurationDialogFragment newInstance(Alarm alarm, int duration, boolean isTimer, String tag) {
        VolumeCrescendoDurationDialogFragment f = new VolumeCrescendoDurationDialogFragment();
        Bundle a = new Bundle(); a.putParcelable("arg_alarm", alarm); a.putInt("arg_edit_crescendo_duration", duration); a.putBoolean("arg_is_timer", isTimer); a.putString("arg_tag", tag); f.setArguments(a); return f;
    }
    public static VolumeCrescendoDurationDialogFragment newInstance(String prefKey, int duration, String tag) {
        return newInstance(prefKey, duration);
    }
    public static void show(androidx.fragment.app.FragmentManager fm, VolumeCrescendoDurationDialogFragment f) { f.show(fm, "volume_crescendo"); }
    public interface VolumeCrescendoDurationDialogHandler {
        void onDialogCrescendoDurationSet(Alarm alarm, int volumeCrescendoDuration, String tag);
    }
}
