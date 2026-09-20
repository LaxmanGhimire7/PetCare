# PetCare project overview

Updated: 20 September 2026. This records what is implemented and verified in the current workspace. The planned work is in [PETCARE_UPGRADE_SPEC.md](PETCARE_UPGRADE_SPEC.md).

## Project at a glance

- Native Android app: Kotlin, XML layouts, Material 3, Navigation, View Binding, Coroutines, WorkManager, Coil, Room, and SharedPreferences.
- Android versions: minSdk 24, targetSdk 36, compileSdk 37.
- Room schema is version 10. Existing migrations 1→9 remain in place; 9→10 adds optional task geotags.
- The project currently builds and its unit tests, emulator tests, and lint task pass. Lint still reports warnings; release cleanup is planned for Phase 9.
- The app works with an empty Maps API key by showing a fallback and keeping saved places accessible.

## Original working features

1. Splash routing to login or dashboard.
2. Local registration, login, signed-in state, and logout. The current account model still supports one local account per installation.
3. Pet profiles with name, species, breed, age, weight, health and care notes, and multiple saved photos; create, edit, and delete.
4. Care tasks with a pet, category, due date, reminder time, repeat frequency, supplies, and instructions; create, edit, complete, and delete.
5. Completion history and automatically generated next occurrences for daily, weekly, and monthly tasks. Monthly recurrence currently uses 30 days; Phase 7 will correct calendar-month behavior.
6. Routine generator for common feeding, exercise, grooming, medication, and healthcare tasks.
7. WorkManager reminders and configurable default reminder time.
8. SMS checklist sharing and task gestures: swipe right to complete, swipe left to delete, double tap to edit, and long press to share.
9. Expense entry, history, totals, and deletion. Expense editing and insights remain planned.
10. Saved care places with address, optional coordinates, hours, phone, booking URL, and directions handoff.
11. `.ics` appointment import that pre-fills a healthcare task for review.

## Upgrade progress

### Phase 1 — Design system and theming: complete

- Added quiet light and dark Material 3 chrome, Fraunces display type, Manrope UI type, six stable pet identity colours, shape and spacing resources, Android splash, predictive back, dynamic chrome colour, and edge-to-edge handling.
- Added pet `colorIndex` and Room 8→9 migration with a migration test.
- Verified debug build, unit tests, lint, and connected tests. Evidence: [evidence/phase1](evidence/phase1).

### Phase 2 — Navigation and dashboard: complete

- Added Today, Pets, Money, Places, and Settings bottom navigation, with a navigation rail on wider layouts.
- Rebuilt Today with a Canvas completion ring, pet filters, task rails, overdue state, completed section, loading skeleton, and Add task FAB.
- Added list and empty states for the other tabs and Undo snackbars for pet, task, expense, and place deletion.
- Verified build, tests, lint, and emulator layouts in light, dark, and landscape. Evidence: [evidence/phase2](evidence/phase2).

### Phase 3 — Motion: complete

- Added pet card transition, fade-through tab navigation, shared-axis forms, task completion response, swipe reveal and threshold haptics, ring and expense-total updates, row collapse/Undo motion, and a draggable task detail sheet.
- Added `MotionPrefs` so nonessential animation respects the system animator scale.
- Verified build, tests, lint, and emulator interactions. Evidence: [evidence/phase3](evidence/phase3).

### Phase 4 — Location and geotagging: implemented; live Maps imagery awaits a key

- Added Maps, fused location, Maps Utils clustering, Secrets Gradle configuration, and a checked-in empty key default. A real key belongs in ignored `local.properties`.
- Places screen has an optional clustered map with category-specific custom marker icons, search by platform geocoder, vet search through a `geo:` intent, distance sorting, and a bottom sheet with directions, call, book, edit, and delete with Undo.
- Handles location permission rationale, denial, permanent denial/settings, location services off, missing Play Services, and empty API key while keeping the places list usable.
- Added long-press pin picker and reverse geocoding, with the API 33 asynchronous path and an IO path on older Android versions. Place forms still accept coordinates manually.
- Added task `latitude`, `longitude`, and `placeId` through Room 9→10; task add/edit forms link a saved place, task rows show Directions, and place/task detail sheets include lite map previews when Maps is configured.
- Verified build, 5 unit tests, lint with 0 errors, and 6 connected emulator tests including migration and geotag persistence. The empty-key UI and task/place flows were smoke tested. Live tiles, marker clustering, and pin placement still need visual verification with a valid key. Evidence: [evidence/phase4](evidence/phase4).

### Phase 5 — App integration: complete

- Added a dedicated import review activity for shared plain text, shared calendar files, and opened `.ics` files. It extracts dates, times, and clinic names from text, reviews every event in a multi-event calendar file, and keeps the fields editable before task creation.
- Added a contact picker, per-pet caregiver details, an editable delegation checklist preview, SMS composer handoff, and a system share-sheet option without contact or SMS permissions.
- Added device calendar insertion through `ACTION_INSERT`, care-plan export to `.ics` or `.txt` through the Storage Access Framework, and `petcare://pet/{id}` / `petcare://task/{id}` deep links.
- Verified an emailed vaccination message imports into a pre-filled task, SMS composer launch, `.ics` export and re-import, task deep-link opening, and calendar handoff on the emulator. Build, 10 unit tests, 6 connected tests, and lint pass with 0 errors and 163 warnings. Evidence: [evidence/phase5](evidence/phase5).

## Remaining phases

- **Phase 6:** shake-to-reset with Undo, drag ordering, pull to refresh, photo zoom, pet paging, gesture haptics, and coach marks.
- **Phase 7:** multiple secure accounts, biometrics, notification actions, widget, expense charts and exports, backup/restore, search, expense editing, calendar-month recurrence, Settings and onboarding, and accessibility.
- **Phase 8:** MVVM/StateFlow/repository consistency, comments, migration and UI tests, and 25 or more automated tests.
- **Phase 9:** backup rules, Git hygiene, lint warning reduction, launcher icon, release setup, documentation, and logical commits.

## Current known limitations

- No valid Maps API key is present in this workspace; the live map path has not been visually checked.
- The emulator did not return a device location during the Phase 4 smoke test. Distance arithmetic has unit tests, and the no-location fallback was observed.
- The original single-account model, expense edit gap, 30-day monthly recurrence, template backup rules, and broad test-coverage target remain for later phases.
- `petcare.db` is currently tracked in the repository root and should be removed from version control during Phase 9. It must not appear in a release commit.
- Phase 5 contact selection could not be exercised on the emulator because it has no contacts with phone numbers. The system picker opens, and the SMS composer receives the checklist; selecting a real contact still needs a populated device.

For the phase-by-phase evidence and test reports, see the linked `evidence/phase*` directories. [PROJECT_STATUS.md](PROJECT_STATUS.md) is the older baseline status and will be reconciled in the documentation phase.
