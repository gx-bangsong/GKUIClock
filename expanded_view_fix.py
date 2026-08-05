import sys

file_path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java'
with open(file_path, 'r') as f:
    content = f.read()

# Update saveRotationPayload to preserve overrides map
old_save = """    private void saveRotationPayload() {
        Alarm alarm = getItemHolder().item;
        int length = (int) cycleLengthSlider.getValue();
        boolean holidaySkip = holidaySkipSwitch.isChecked();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shiftGridContainer.getChildCount(); i++) {
            View node = shiftGridContainer.getChildAt(i);
            TextView input = node.findViewById(R.id.day_time_input);
            sb.append(parseTimeToMinutes(input.getText().toString()));
            if (i < shiftGridContainer.getChildCount() - 1) sb.append(",");
        }
        long anchorMs = 0;
        try {
            anchorMs = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(anchorDateButton.getText().toString()).getTime();
        } catch (Exception ignored) {}
        String payload = String.format(Locale.US, "SHIFT_ROTATION_V2|%d|%d|%b|%s|{}", length, anchorMs, holidaySkip, sb.toString());
        if (!payload.equals(alarm.rotationPayload)) {
            alarm.rotationPayload = payload;
            getAlarmTimeClickHandler().asyncUpdateAlarm(alarm, false);
            getItemHolder().notifyItemChanged();
        }
    }"""

new_save = """    private void saveRotationPayload() {
        Alarm alarm = getItemHolder().item;
        int length = (int) cycleLengthSlider.getValue();
        boolean holidaySkip = holidaySkipSwitch.isChecked();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shiftGridContainer.getChildCount(); i++) {
            View node = shiftGridContainer.getChildAt(i);
            TextView input = node.findViewById(R.id.day_time_input);
            sb.append(parseTimeToMinutes(input.getText().toString()));
            if (i < shiftGridContainer.getChildCount() - 1) sb.append(",");
        }
        long anchorMs = 0;
        try {
            anchorMs = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(anchorDateButton.getText().toString()).getTime();
        } catch (Exception ignored) {}

        String overrides = "{}";
        if (!android.text.TextUtils.isEmpty(alarm.rotationPayload) && alarm.rotationPayload.startsWith("SHIFT_ROTATION_V2")) {
            String[] existingParts = alarm.rotationPayload.split("\\\\|");
            if (existingParts.length >= 6) {
                overrides = existingParts[5];
            }
        }

        String payload = String.format(Locale.US, "SHIFT_ROTATION_V2|%d|%d|%b|%s|%s", length, anchorMs, holidaySkip, sb.toString(), overrides);
        if (!payload.equals(alarm.rotationPayload)) {
            alarm.rotationPayload = payload;
            getAlarmTimeClickHandler().asyncUpdateAlarm(alarm, false);
            getItemHolder().notifyItemChanged();
        }
    }"""

content = content.replace(old_save, new_save)

# Fix bindShiftSetup to correctly handle partial updates and avoid unnecessary grid re-inflation
# Actually, the grid re-inflation is okay for now, but we should make sure the slider doesn't trigger
# double updates during binding.

with open(file_path, 'w') as f:
    f.write(content)
