package com.best.deskclock;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.provider.Alarm;

public class AutoSilenceDurationDialogFragment extends DialogFragment {
    public static final String REQUEST_KEY = "auto_silence_request_key";
    public static final String RESULT_PREF_KEY = "auto_silence_pref_key";
    public static final String AUTO_SILENCE_DURATION_VALUE = "auto_silence_duration_value";

    private static final String ARG_ALARM = "arg_alarm";
    private static final String ARG_MINUTES = "arg_edit_auto_silence_minutes";
    private static final String ARG_IS_TIMER = "arg_is_timer";
    private static final String ARG_TAG = "arg_tag";

    public static AutoSilenceDurationDialogFragment newInstance(String prefKey, int minutes, boolean isSnooze, boolean isTimer) {
        AutoSilenceDurationDialogFragment f = new AutoSilenceDurationDialogFragment();
        Bundle a = new Bundle();
        a.putString("arg_pref_key", prefKey);
        a.putInt(ARG_MINUTES, minutes);
        a.putBoolean(ARG_IS_TIMER, isTimer);
        f.setArguments(a);
        return f;
    }

    public static AutoSilenceDurationDialogFragment newInstance(Alarm alarm, int minutes, boolean isTimer, boolean isSnooze, String tag) {
        AutoSilenceDurationDialogFragment f = new AutoSilenceDurationDialogFragment();
        Bundle a = new Bundle();
        a.putParcelable(ARG_ALARM, alarm);
        a.putInt(ARG_MINUTES, minutes);
        a.putBoolean(ARG_IS_TIMER, isTimer);
        a.putString(ARG_TAG, tag);
        f.setArguments(a);
        return f;
    }

    public static AutoSilenceDurationDialogFragment newInstance(String prefKey, int minutes, String tag) {
        return newInstance(prefKey, minutes, false, false);
    }

    public static void show(FragmentManager fm, AutoSilenceDurationDialogFragment f) {
        f.show(fm, "auto_silence");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Alarm alarm = getArguments().getParcelable(ARG_ALARM);
        final int minutes = getArguments().getInt(ARG_MINUTES);
        final String tag = getArguments().getString(ARG_TAG);

        final String[] items = {"1", "5", "10", "15", "20", "25", "30"};
        int selectedIndex = -1;
        for (int i = 0; i < items.length; i++) {
            if (Integer.parseInt(items[i]) == minutes) {
                selectedIndex = i;
                break;
            }
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auto_silence_title)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    if (getActivity() instanceof AutoSilenceDurationDialogHandler) {
                        ((AutoSilenceDurationDialogHandler) getActivity())
                                .onDialogAutoSilenceDurationSet(alarm, Integer.parseInt(items[which]), tag);
                    }
                    dismiss();
                })
                .create();
    }

    public interface AutoSilenceDurationDialogHandler {
        void onDialogAutoSilenceDurationSet(Alarm alarm, int autoSilenceDuration, String tag);
    }
}
