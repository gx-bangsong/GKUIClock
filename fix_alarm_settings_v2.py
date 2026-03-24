import sys

path = 'app/src/main/java/com/best/deskclock/settings/AlarmSettingsFragment.java'
with open(path, 'r') as f:
    content = f.read()

# Add missing imports
required_imports = [
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

import_insertion_point = 'package com.best.deskclock.settings;'
for imp in required_imports:
    if imp not in content:
        content = content.replace(import_insertion_point, import_insertion_point + '\\n' + imp)

with open(path, 'w') as f:
    f.write(content)
