# PetCare

**Declared desirable feature for the submission: location and geotagging.** App integration and gestures/sensors are implemented as additional work.

PetCare is a native Android app for managing pets, care schedules, expenses, and useful places. It is written in Kotlin with XML layouts and Material 3. It supports Android API 24 and later, targets API 36, and compiles with API 37.

## What the app does

- **Today:** shows upcoming tasks including future appointments with their due dates, completed tasks, a completion ring, pet filters, reminders, routine generation, and undo for destructive actions. Save a pet profile before adding its first task.
- **Pets:** stores profiles, health details, caregiver contacts, and photos. Pet colours consistently identify their tasks and cards.
- **Money:** records and edits expenses, shows per-pet totals and Canvas charts, and exports CSV or PDF.
- **Places:** saves care locations, sorts by distance when location is available, and opens directions, calls, or booking links. With a Maps key, it also shows clustered markers, a pin picker, and map previews.
- **Integration:** imports clinic or pet care website messages shared as text, plus `.ics` calendar exports and attachments (`text/calendar`, `application/ics`, `text/x-vcalendar`). Every recognised field is editable before a task is saved. PetCare also exports care plans, sends caregiver checklists through SMS or the share sheet, inserts calendar events, and opens pet/task deep links. No vendor-specific account or live clinic API is required.
- **Gestures:** swipe right on a task to complete it, swipe left to delete it with Undo, and shake three times to reset today's completed checklist with confirmation and Undo. Dragging, double tap, long press, pull to refresh, and photo zoom are also available. Replay the guide from Settings.
- **Accounts and settings:** supports multiple local accounts with PBKDF2 password hashes and account-scoped records, optional biometric unlock, local JSON backup/restore, theme and reminder settings, and a home-screen widget.

The app stores data locally in Room. Its core care, expense, and place lists work offline. Maps tiles, geocoding, and external directions require their respective services.

## Build and run

Open the project in Android Studio with Android SDK 37 and JDK 17. To build from the project root on Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

For emulator tests, start an Android emulator and run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. `local.defaults.properties` supplies an empty Maps key, so a clean checkout builds without secrets and displays a usable **Map unavailable** state. To enable map tiles, add `MAPS_API_KEY=your_key` to ignored `local.properties` and enable the Maps SDK for Android for that key. Location and geocoding should also be tested on a device with working location services.

## Release signing

The release build reads signing details from these environment variables:

```text
PETCARE_RELEASE_STORE_FILE
PETCARE_RELEASE_STORE_PASSWORD
PETCARE_RELEASE_KEY_ALIAS
PETCARE_RELEASE_KEY_PASSWORD
```

After setting all four, run `.\gradlew.bat :app:assembleRelease`. Without them, Gradle produces an unsigned release APK. Keep the keystore and passwords outside Git. Version is `2.0.0` (`versionCode` 2).

## Privacy and data

PetCare requests location only for distance and maps, notifications for reminders, and uses a temporary system contact picker grant to choose a caregiver. SMS, calendar, maps, and document export use system activities. Auto Backup excludes sign-in and biometric preferences; the app also offers user-directed JSON backup and restore. Local accounts are device-only; there is no cloud sync or server account recovery.

## Tests and status

The automated suite covers recurrence including month-end clamping, parsing, password hashing, expenses, database migrations through version 12, account scoping, import/export, and an Espresso register-to-completion journey. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for current verification and limitations, [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for a feature inventory, and [PETCARE_UPGRADE_SPEC.md](PETCARE_UPGRADE_SPEC.md) for the original phase brief.
