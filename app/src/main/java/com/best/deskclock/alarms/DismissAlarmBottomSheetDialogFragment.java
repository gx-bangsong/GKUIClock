package com.best.deskclock.alarms;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.utils.AlarmUtils;
import com.best.deskclock.utils.Utils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;

/**
 * BottomSheet that appears when user turns OFF a repeating alarm.
 * Asks whether to skip just the next occurrence or disable the repeating alarm entirely.
 */
public class DismissAlarmBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_ALARM_ID = "alarm_id";
    private static final String ARG_INSTANCE_ID = "instance_id";
    private static final String ARG_LABEL = "label";
    private static final String ARG_HOUR = "hour";
    private static final String ARG_MINUTE = "minute";

    public interface OnDismissOptionSelectedListener {
        void onSkipOnceSelected(Alarm alarm, AlarmInstance instance);
        void onDisableRepeatingSelected(Alarm alarm);
    }

    private OnDismissOptionSelectedListener mListener;
    private Alarm mAlarm;
    private AlarmInstance mInstance;

    public static DismissAlarmBottomSheetDialogFragment newInstance(Alarm alarm, @Nullable AlarmInstance instance) {
        DismissAlarmBottomSheetDialogFragment frag = new DismissAlarmBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ALARM_ID, alarm.id);
        args.putLong(ARG_INSTANCE_ID, instance != null ? instance.mId : AlarmInstance.INVALID_ID);
        args.putString(ARG_LABEL, alarm.label);
        args.putInt(ARG_HOUR, alarm.hour);
        args.putInt(ARG_MINUTE, alarm.minutes);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Try to find listener from parent fragment or activity
        if (getParentFragment() instanceof OnDismissOptionSelectedListener) {
            mListener = (OnDismissOptionSelectedListener) getParentFragment();
        } else if (context instanceof OnDismissOptionSelectedListener) {
            mListener = (OnDismissOptionSelectedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load alarm and instance from DB
        Bundle args = getArguments();
        if (args != null) {
            long alarmId = args.getLong(ARG_ALARM_ID, Alarm.INVALID_ID);
            long instanceId = args.getLong(ARG_INSTANCE_ID, AlarmInstance.INVALID_ID);
            Context ctx = requireContext();
            mAlarm = Alarm.getAlarm(ctx.getContentResolver(), alarmId);
            if (instanceId != AlarmInstance.INVALID_ID) {
                mInstance = AlarmInstance.getInstance(ctx.getContentResolver(), instanceId);
            }
            if (mInstance == null && mAlarm != null) {
                // Fallback: get next upcoming instance
                mInstance = AlarmInstance.getNextUpcomingInstanceByAlarmId(ctx.getContentResolver(), alarmId);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // Ensure background is transparent for rounded corners if needed
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_dismiss_alarm, container, false);

        TextView subtitle = view.findViewById(R.id.bottom_sheet_subtitle);
        TextView skipSummary = view.findViewById(R.id.option_skip_once_summary);
        TextView nextRing = view.findViewById(R.id.option_skip_once_next_ring);
        View optionSkipOnce = view.findViewById(R.id.option_skip_once);
        View optionDisable = view.findViewById(R.id.option_disable_repeating);

        if (mAlarm != null) {
            Context ctx = requireContext();
            String timeText = android.text.format.DateFormat.getTimeFormat(ctx).format(
                    new java.util.Date(0, 0, 0, mAlarm.hour, mAlarm.minutes));
            // Show label if exists
            if (mAlarm.label != null && !mAlarm.label.isEmpty()) {
                subtitle.setText(mAlarm.label + " • " + timeText);
                subtitle.setVisibility(View.VISIBLE);
            } else {
                subtitle.setText(timeText);
                subtitle.setVisibility(View.VISIBLE);
            }

            if (mInstance != null) {
                Calendar nextAfterSkip = mAlarm.getNextAlarmTime(mInstance.getAlarmTime());
                // For repeating alarms, next after current instance
                String nextTime = AlarmUtils.getFormattedTime(ctx, nextAfterSkip);
                nextRing.setText(ctx.getString(R.string.dismiss_alarm_next_ring, nextTime));
                nextRing.setVisibility(View.VISIBLE);
            }
        }

        optionSkipOnce.setOnClickListener(v -> {
            Utils.setVibrationTime(requireContext(), 30);
            if (mListener != null && mAlarm != null) {
                mListener.onSkipOnceSelected(mAlarm, mInstance);
            }
            dismiss();
        });

        optionDisable.setOnClickListener(v -> {
            Utils.setVibrationTime(requireContext(), 30);
            if (mListener != null && mAlarm != null) {
                mListener.onDisableRepeatingSelected(mAlarm);
            }
            dismiss();
        });

        return view;
    }

    public void setOnDismissOptionSelectedListener(OnDismissOptionSelectedListener listener) {
        mListener = listener;
    }
}
