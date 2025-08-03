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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.R;
import com.best.deskclock.provider.Alarm;

public class HolidayDialogFragment extends DialogFragment {

    private static final String ARG_ALARM = "alarm";

    public static void show(FragmentManager manager, Alarm alarm) {
        final HolidayDialogFragment dialog = new HolidayDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_ALARM, alarm);
        dialog.setArguments(args);
        dialog.show(manager, "holiday");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Alarm alarm = getArguments().getParcelable(ARG_ALARM);

        final CharSequence[] items = {
                getString(R.string.skip_holiday),
                getString(R.string.daxiao_da),
                getString(R.string.daxiao_xiao),
                getString(R.string.danxiu)
        };

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.holiday_title)
                .setItems(items, (dialog, which) -> {
                    alarm.holidayOption = which;
                })
                .create();
    }
}
