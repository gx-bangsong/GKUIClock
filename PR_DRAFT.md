# PR Title: Feature: Holiday Alarm Support (Legal Holidays & Custom Work Patterns)

## Description
This Pull Request introduces the "Holiday Alarm" feature, specifically designed to handle complex work schedules and legal holidays. It allows alarms to automatically skip holidays and stay enabled on weekend compensation workdays.

## Motivation
Standard recurring alarms (e.g., Monday-Friday) often fail in regions where legal holidays shift and weekends are sometimes used as compensation workdays. Users often have to manually enable/disable alarms every holiday season. This feature automates that process by syncing with a reliable holiday calendar.

## Key Features
- **Skip Holidays:** Automatically silence alarms on official legal holidays.
- **Workday Compensation:** Ensure alarms fire on weekend days that are marked as compensation workdays.
- **Custom Work Patterns:** Support for common specialized work schedules:
    - **Big/Small Week (大小周):** Support for alternating 6-day and 5-day work weeks.
    - **Single Day Off (单休):** Support for a fixed 6-day work week (Monday-Saturday).
- **Dynamic Data Sync:** Fetch latest holiday data from a user-configurable URL (JSON format).
- **Per-Alarm Settings:** Each alarm can have its own specific holiday/workday logic assigned.

## Technical Changes
- **Holiday Data Management:**
    - Created a new `holiday` package containing Room database entities (`Holiday`), DAO (`HolidayDao`), and a repository (`HolidayRepository`) for managing holiday/workday data.
    - Added background synchronization logic to fetch holiday data from a remote URL.
- **Provider & Model Updates:**
    - Expanded `Alarm` model to include `holidayOption` for per-alarm configuration.
    - Integrated holiday data URL and update preferences into `SettingsDAO`.
- **UI Improvements:**
    - Added a "Holiday" setting entry to the expanded alarm view.
    - Created `HolidayDialogFragment` for choosing between different work patterns and holiday skipping.
    - Added holiday data settings (update URL, manual sync trigger) in the Alarm Settings screen.
- **Scheduling Logic:**
    - Integrated holiday/workday checks into the alarm instance creation and scheduling process.

## Contribution Guidelines Compliance
- **Focused PR:** This PR is strictly focused on adding holiday support.
- **Performance:** Database queries for holidays are optimized using Room and executed off the main thread where appropriate.
- **Privacy:** Holiday data is fetched from a public URL; no user data is uploaded.
- **No Proprietary Code:** All code is original or uses compatible open-source libraries.
- **Localization:** Includes updated string resources for English and Chinese.
