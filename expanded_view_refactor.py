import sys

file_path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java'
with open(file_path, 'r') as f:
    content = f.read()

# Refactor toggleShiftPanel
old_toggle = """    private void toggleShiftPanel() {
        boolean isVisible = advancedShiftPanel.getVisibility() == View.VISIBLE;
        if (isVisible) {
             if (android.text.TextUtils.isEmpty(getItemHolder().item.rotationPayload)) repeatDays.setVisibility(VISIBLE);
        } else {
             repeatDays.setVisibility(GONE);
        }
        androidx.transition.TransitionManager.beginDelayedTransition((ViewGroup) itemView.getParent());
        advancedShiftPanel.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        shiftSetupCaret.animate().rotation(isVisible ? 0 : 180).setDuration(200).start();
    }"""

new_toggle = """    private void toggleShiftPanel() {
        final boolean isVisible = advancedShiftPanel.getVisibility() == View.VISIBLE;
        androidx.transition.TransitionManager.beginDelayedTransition((ViewGroup) itemView.getParent());
        if (isVisible) {
            advancedShiftPanel.setVisibility(View.GONE);
            shiftSetupCaret.animate().rotation(0).setDuration(200).start();
            if (android.text.TextUtils.isEmpty(getItemHolder().item.rotationPayload)) {
                repeatDays.setVisibility(View.VISIBLE);
            }
        } else {
            advancedShiftPanel.setVisibility(View.VISIBLE);
            shiftSetupCaret.animate().rotation(180).setDuration(200).start();
            repeatDays.setVisibility(View.GONE);
        }
    }"""

content = content.replace(old_toggle, new_toggle)

# Refactor bindShiftSetup
old_bind = """    private void bindShiftSetup(Context context, Alarm alarm) {
        shiftSetupActivator.setVisibility(VISIBLE);
        if (android.text.TextUtils.isEmpty(alarm.rotationPayload) || !alarm.rotationPayload.startsWith("SHIFT_ROTATION_V2")) {
            advancedShiftPanel.setVisibility(GONE);
            shiftSetupCaret.setRotation(0);
            cycleLengthSlider.setValue(7);
            cycleLengthValue.setText(context.getString(R.string.days_count, 7));
            anchorDateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            holidaySkipSwitch.setChecked(false);
            rebuildShiftGrid(7);
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

# Need to handle the double backslash in the split regex if the string was already escaped or not.
# In Python string, it's \\| which means literal \| for the regex.
content = content.replace(old_bind, new_bind)

with open(file_path, 'w') as f:
    f.write(content)
