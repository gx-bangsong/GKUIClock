package com.best.deskclock;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.provider.Alarm;

public class VolumeCrescendoDurationDialogFragment extends DialogFragment {
    public static final String REQUEST_KEY = "volume_crescendo_request_key";
    public static final String RESULT_PREF_KEY = "volume_crescendo_pref_key";
    public static final String VOLUME_CRESCENDO_DURATION_VALUE = "volume_crescendo_duration_value";

    private static final String ARG_ALARM = "arg_alarm";
    private static final String ARG_DURATION = "arg_edit_crescendo_duration";
    private static final String ARG_IS_TIMER = "arg_is_timer";
    private static final String ARG_TAG = "arg_tag";

    public static VolumeCrescendoDurationDialogFragment newInstance(String prefKey, int duration) {
        VolumeCrescendoDurationDialogFragment f = new VolumeCrescendoDurationDialogFragment();
        Bundle a = new Bundle();
        a.putString("arg_pref_key", prefKey);
        a.putInt(ARG_DURATION, duration);
        f.setArguments(a);
        return f;
    }

    public static VolumeCrescendoDurationDialogFragment newInstance(Alarm alarm, int duration, boolean isTimer, String tag) {
        VolumeCrescendoDurationDialogFragment f = new VolumeCrescendoDurationDialogFragment();
        Bundle a = new Bundle();
        a.putParcelable(ARG_ALARM, alarm);
        a.putInt(ARG_DURATION, duration);
        a.putBoolean(ARG_IS_TIMER, isTimer);
        a.putString(ARG_TAG, tag);
        f.setArguments(a);
        return f;
    }

    public static VolumeCrescendoDurationDialogFragment newInstance(String prefKey, int duration, String tag) {
        return newInstance(prefKey, duration);
    }

    public static void show(FragmentManager fm, VolumeCrescendoDurationDialogFragment f) {
        f.show(fm, "volume_crescendo");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Alarm alarm = getArguments().getParcelable(ARG_ALARM);
        final int duration = getArguments().getInt(ARG_DURATION);
        final String tag = getArguments().getString(ARG_TAG);

        final String[] items = {"0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60"};
        int selectedIndex = -1;
        for (int i = 0; i < items.length; i++) {
            if (Integer.parseInt(items[i]) == duration) {
                selectedIndex = i;
                break;
            }
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.crescendo_duration_title)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    if (getActivity() instanceof VolumeCrescendoDurationDialogHandler) {
                        ((VolumeCrescendoDurationDialogHandler) getActivity())
                                .onDialogCrescendoDurationSet(alarm, Integer.parseInt(items[which]), tag);
                    }
                    dismiss();
                })
                .create();
    }

    public interface VolumeCrescendoDurationDialogHandler {
        void onDialogCrescendoDurationSet(Alarm alarm, int volumeCrescendoDuration, String tag);
    }
}
