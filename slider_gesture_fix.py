import sys

file_path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java'
with open(file_path, 'r') as f:
    content = f.read()

old_code = '        cycleLengthSlider.addOnChangeListener((slider, value, fromUser) -> {'
new_code = '''        cycleLengthSlider.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        cycleLengthSlider.addOnChangeListener((slider, value, fromUser) -> {'''

content = content.replace(old_code, new_code)

with open(file_path, 'w') as f:
    f.write(content)
