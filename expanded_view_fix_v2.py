import sys

file_path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java'
with open(file_path, 'r') as f:
    content = f.read()

# Fix bindShiftSetup to correctly handle partial updates and avoid unnecessary grid re-inflation
old_bind = """    private void bindShiftSetup(Context context, Alarm alarm) {
        shiftSetupActivator.setVisibility(VISIBLE);
        final boolean hasRotation = !android.text.TextUtils.isEmpty(alarm.rotationPayload)
                && alarm.rotationPayload.startsWith("SHIFT_ROTATION_V2");

        if (hasRotation || advancedShiftPanel.getVisibility() == VISIBLE) {
            repeatDays.setVisibility(GONE);
        } else {
            repeatDays.setVisibility(VISIBLE);
        }

        if (!hasRotation) {
            if (advancedShiftPanel.getVisibility() != VISIBLE) {
                advancedShiftPanel.setVisibility(GONE);
                shiftSetupCaret.setRotation(0);
            }
            cycleLengthSlider.setValue(7);
            cycleLengthValue.setText(context.getString(R.string.days_count, 7));
            if (android.text.TextUtils.isEmpty(anchorDateButton.getText())) {
                anchorDateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            }
            holidaySkipSwitch.setChecked(false);
            if (shiftGridContainer.getChildCount() == 0) rebuildShiftGrid(7);
            return;
        }
        String[] parts = alarm.rotationPayload.split("\\\\|");"""

new_bind = """    private void bindShiftSetup(Context context, Alarm alarm) {
        shiftSetupActivator.setVisibility(VISIBLE);
        final boolean hasRotation = !android.text.TextUtils.isEmpty(alarm.rotationPayload)
                && alarm.rotationPayload.startsWith("SHIFT_ROTATION_V2");

        if (hasRotation || advancedShiftPanel.getVisibility() == VISIBLE) {
            repeatDays.setVisibility(GONE);
        } else {
            repeatDays.setVisibility(VISIBLE);
        }

        if (!hasRotation) {
            if (advancedShiftPanel.getVisibility() != VISIBLE) {
                advancedShiftPanel.setVisibility(GONE);
                shiftSetupCaret.setRotation(0);
            }
            cycleLengthSlider.setValue(7);
            cycleLengthValue.setText(context.getString(R.string.days_count, 7));
            if (android.text.TextUtils.isEmpty(anchorDateButton.getText())) {
                anchorDateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            }
            holidaySkipSwitch.setChecked(false);
            if (shiftGridContainer.getChildCount() == 0) rebuildShiftGrid(7);
            return;
        }
        String[] parts = alarm.rotationPayload.split("\\\\|");"""

# Let's refine the logic inside bindShiftSetup if rotation exists
old_logic_start = """        try {
            int length = Integer.parseInt(parts[1]);
            long anchorMs = Long.parseLong(parts[2]);
            boolean holidaySkip = Boolean.parseBoolean(parts[3]);
            String[] minutes = parts[4].split(",");
            cycleLengthSlider.setValue(length);
            cycleLengthValue.setText(context.getString(R.string.days_count, length));
            anchorDateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(anchorMs)));
            holidaySkipSwitch.setChecked(holidaySkip);
            shiftGridContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context);
            for (int i = 0; i < length; i++) {"""

new_logic_start = """        try {
            int length = Integer.parseInt(parts[1]);
            long anchorMs = Long.parseLong(parts[2]);
            boolean holidaySkip = Boolean.parseBoolean(parts[3]);
            String[] minutes = parts[4].split(",");
            if (cycleLengthSlider.getValue() != length) {
                cycleLengthSlider.setValue(length);
            }
            cycleLengthValue.setText(context.getString(R.string.days_count, length));
            anchorDateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(anchorMs)));
            if (holidaySkipSwitch.isChecked() != holidaySkip) {
                holidaySkipSwitch.setChecked(holidaySkip);
            }
            shiftGridContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context);
            for (int i = 0; i < length; i++) {"""

content = content.replace(old_logic_start, new_logic_start)

with open(file_path, 'w') as f:
    f.write(content)
