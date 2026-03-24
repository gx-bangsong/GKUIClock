import sys

path = 'app/src/main/java/com/best/deskclock/settings/AlarmSettingsFragment.java'
with open(path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.startswith('package com.best.deskclock.settings;'):
        new_lines.append(line)
        new_lines.append('import androidx.preference.Preference;\n')
        new_lines.append('import androidx.preference.SwitchPreferenceCompat;\n')
        new_lines.append('import androidx.preference.ListPreference;\n')
        new_lines.append('import androidx.preference.EditTextPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.AlarmSnoozeDurationPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.AlarmVolumePreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.AutoSilenceDurationPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.CustomListPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.CustomPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.CustomPreferenceCategory;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.CustomSeekbarPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.CustomSwitchPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.VibrationPatternPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.VibrationStartDelayPreference;\n')
        new_lines.append('import com.best.deskclock.settings.custompreference.VolumeCrescendoDurationPreference;\n')
    elif not line.strip().startswith('import androidx.preference.') and \
         not line.strip().startswith('import com.best.deskclock.settings.custompreference.') and \
         'illegal character' not in line:
        new_lines.append(line)

# Remove duplicates if any
final_lines = []
seen = set()
for line in new_lines:
    if line.startswith('import ') or line.startswith('package '):
        if line not in seen:
            final_lines.append(line)
            seen.add(line)
    else:
        final_lines.append(line)

with open(path, 'w') as f:
    f.writelines(final_lines)
