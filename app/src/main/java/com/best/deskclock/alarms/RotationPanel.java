package com.best.deskclock.alarms;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class RotationPanel {

    private final View mRootView;
    private final Button mAnchorDateButton;
    private final RecyclerView mGrid;
    private final Context mContext;
    private final androidx.fragment.app.FragmentManager mFragmentManager;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private Alarm mAlarm;
    private OnRotationChangedListener mListener;
    private int[] mRotationRules = new int[64];
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
        mGrid = rootView.findViewById(R.id.rotation_grid);

        mGrid.setLayoutManager(new GridLayoutManager(mContext, 8));
        mGrid.setAdapter(new RotationAdapter());

        mAnchorDateButton.setOnClickListener(v -> showDatePicker());
    }

    public void bind(Alarm alarm) {
        mAlarm = alarm;
        parsePayload(alarm.rotationPayload);
        updateUI();
    }

    private void updateUI() {
        mAnchorDateButton.setText(mDateFormat.format(mAnchorTimestamp));
        if (mGrid.getAdapter() != null) {
            mGrid.getAdapter().notifyDataSetChanged();
        }
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
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(mContext);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 100));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackgroundResource(android.R.drawable.btn_default);
            tv.setTextSize(12);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TextView tv = (TextView) holder.itemView;
            tv.setText(String.valueOf(position + 1));
            int state = mRotationRules[position];
            if (state == 0) {
                tv.setBackgroundColor(Color.LTGRAY);
                tv.setTextColor(Color.DKGRAY);
            } else if (state == 1) {
                tv.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
                tv.setTextColor(Color.WHITE);
            } else {
                tv.setBackgroundColor(Color.parseColor("#FF9800")); // Orange
                tv.setTextColor(Color.WHITE);
            }

            tv.setOnClickListener(v -> {
                mRotationRules[position] = (mRotationRules[position] + 1) % 3;
                save();
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
