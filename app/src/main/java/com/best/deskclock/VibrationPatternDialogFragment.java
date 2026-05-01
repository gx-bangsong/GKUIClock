/*
 * Copyright (C) 2023 BlackyHawky
 * modified
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.best.deskclock;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.best.deskclock.provider.Alarm;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VibrationPatternDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "vibration_pattern_request_key";
    public static final String RESULT_PREF_KEY = "vibration_pattern_pref_key";
    public static final String RESULT_PATTERN_KEY = "vibration_pattern_pattern_key";

    private static final String ARG_PREF_KEY = "arg_pref_key";
    private static final String ARG_PATTERN = "arg_pattern";
    private static final String ARG_ALARM = "arg_alarm";
    private static final String ARG_TAG = "arg_tag";

    private String mPrefKey;
    private String mPattern;
    private Alarm mAlarm;
    private String mTag;

    public static VibrationPatternDialogFragment newInstance(String prefKey, String pattern) {
        final Bundle args = new Bundle();
        args.putString(ARG_PREF_KEY, prefKey);
        args.putString(ARG_PATTERN, pattern);

        final VibrationPatternDialogFragment frag = new VibrationPatternDialogFragment();
        frag.setArguments(args);
        return frag;
    }

    public static VibrationPatternDialogFragment newInstance(Alarm alarm, String pattern, String tag) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_ALARM, alarm);
        args.putString(ARG_PATTERN, pattern);
        args.putString(ARG_TAG, tag);

        final VibrationPatternDialogFragment frag = new VibrationPatternDialogFragment();
        frag.setArguments(args);
        return frag;
    }

    public static void show(FragmentManager fm, VibrationPatternDialogFragment f) {
        if (fm == null || fm.isDestroyed()) {
            return;
        }
        f.show(fm, "vibration_pattern");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        if (args != null) {
            mPrefKey = args.getString(ARG_PREF_KEY);
            mPattern = args.getString(ARG_PATTERN);
            mAlarm = args.getParcelable(ARG_ALARM);
            mTag = args.getString(ARG_TAG);
        }

        final View view = LayoutInflater.from(requireContext()).inflate(R.layout.vibration_pattern_dialog, null);
        final RadioGroup radioGroup = view.findViewById(R.id.vibration_pattern_radio_group);

        final String[] patterns = getResources().getStringArray(R.array.vibration_pattern_values);
        final int[] radioIds = {
                R.id.vibration_pattern_default,
                R.id.vibration_pattern_soft,
                R.id.vibration_pattern_strong,
                R.id.vibration_pattern_heartbeat,
                R.id.vibration_pattern_escalating,
                R.id.vibration_pattern_tick_tock
        };

        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i].equals(mPattern)) {
                ((RadioButton) radioGroup.findViewById(radioIds[i])).setChecked(true);
                break;
            }
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String newPattern = patterns[0];
            for (int i = 0; i < radioIds.length; i++) {
                if (radioIds[i] == checkedId) {
                    newPattern = patterns[i];
                    break;
                }
            }

            if (mAlarm != null) {
                ((VibrationPatternDialogHandler) requireActivity()).onDialogVibrationPatternSet(mAlarm, newPattern, mTag);
            } else if (mPrefKey != null) {
                final Bundle result = new Bundle();
                result.putString(RESULT_PREF_KEY, mPrefKey);
                result.putString(RESULT_PATTERN_KEY, newPattern);
                getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            }
            dismiss();
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vibration_pattern_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    public interface VibrationPatternDialogHandler {
        void onDialogVibrationPatternSet(Alarm alarm, String pattern, String tag);
    }
}
