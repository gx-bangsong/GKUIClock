import sys

file_path = 'app/src/main/java/com/best/deskclock/alarms/dataadapter/AlarmItemViewHolder.java'
with open(file_path, 'r') as f:
    content = f.read()

old_calc = """                long nextAlertTimeMs = next.getTimeInMillis();
                int upcomingDayIndex = (int) ((nextAlertTimeMs - anchorMs) / (1000 * 60 * 60 * 24)) % cycleLength + 1;"""

new_calc = """                long nextAlertTimeMs = next.getTimeInMillis();
                long dayMs = 1000 * 60 * 60 * 24;
                long targetLocal = nextAlertTimeMs + next.getTimeZone().getOffset(nextAlertTimeMs);
                Calendar anchor = Calendar.getInstance(next.getTimeZone());
                anchor.setTimeInMillis(anchorMs);
                long anchorLocal = anchorMs + anchor.getTimeZone().getOffset(anchorMs);
                int diffDays = (int) (targetLocal / dayMs) - (int) (anchorLocal / dayMs);
                int cycleIndex = diffDays % cycleLength;
                if (cycleIndex < 0) cycleIndex += cycleLength;
                int upcomingDayIndex = cycleIndex + 1;"""

content = content.replace(old_calc, new_calc)

with open(file_path, 'w') as f:
    f.write(content)
