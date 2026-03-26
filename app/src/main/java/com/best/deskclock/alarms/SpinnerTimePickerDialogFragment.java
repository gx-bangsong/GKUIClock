package com.best.deskclock.alarms;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpinnerTimePickerDialogFragment extends DialogFragment {

    public static SpinnerTimePickerDialogFragment newInstance(int hours, int minutes) {
        SpinnerTimePickerDialogFragment fragment = new SpinnerTimePickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt("hours", hours);
        args.putInt("minutes", minutes);
        fragment.setArguments(args);
        return fragment;
    }

    public static void show(FragmentManager fragmentManager, SpinnerTimePickerDialogFragment fragment) {
        fragment.show(fragmentManager, "spinner_time_picker_dialog");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Time")
                .setMessage("Spinner implementation placeholder")
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
