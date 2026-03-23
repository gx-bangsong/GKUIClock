import sys

path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java'
with open(path, 'r') as f:
    content = f.read()

# Add missing initialization in constructor
if 'holidayOption = itemView.findViewById(R.id.holiday_option);' not in content:
    content = content.replace('duplicate = itemView.findViewById(R.id.duplicate);',
                              'duplicate = itemView.findViewById(R.id.duplicate);\n        holidayOption = itemView.findViewById(R.id.holiday_option);')

# Fix bindHolidayOption
if 'private void bindHolidayOption' not in content:
    bind_holiday_method = '''
    private void bindHolidayOption(Context context, Alarm alarm) {
        holidayOption.setVisibility(VISIBLE);
        holidayOption.setTypeface(mGeneralTypeface);
        switch (alarm.holidayOption) {
            case com.best.deskclock.holiday.HolidayUtils.HOLIDAY_OPTION_SKIP_HOLIDAY ->
                    holidayOption.setText(context.getString(R.string.holiday_option_skip_holiday));
            case com.best.deskclock.holiday.HolidayUtils.HOLIDAY_OPTION_BIG_SMALL_DA ->
                    holidayOption.setText(context.getString(R.string.holiday_option_big_small_da));
            case com.best.deskclock.holiday.HolidayUtils.HOLIDAY_OPTION_BIG_SMALL_XIAO ->
                    holidayOption.setText(context.getString(R.string.holiday_option_big_small_xiao));
            case com.best.deskclock.holiday.HolidayUtils.HOLIDAY_OPTION_SINGLE_DAY_OFF ->
                    holidayOption.setText(context.getString(R.string.holiday_option_single_day_off));
            default -> holidayOption.setText(context.getString(R.string.holiday_option_none));
        }
    }
'''
    # Insert before onAnimateChange
    content = content.replace('    @Override\n    public Animator onAnimateChange', bind_holiday_method + '\n    @Override\n    public Animator onAnimateChange')

# Call bindHolidayOption in onBindItemView
if 'bindHolidayOption(context, alarm);' not in content:
    content = content.replace('bindDeleteAndDuplicateButtons();', 'bindDeleteAndDuplicateButtons();\n        bindHolidayOption(context, alarm);')

# Add click listener for holiday option in constructor
if 'holidayOption.setOnClickListener' not in content:
     content = content.replace('duplicate.setOnClickListener(v -> {',
                               '''holidayOption.setOnClickListener(v ->
                getAlarmTimeClickHandler().onHolidayOptionClicked(getItemHolder().item));

        duplicate.setOnClickListener(v -> {''')

with open(path, 'w') as f:
    f.write(content)
