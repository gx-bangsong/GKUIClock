import sys

path = 'app/src/main/java/com/best/deskclock/settings/AlarmSettingsFragment.java'
with open(path, 'r') as f:
    content = f.read()

# Add to onPreferenceChange
if 'case KEY_HOLIDAY_DATA_URL -> {' not in content:
    content = content.replace('switch (preference.getKey()) {',
                              'switch (preference.getKey()) {\\n            case KEY_HOLIDAY_DATA_URL -> {\\n                String url = (String) newValue;\\n                SettingsDAO.setHolidayDataUrl(mPrefs, url);\\n                mHolidayDataUrlPref.setSummary(url);\\n                return true;\\n            }')

with open(path, 'w') as f:
    f.write(content)
