package com.best.deskclock.alarms;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;

public class AlarmMissedRepeatLimitDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "alarm_missed_repeat_limit_request_key";
    public static final String RESULT_PREF_KEY = "alarm_missed_repeat_limit_pref_key";
    public static final String MISSED_ALARM_REPEAT_LIMIT_VALUE = "missed_alarm_repeat_limit_value";

    private static final String ARG_ALARM = "arg_alarm";
    private static final String ARG_REPEAT_LIMIT = "arg_repeat_limit";
    private static final String ARG_TAG = "arg_tag";

    public static AlarmMissedRepeatLimitDialogFragment newInstance(Alarm alarm, int repeatLimit, String tag) {
        AlarmMissedRepeatLimitDialogFragment f = new AlarmMissedRepeatLimitDialogFragment();
        Bundle a = new Bundle();
        a.putParcelable(ARG_ALARM, alarm);
        a.putInt(ARG_REPEAT_LIMIT, repeatLimit);
        a.putString(ARG_TAG, tag);
        f.setArguments(a);
        return f;
    }

    public static void show(FragmentManager fm, AlarmMissedRepeatLimitDialogFragment f) {
        f.show(fm, "missed_repeat_limit");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Alarm alarm = getArguments().getParcelable(ARG_ALARM);
        final int repeatLimit = getArguments().getInt(ARG_REPEAT_LIMIT);
        final String tag = getArguments().getString(ARG_TAG);

        final String[] items = new String[11];
        for (int i = 0; i <= 10; i++) {
            items[i] = String.valueOf(i);
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.missed_alarm_repeat_limit_title)
                .setSingleChoiceItems(items, repeatLimit, (dialog, which) -> {
                    if (getActivity() instanceof AlarmMissedRepeatLimitDialogHandler) {
                        ((AlarmMissedRepeatLimitDialogHandler) getActivity())
                                .onDialogMissedRepeatLimitSet(alarm, which, tag);
                    }
                    dismiss();
                })
                .create();
    }

    public interface AlarmMissedRepeatLimitDialogHandler {
        void onDialogMissedRepeatLimitSet(Alarm alarm, int repeatLimit, String tag);
    }
}
