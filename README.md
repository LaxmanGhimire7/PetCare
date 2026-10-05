# PetCare

**Declared desirable feature for the submission: location and geotagging.** App integration and gestures/sensors are implemented as additional work.

PetCare is a native Android app for managing pets, care schedules, expenses, and useful places. It is written in Kotlin with XML layouts and Material 3. It supports Android API 24 and later, targets API 36, and compiles with API 37.

## What the app does

- **Today:** shows the selected day as a strict time-ordered timeline. A live countdown and Now marker keep the next task clear; completed tasks stay in place, pet rings and progress segments update immediately, and destructive actions remain undoable.
- **Pets:** stores profiles, health details, caregiver contacts, and photos. Pet colours consistently identify their tasks and cards.
- **Money:** records and edits expenses, shows per-pet totals and Canvas charts, and exports CSV or PDF.
- **Places:** saves care locations, sorts by distance when location is available, and opens directions, calls, or booking links. MapLibre displays OpenStreetMap raster tiles, clustered markers, a pin picker, and map previews without a Google Maps key.
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

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The map uses MapLibre and OpenStreetMap tiles; it needs no Google Maps key, billing account, or `MAPS_API_KEY` setting. For Google Sign-In, set `GOOGLE_WEB_CLIENT_ID` in ignored `local.properties`. Location and geocoding should also be tested on a device with working location services. The public OpenStreetMap tile endpoint is intended here only for low-volume development or college demonstration use; use a suitable tile provider or self-hosted tiles for production.

Places text search uses the public OpenStreetMap Nominatim service only when **Search** is tapped. It sends the typed query to that service, so do not enter private information. Requests have an app-identifying User-Agent, a one-request-per-second device limit, and a small result cache; the visible map biases ambiguous names toward nearby results. A successful search zooms in and leaves a large, labelled destination pin on the map, including when a nearby-category chip is selected. The keyboard and place sheet collapse so the pin is easier to see. Long-press **Search** to switch to the device's address lookup without updating the app. This public service is only for this low-volume college demonstration, not production or autocomplete.

Tapping **All**, **Vets**, **Grooming**, or **Parks** also looks up nearby public OpenStreetMap places through Overpass, centred on the visible map or your location. If the map and GPS are still starting, that tap waits for the first usable location; if location is disabled, move the map to an area and tap again. It does not add results to your saved places. Each category is queried separately for OSM nodes and ways; relation-only features may not appear. The lookup runs only after a tap, has a two-second device request limit, a ten-minute success cache, and a one-minute pause after service failures. If the main FOSSGIS server fails, the app briefly uses its documented second server as a fallback. Public Overpass can still be busy or unavailable even when map tiles and text search work; this feature is intended only for the low-volume college demonstration. Public OSM/Overpass services should be replaced with a suitable hosted or self-hosted service before wider distribution.

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
