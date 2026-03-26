package com.best.deskclock.alarms;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.provider.Alarm;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AlarmDelayPickerDialogFragment extends DialogFragment {

    public static AlarmDelayPickerDialogFragment newInstance(Alarm alarm, String tag) {
        AlarmDelayPickerDialogFragment fragment = new AlarmDelayPickerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("arg_alarm", alarm);
        args.putString("arg_tag", tag);
        fragment.setArguments(args);
        return fragment;
    }

    public static void show(FragmentManager fragmentManager, AlarmDelayPickerDialogFragment fragment) {
        fragment.show(fragmentManager, "alarm_delay_picker_dialog");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Delay")
                .setItems(new String[]{"1 minute", "5 minutes", "10 minutes"}, (dialog, which) -> {
                    // Placeholder
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
