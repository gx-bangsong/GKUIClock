import sys

keys_path = 'app/src/main/java/com/best/deskclock/settings/PreferencesKeys.java'
with open(keys_path, 'r') as f:
    keys_content = f.read()

missing_keys = [
    ('KEY_ALARM_DIGITAL_CLOCK_FONT_SIZE', '"key_alarm_digital_clock_font_size"'),
    ('KEY_DISPLAY_ALARM_SECOND_HAND', '"key_display_alarm_second_hand"'),
    ('KEY_TIMER_AUTO_SILENCE_DURATION', '"key_timer_auto_silence_duration"'),
    ('KEY_TIMER_ADD_TIME_BUTTON_VALUE', '"key_timer_add_time_button_value"')
]

for key, val in missing_keys:
    if key not in keys_content:
        keys_content = keys_content.replace('}', f'    public static final String {key} = {val};\n}}')

with open(keys_path, 'w') as f:
    f.write(keys_content)

defaults_path = 'app/src/main/java/com/best/deskclock/settings/PreferencesDefaultValues.java'
with open(defaults_path, 'r') as f:
    defaults_content = f.read()

missing_defaults = [
    ('DEFAULT_ALARM_DIGITAL_CLOCK_FONT_SIZE', '70', 'int'),
    ('DEFAULT_DISPLAY_ALARM_SECOND_HAND', 'true', 'boolean'),
    ('DEFAULT_TIMER_AUTO_SILENCE_DURATION', '30', 'int'),
    ('DEFAULT_TIMER_ADD_TIME_BUTTON_VALUE', '60', 'int'),
    ('DEFAULT_TIMER_AUTO_SILENCE', '"30"', 'String'),
    ('DEFAULT_TIME_TO_ADD_TO_TIMER', '"1"', 'String'),
    ('DEFAULT_ALARM_VOLUME_CRESCENDO_DURATION', '0', 'int'),
    ('DEFAULT_TIMER_VOLUME_CRESCENDO_DURATION', '0', 'int')
]

for key, val, type_name in missing_defaults:
    if key not in defaults_content:
        defaults_content = defaults_content.replace('}', f'    public static final {type_name} {key} = {val};\n}}')

with open(defaults_path, 'w') as f:
    f.write(defaults_content)
