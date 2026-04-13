package com.best.deskclock.holiday;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;

public class HolidayDialogFragment extends DialogFragment {

    private static final String ARG_ALARM = "alarm";

    public static HolidayDialogFragment newInstance(Alarm alarm) {
        final HolidayDialogFragment dialog = new HolidayDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_ALARM, alarm);
        dialog.setArguments(args);
        return dialog;
    }

    public static void show(FragmentManager manager, HolidayDialogFragment fragment) {
        fragment.show(manager, "holiday");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Alarm alarm = getArguments().getParcelable(ARG_ALARM);

        final CharSequence[] items = {
                getString(R.string.label_off),
                getString(R.string.skip_holiday),
                getString(R.string.daxiao_da),
                getString(R.string.daxiao_xiao),
                getString(R.string.danxiu)
        };

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.holiday_title)
                .setSingleChoiceItems(items, alarm.holidayOption, (dialog, which) -> {
                    if (getActivity() instanceof HolidayDialogHandler) {
                        ((HolidayDialogHandler) getActivity()).onDialogHolidayOptionSet(alarm, which);
                    }
                    dismiss();
                })
                .create();
    }

    public interface HolidayDialogHandler {
        void onDialogHolidayOptionSet(Alarm alarm, int holidayOption);
    }
}
