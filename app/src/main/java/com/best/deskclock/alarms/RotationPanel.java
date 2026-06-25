package com.best.deskclock.alarms;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.button.MaterialButtonToggleGroup;

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
        mOpenGridButton.setOnClickListener(v -> showWizardDialog());
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

    private void showWizardDialog() {
        View dialogView = LayoutInflater.from(mContext).inflate(R.layout.dialog_rotation_grid, null);
        ViewFlipper flipper = dialogView.findViewById(R.id.wizard_flipper);
        TextView titleView = dialogView.findViewById(R.id.wizard_title);
        Button btnBack = dialogView.findViewById(R.id.btn_back);
        Button btnNext = dialogView.findViewById(R.id.btn_next);
        Button btnReset = dialogView.findViewById(R.id.btn_reset);

        // Step 1: Pattern
        NumberPicker pickerWork = dialogView.findViewById(R.id.picker_work);
        NumberPicker pickerRest = dialogView.findViewById(R.id.picker_rest);
        pickerWork.setMinValue(1); pickerWork.setMaxValue(31); pickerWork.setValue(5);
        pickerRest.setMinValue(0); pickerRest.setMaxValue(31); pickerRest.setValue(2);

        // Step 2: Assign
        RecyclerView workDayList = dialogView.findViewById(R.id.work_day_list);
        workDayList.setLayoutManager(new LinearLayoutManager(mContext));
        int[] workDayShifts = new int[31];
        for (int i = 0; i < 31; i++) workDayShifts[i] = 1; // Default all morning

        // Step 3: Preview
        RecyclerView previewGrid = dialogView.findViewById(R.id.rotation_preview_grid);
        previewGrid.setLayoutManager(new GridLayoutManager(mContext, 7));
        int[] tempRules = mRotationRules.clone();

        btnNext.setOnClickListener(v -> {
            int step = flipper.getDisplayedChild();
            if (step == 0) {
                // To Step 2
                WorkDayAdapter adapter = new WorkDayAdapter(pickerWork.getValue(), workDayShifts);
                workDayList.setAdapter(adapter);
                flipper.setDisplayedChild(1);
                titleView.setText(mContext.getString(R.string.wizard_step_2));
                btnBack.setVisibility(View.VISIBLE);
            } else if (step == 1) {
                // To Step 3 (Preview)
                int work = pickerWork.getValue();
                int rest = pickerRest.getValue();
                int cycle = work + rest;
                for (int i = 0; i < 64; i++) {
                    int dayInCycle = i % cycle;
                    tempRules[i] = (dayInCycle < work) ? workDayShifts[dayInCycle] : 0;
                }
                previewGrid.setAdapter(new PreviewAdapter(tempRules));
                flipper.setDisplayedChild(2);
                titleView.setText(mContext.getString(R.string.wizard_step_3));
                btnNext.setText(mContext.getString(R.string.wizard_finish));
            } else {
                // Finish
                System.arraycopy(tempRules, 0, mRotationRules, 0, 64);
                save();
                updateUI();
                ((AlertDialog) btnNext.getTag()).dismiss();
            }
        });

        btnBack.setOnClickListener(v -> {
            int step = flipper.getDisplayedChild();
            if (step == 1) {
                flipper.setDisplayedChild(0);
                titleView.setText(mContext.getString(R.string.wizard_step_1));
                btnBack.setVisibility(View.GONE);
            } else if (step == 2) {
                flipper.setDisplayedChild(1);
                titleView.setText(mContext.getString(R.string.wizard_step_2));
                btnNext.setText(mContext.getString(R.string.wizard_next));
            }
        });

        btnReset.setOnClickListener(v -> {
            for (int i = 0; i < 64; i++) mRotationRules[i] = 0;
            save();
            updateUI();
            Toast.makeText(mContext, mContext.getString(R.string.wizard_reset), Toast.LENGTH_SHORT).show();
            ((AlertDialog) btnNext.getTag()).dismiss();
        });

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(dialogView)
                .create();
        btnNext.setTag(dialog);
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

    // --- Adapters ---

    private class WorkDayAdapter extends RecyclerView.Adapter<WorkDayAdapter.ViewHolder> {
        private final int count;
        private final int[] shifts;

        public WorkDayAdapter(int count, int[] shifts) { this.count = count; this.shifts = shifts; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(mContext).inflate(R.layout.item_work_day_assign, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.label.setText("第 " + (position + 1) + " 天");
            int currentShift = shifts[position];
            if (currentShift == 1) holder.group.check(R.id.btn_morning);
            else if (currentShift == 2) holder.group.check(R.id.btn_afternoon);
            else if (currentShift == 3) holder.group.check(R.id.btn_night);

            holder.group.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btn_morning) shifts[position] = 1;
                else if (checkedId == R.id.btn_afternoon) shifts[position] = 2;
                else if (checkedId == R.id.btn_night) shifts[position] = 3;
            });
        }

        @Override
        public int getItemCount() { return count; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView label; MaterialButtonToggleGroup group;
            public ViewHolder(View v) {
                super(v);
                label = v.findViewById(R.id.day_label);
                group = v.findViewById(R.id.shift_toggle_group);
            }
        }
    }

    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {
        private final int[] rules;
        public PreviewAdapter(int[] rules) { this.rules = rules; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(mContext);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackgroundResource(android.R.drawable.btn_default);
            tv.setTextSize(10);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TextView tv = (TextView) holder.itemView;
            int rule = rules[position];
            tv.setText((position + 1) + "\n" + getShiftTypeName(rule));
            switch (rule) {
                case 1 -> { tv.setBackgroundColor(Color.parseColor("#E3F2FD")); tv.setTextColor(Color.parseColor("#1976D2")); }
                case 2 -> { tv.setBackgroundColor(Color.parseColor("#FFF3E0")); tv.setTextColor(Color.parseColor("#F57C00")); }
                case 3 -> { tv.setBackgroundColor(Color.parseColor("#F3E5F5")); tv.setTextColor(Color.parseColor("#7B1FA2")); }
                default -> { tv.setBackgroundColor(Color.parseColor("#F5F5F5")); tv.setTextColor(Color.LTGRAY); }
            }
            // Allow micro-adjustments in preview
            tv.setOnClickListener(v -> {
                rules[position] = (rules[position] + 1) % 4;
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return 64; }

        class ViewHolder extends RecyclerView.ViewHolder { public ViewHolder(View v) { super(v); } }
    }
}
