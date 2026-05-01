/*
 * Copyright (C) 2023 BlackyHawky
 * modified
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.best.deskclock;

import static com.best.deskclock.settings.PreferencesDefaultValues.DEFAULT_VIBRATION_START_DELAY;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VibrationStartDelayDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "vibration_start_delay_request_key";
    public static final String RESULT_PREF_KEY = "vibration_start_delay_pref_key";
    public static final String VIBRATION_DELAY_VALUE = "vibration_delay_value";

    private static final String ARG_PREF_KEY = "arg_pref_key";
    private static final String ARG_VALUE = "arg_value";

    private String mPrefKey;
    private int mValue;

    public static VibrationStartDelayDialogFragment newInstance(String prefKey, int value, String tag) {
        final Bundle args = new Bundle();
        args.putString(ARG_PREF_KEY, prefKey);
        args.putInt(ARG_VALUE, value);

        final VibrationStartDelayDialogFragment frag = new VibrationStartDelayDialogFragment();
        frag.setArguments(args);
        return frag;
    }

    public static void show(FragmentManager fm, VibrationStartDelayDialogFragment f) {
        if (fm == null || fm.isDestroyed()) {
            return;
        }
        f.show(fm, "vibration_delay");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        if (args != null) {
            mPrefKey = args.getString(ARG_PREF_KEY);
            mValue = args.getInt(ARG_VALUE);
        }

        final View view = LayoutInflater.from(requireContext()).inflate(R.layout.alarm_vibration_start_delay_dialog, null);
        final EditText editMinutes = view.findViewById(R.id.edit_minutes);
        final CheckBox endOfRingtone = view.findViewById(R.id.end_of_ringtone);

        if (mValue == -1) {
            endOfRingtone.setChecked(true);
            editMinutes.setEnabled(false);
        } else {
            editMinutes.setText(String.valueOf(mValue / 60));
        }

        editMinutes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    endOfRingtone.setChecked(false);
                }
            }
        });

        endOfRingtone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                editMinutes.setText("");
                editMinutes.setEnabled(false);
            } else {
                editMinutes.setEnabled(true);
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vibration_start_delay_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int newValue;
                    if (endOfRingtone.isChecked()) {
                        newValue = -1;
                    } else {
                        String minutesStr = editMinutes.getText().toString();
                        newValue = TextUtils.isEmpty(minutesStr) ? DEFAULT_VIBRATION_START_DELAY : Integer.parseInt(minutesStr) * 60;
                    }

                    final Bundle result = new Bundle();
                    result.putString(RESULT_PREF_KEY, mPrefKey);
                    result.putInt(VIBRATION_DELAY_VALUE, newValue);
                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                    Utils.setVibrationTime(requireContext(), 50);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
