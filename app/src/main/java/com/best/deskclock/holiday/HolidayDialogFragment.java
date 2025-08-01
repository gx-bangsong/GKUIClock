/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.best.deskclock.holiday;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.best.deskclock.R;

public class HolidayDialogFragment extends DialogFragment {

    public interface HolidayDialogListener {
        void onHolidayOptionSelected(int option);
    }

    private HolidayDialogListener listener;

    public static HolidayDialogFragment newInstance(HolidayDialogListener listener) {
        HolidayDialogFragment fragment = new HolidayDialogFragment();
        fragment.listener = listener;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.holiday_dialog, null);

        TextView skipHoliday = view.findViewById(R.id.skip_holiday);
        TextView bigWeek = view.findViewById(R.id.big_week);
        TextView smallWeek = view.findViewById(R.id.small_week);
        TextView singleDayOff = view.findViewById(R.id.single_day_off);

        skipHoliday.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHolidayOptionSelected(0);
            }
            dismiss();
        });

        bigWeek.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHolidayOptionSelected(1);
            }
            dismiss();
        });

        smallWeek.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHolidayOptionSelected(2);
            }
            dismiss();
        });

        singleDayOff.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHolidayOptionSelected(3);
            }
            dismiss();
        });

        builder.setView(view);
        return builder.create();
    }
}
