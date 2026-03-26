package com.best.deskclock;
public class VibrationPatternDialogFragment extends androidx.fragment.app.DialogFragment {
    public static final String REQUEST_KEY = "vibration_pattern_request_key";
    public static final String RESULT_PREF_KEY = "vibration_pattern_pref_key";
    public static final String RESULT_PATTERN_KEY = "vibration_pattern_pattern_key";
    public static VibrationPatternDialogFragment newInstance(String prefKey, String pattern) { return new VibrationPatternDialogFragment(); }
    public static VibrationPatternDialogFragment newInstance(com.best.deskclock.provider.Alarm alarm, String pattern, String tag) { return new VibrationPatternDialogFragment(); }
    public static void show(androidx.fragment.app.FragmentManager fm, VibrationPatternDialogFragment f) { f.show(fm, "vibration_pattern"); }
}
