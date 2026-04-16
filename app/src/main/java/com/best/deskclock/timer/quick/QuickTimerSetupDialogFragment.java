package com.best.deskclock.timer.quick;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.best.deskclock.R;

public class QuickTimerSetupDialogFragment extends DialogFragment {

    public interface QuickTimerSetupListener {
        void onQuickTimerSetup(long duration, String label);
    }

    private QuickTimerSetupListener mListener;

    public void setListener(QuickTimerSetupListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quick_timer_setup, null);
        EditText labelEdit = view.findViewById(R.id.quick_timer_label);
        EditText hoursEdit = view.findViewById(R.id.quick_timer_hours);
        EditText minutesEdit = view.findViewById(R.id.quick_timer_minutes);
        EditText secondsEdit = view.findViewById(R.id.quick_timer_seconds);

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_quick_timer_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (mListener != null) {
                        long hours = getLong(hoursEdit);
                        long minutes = getLong(minutesEdit);
                        long seconds = getLong(secondsEdit);
                        long duration = (hours * 3600 + minutes * 60 + seconds) * 1000;
                        if (duration > 0) {
                            mListener.onQuickTimerSetup(duration, labelEdit.getText().toString());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private long getLong(EditText edit) {
        String text = edit.getText().toString();
        try {
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
