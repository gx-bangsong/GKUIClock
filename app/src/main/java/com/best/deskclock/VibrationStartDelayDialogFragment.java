package com.best.deskclock;
public class VibrationStartDelayDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "vibration_start_delay_request_key";
    public static final String RESULT_PREF_KEY = "vibration_start_delay_pref_key";
    public static final String VIBRATION_DELAY_VALUE = "vibration_delay_value";
    public static VibrationStartDelayDialogFragment newInstance(String prefKey, int value, String tag) { return new VibrationStartDelayDialogFragment(); }
    public static void show(androidx.fragment.app.FragmentManager fm, VibrationStartDelayDialogFragment f) { f.show(fm, "vibration_delay"); }
}
