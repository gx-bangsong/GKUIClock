import sys

path = 'app/src/main/java/com/best/deskclock/settings/AlarmSettingsFragment.java'
with open(path, 'r') as f:
    content = f.read()

# Fix the broken package line
content = content.replace('package com.best.deskclock.settings;\\nimport androidx.preference.EditTextPreference;\\nimport androidx.preference.ListPreference;\\nimport androidx.preference.SwitchPreferenceCompat;',
                          'package com.best.deskclock.settings;')

# Clean up imports
import_list = [
    'package com.best.deskclock.settings;',
    'import androidx.preference.Preference;',
    'import androidx.preference.SwitchPreferenceCompat;',
    'import androidx.preference.ListPreference;',
    'import androidx.preference.EditTextPreference;',
    'import com.best.deskclock.settings.custompreference.AlarmSnoozeDurationPreference;',
    'import com.best.deskclock.settings.custompreference.AlarmVolumePreference;',
    'import com.best.deskclock.settings.custompreference.AutoSilenceDurationPreference;',
    'import com.best.deskclock.settings.custompreference.CustomListPreference;',
    'import com.best.deskclock.settings.custompreference.CustomPreference;',
    'import com.best.deskclock.settings.custompreference.CustomPreferenceCategory;',
    'import com.best.deskclock.settings.custompreference.CustomSeekbarPreference;',
    'import com.best.deskclock.settings.custompreference.CustomSwitchPreference;',
    'import com.best.deskclock.settings.custompreference.VibrationPatternPreference;',
    'import com.best.deskclock.settings.custompreference.VibrationStartDelayPreference;',
    'import com.best.deskclock.settings.custompreference.VolumeCrescendoDurationPreference;'
]

lines = content.splitlines()
new_lines = []
skip_imports = False
for line in lines:
    if line.startswith('package '):
        new_lines.extend(import_list)
        skip_imports = True
        continue
    if skip_imports:
        if line.startswith('import ') or not line.strip():
            continue
        else:
            skip_imports = False
    new_lines.append(line)

with open(path, 'w') as f:
    f.write('\\n'.join(new_lines))
