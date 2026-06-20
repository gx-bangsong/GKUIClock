package com.best.deskclock.alarms;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class RotationPanel {

    private final View mRootView;
    private final Button mAnchorDateButton;
    private final Button mOpenGridButton;
    private final TextView mSummaryText;
    private final Context mContext;
    private final androidx.fragment.app.FragmentManager mFragmentManager;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private Alarm mAlarm;
    private OnRotationChangedListener mListener;
    private int[] mRotationRules = new int[64]; // 0: Off, 1: Morning, 2: Afternoon, 3: Night
    private long mAnchorTimestamp;

    public interface OnRotationChangedListener {
        void onRotationChanged(Alarm alarm, String payload);
    }

    public RotationPanel(View rootView, OnRotationChangedListener listener, androidx.fragment.app.FragmentManager fm) {
        if (rootView == null) throw new IllegalArgumentException("rootView cannot be null");
        mRootView = rootView;
        mContext = rootView.getContext();
        mListener = listener;
        mFragmentManager = fm;
        mAnchorDateButton = rootView.findViewById(R.id.rotation_anchor_date);
        mOpenGridButton = rootView.findViewById(R.id.btn_open_grid);
        mSummaryText = rootView.findViewById(R.id.rotation_summary);

        mAnchorDateButton.setOnClickListener(v -> showDatePicker());
        mOpenGridButton.setOnClickListener(v -> showGridDialog());
    }

    public void bind(Alarm alarm) {
        mAlarm = alarm;
        parsePayload(alarm.rotationPayload);
        updateUI();
    }

    private void updateUI() {
        mAnchorDateButton.setText(mDateFormat.format(mAnchorTimestamp));
        updateSummary();
    }

    private void updateSummary() {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(mAnchorTimestamp);
        anchor.set(Calendar.HOUR_OF_DAY, 0);
        anchor.set(Calendar.MINUTE, 0);
        anchor.set(Calendar.SECOND, 0);
        anchor.set(Calendar.MILLISECOND, 0);

        long diff = now.getTimeInMillis() - anchor.getTimeInMillis();
        int daysDiff = (int) (diff / (24 * 60 * 60 * 1000L));
        if (daysDiff < 0) {
            mSummaryText.setText("尚未开始轮班");
            return;
        }

        int cycleIndex = daysDiff % 64;
        int rule = mRotationRules[cycleIndex];
        String shiftType = getShiftTypeName(rule);
        mSummaryText.setText(mContext.getString(R.string.shift_summary_format, cycleIndex + 1, shiftType));
    }

    private String getShiftTypeName(int rule) {
        return switch (rule) {
            case 1 -> mContext.getString(R.string.shift_type_morning);
            case 2 -> mContext.getString(R.string.shift_type_afternoon);
            case 3 -> mContext.getString(R.string.shift_type_night);
            default -> mContext.getString(R.string.shift_type_off);
        };
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(mAnchorTimestamp)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            mAnchorTimestamp = selection;
            save();
            updateUI();
        });
        picker.show(mFragmentManager, "rotation_anchor_date");
    }

    private void showGridDialog() {
        View dialogView = LayoutInflater.from(mContext).inflate(R.layout.dialog_rotation_grid, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rotation_grid_full);
        recyclerView.setLayoutManager(new GridLayoutManager(mContext, 7)); // Calendar style (7 days per week)

        int[] tempRules = mRotationRules.clone();
        RotationAdapter adapter = new RotationAdapter(tempRules);
        recyclerView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            System.arraycopy(tempRules, 0, mRotationRules, 0, 64);
            save();
            updateUI();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void parsePayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            mAnchorTimestamp = System.currentTimeMillis();
            for (int i = 0; i < 64; i++) mRotationRules[i] = 0;
            return;
        }
        String[] parts = payload.split(",");
        try {
            mAnchorTimestamp = Long.parseLong(parts[0]);
            for (int i = 0; i < 64; i++) {
                if (i + 1 < parts.length) {
                    mRotationRules[i] = Integer.parseInt(parts[i+1]);
                } else {
                    mRotationRules[i] = 0;
                }
            }
        } catch (Exception e) {
            mAnchorTimestamp = System.currentTimeMillis();
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        sb.append(mAnchorTimestamp);
        for (int val : mRotationRules) {
            sb.append(",").append(val);
        }
        mAlarm.rotationPayload = sb.toString();
        mListener.onRotationChanged(mAlarm, mAlarm.rotationPayload);
    }

    private class RotationAdapter extends RecyclerView.Adapter<RotationAdapter.ViewHolder> {
        private final int[] rules;
        public RotationAdapter(int[] rules) { this.rules = rules; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(mContext);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 120));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackgroundResource(android.R.drawable.btn_default);
            tv.setTextSize(10);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TextView tv = (TextView) holder.itemView;
            int rule = rules[position];

            // Format text: Day number + Shift Name
            String dayNum = String.valueOf(position + 1);
            String typeName = getShiftTypeName(rule);
            tv.setText(dayNum + "\n" + typeName);

            // Color Coding
            switch (rule) {
                case 1 -> { // Morning - Blue
                    tv.setBackgroundColor(Color.parseColor("#E3F2FD"));
                    tv.setTextColor(Color.parseColor("#1976D2"));
                }
                case 2 -> { // Afternoon - Orange
                    tv.setBackgroundColor(Color.parseColor("#FFF3E0"));
                    tv.setTextColor(Color.parseColor("#F57C00"));
                }
                case 3 -> { // Night - Purple
                    tv.setBackgroundColor(Color.parseColor("#F3E5F5"));
                    tv.setTextColor(Color.parseColor("#7B1FA2"));
                }
                default -> { // Off - Gray
                    tv.setBackgroundColor(Color.parseColor("#F5F5F5"));
                    tv.setTextColor(Color.LTGRAY);
                }
            }

            tv.setOnClickListener(v -> {
                rules[position] = (rules[position] + 1) % 4; // Cycle 0-3
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return 64; }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) { super(itemView); }
        }
    }
}
