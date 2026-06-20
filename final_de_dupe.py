import os
import re

def de_dupe_file(path, pattern, replacement=None):
    if not os.path.exists(path):
        print(f"Skipping {path}, does not exist.")
        return
    with open(path, 'r') as f:
        lines = f.readlines()

    new_lines = []
    seen = set()

    # Simple line-based de-dupe for common patterns
    for line in lines:
        stripped = line.strip()
        if pattern in stripped:
            if stripped in seen:
                continue
            seen.add(stripped)
        new_lines.append(line)

    with open(path, 'w') as f:
        f.writelines(new_lines)

def de_dupe_methods(path, method_sig):
    with open(path, 'r') as f:
        content = f.read()

    # Find all occurrences of the method signature
    # This is a bit rough but works if methods are separated by braces
    matches = list(re.finditer(re.escape(method_sig), content))
    if len(matches) <= 1:
        return

    # Keep the first one.
    # To find the end of the first method, we count braces.
    first_match_start = matches[0].start()

    # We want to remove all subsequent matches.
    # It's easier to just remove the specific strings for now if they are duplicated.
    # But methods have bodies.

    # Let's try a different way: split by the signature and take 0 and 1
    parts = content.split(method_sig)
    # parts[0] is everything before first
    # parts[1] is everything between first and second
    # ...
    # This is also risky.

    # Safest: if we know the methods are at the end, find the first and cut.
    # But some might be in the middle.

    print(f"Duplicated method {method_sig} in {path}")

# 1. ClockContract.java
de_dupe_file('app/src/main/java/com/best/deskclock/provider/ClockContract.java', 'String ROTATION_PAYLOAD = "rotation_payload";')

# 2. AlarmInstance.java
de_dupe_file('app/src/main/java/com/best/deskclock/provider/AlarmInstance.java', 'public String mRotationPayload;')
de_dupe_file('app/src/main/java/com/best/deskclock/provider/AlarmInstance.java', 'mRotationPayload = null;')
de_dupe_file('app/src/main/java/com/best/deskclock/provider/AlarmInstance.java', 'this.mRotationPayload = instance.mRotationPayload;')

# 3. AlarmTimeClickHandler.java
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/AlarmTimeClickHandler.java', 'private boolean mIsAnchorDateMode = false;')
# Methods are harder. Let's use a surgical regex to remove duplicate method bodies.
with open('app/src/main/java/com/best/deskclock/alarms/AlarmTimeClickHandler.java', 'r') as f:
    c = f.read()
# Remove duplicated onAnchorDateClicked
c = re.sub(r'(public void onAnchorDateClicked\(Alarm alarm\) \{.*?mIsAnchorDateMode = false;.*?return;.*?\}).*?public void onAnchorDateClicked', r'\1\n\n    public void onAnchorDateClicked', c, flags=re.DOTALL)
# Actually, the above regex is complex. Let's just do multiple splits and rejoin.

def fix_methods(content, sig):
    parts = content.split(sig)
    if len(parts) > 2:
        # Keep prefix, the first method body (approx), and the rest of the file
        # This is too hard to get right generally.
        # Let's just find and replace the second occurrence of the block.
        pass
    return content

# 4. ExpandedAlarmViewHolder.java
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final View shiftSetupActivator;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final ImageView shiftSetupCaret;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final View advancedShiftPanel;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final Slider cycleLengthSlider;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final TextView cycleLengthValue;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final com.google.android.material.button.MaterialButton anchorDateButton;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final MaterialSwitch holidaySkipSwitch;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'private final ChipGroup shiftGridContainer;')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'shiftSetupActivator = itemView.findViewById(R.id.shift_setup_activator);')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'shiftSetupActivator.setOnClickListener(v -> toggleShiftPanel());')
de_dupe_file('app/src/main/java/com/best/deskclock/alarms/dataadapter/ExpandedAlarmViewHolder.java', 'shiftSetupActivator.setAlpha(1f);')
