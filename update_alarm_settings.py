import sys

path = 'app/src/main/java/com/best/deskclock/settings/AlarmSettingsFragment.java'
with open(path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip() == 'import com.best.deskclock.holiday.HolidayRepository;':
        continue
    if line.startswith('package com.best.deskclock.settings;'):
        new_lines.append(line)
        new_lines.append('import com.best.deskclock.holiday.HolidayRepository;\n')
    else:
        new_lines.append(line)

# Add holiday preference logic
end_of_on_create = -1
for i, line in enumerate(new_lines):
    if 'mDeleteOccasionalAlarmByDefaultPref.setOnPreferenceChangeListener(this);' in line:
        end_of_on_create = i + 1
        break

if end_of_on_create != -1:
    holiday_logic = '''
        Preference updateHolidayDataPref = findPreference(KEY_UPDATE_HOLIDAY_DATA);
        if (updateHolidayDataPref != null) {
            updateHolidayDataPref.setOnPreferenceClickListener(preference -> {
                HolidayRepository.getInstance(requireContext()).updateWorkdayData();
                return true;
            });
        }

        mHolidayDataUrlPref = findPreference(KEY_HOLIDAY_DATA_URL);
        if (mHolidayDataUrlPref != null) {
            mHolidayDataUrlPref.setSummary(SettingsDAO.getHolidayDataUrl(mPrefs));
            mHolidayDataUrlPref.setOnPreferenceChangeListener(this);
        }
'''
    new_lines.insert(end_of_on_create, holiday_logic)

# Add field declaration
for i, line in enumerate(new_lines):
    if 'private CustomSwitchPreference mDeleteOccasionalAlarmByDefaultPref;' in line:
        new_lines.insert(i + 1, '    private androidx.preference.EditTextPreference mHolidayDataUrlPref;\n')
        break

with open(path, 'w') as f:
    f.writelines(new_lines)
