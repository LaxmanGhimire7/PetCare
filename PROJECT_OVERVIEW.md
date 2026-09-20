# PetCare project overview

Updated: 21 September 2026. This records what is implemented and verified in the current workspace. The planned work is in [PETCARE_UPGRADE_SPEC.md](PETCARE_UPGRADE_SPEC.md).

## Project at a glance

- Native Android app: Kotlin, XML layouts, Material 3, Navigation, View Binding, Coroutines, WorkManager, Coil, Room, and SharedPreferences.
- Android versions: minSdk 24, targetSdk 36, compileSdk 37.
- Room schema is version 12. Existing migrations remain in place; 8→9 adds pet colours, 9→10 adds task geotags, 10→11 adds task ordering and recurrence origin, and 11→12 adds account ownership.
- The project builds and its unit tests, emulator tests, and lint task pass. The release cleanup brought the current lint report to 0 errors and 0 warnings.
- The app works with an empty Maps API key by showing a fallback and keeping saved places accessible.

## Original working features

1. Splash routing to login or dashboard.
2. Local registration, login, signed-in state, and logout; multiple accounts are now supported with account-scoped data.
3. Pet profiles with name, species, breed, age, weight, health and care notes, and multiple saved photos; create, edit, and delete.
4. Care tasks with a pet, category, due date, reminder time, repeat frequency, supplies, and instructions; create, edit, complete, and delete.
5. Completion history and automatically generated next occurrences for daily, weekly, and calendar-month tasks.
6. Routine generator for common feeding, exercise, grooming, medication, and healthcare tasks.
7. WorkManager reminders and configurable default reminder time.
8. SMS checklist sharing and task gestures: swipe right to complete, swipe left to delete, double tap to edit, and long press to share.
9. Expense entry, editing, history, totals, insights, CSV/PDF export, and undoable deletion.
10. Saved care places with address, optional coordinates, hours, phone, booking URL, and directions handoff.
11. `.ics` appointment import that pre-fills a healthcare task for review.

## Upgrade progress

### Phase 1 — Design system and theming: complete

- Added quiet light and dark Material 3 chrome, Fraunces display type, Manrope UI type, six stable pet identity colours, shape and spacing resources, Android splash, predictive back, dynamic chrome colour, and edge-to-edge handling.
- Added pet `colorIndex` and Room 8→9 migration with a migration test.
- Verified debug build, unit tests, lint, and connected tests.

### Phase 2 — Navigation and dashboard: complete

- Added Today, Pets, Money, Places, and Settings bottom navigation, with a navigation rail on wider layouts.
- Rebuilt Today with a Canvas completion ring, pet filters, task rails, overdue state, completed section, loading skeleton, and Add task FAB.
- Added list and empty states for the other tabs and Undo snackbars for pet, task, expense, and place deletion.
- Verified build, tests, lint, and emulator layouts in light, dark, and landscape.

### Phase 3 — Motion: complete

- Added pet card transition, fade-through tab navigation, shared-axis forms, task completion response, swipe reveal and threshold haptics, ring and expense-total updates, row collapse/Undo motion, and a draggable task detail sheet.
- Added `MotionPrefs` so nonessential animation respects the system animator scale.
- Verified build, tests, lint, and emulator interactions.

### Phase 4 — Location and geotagging: implemented; live Maps imagery awaits a key

- Added Maps, fused location, Maps Utils clustering, Secrets Gradle configuration, and a checked-in empty key default. A real key belongs in ignored `local.properties`.
- Places screen has an optional clustered map with category-specific custom marker icons, search by platform geocoder, vet search through a `geo:` intent, distance sorting, and a bottom sheet with directions, call, book, edit, and delete with Undo.
- Handles location permission rationale, denial, permanent denial/settings, location services off, missing Play Services, and empty API key while keeping the places list usable.
- Added long-press pin picker and reverse geocoding, with the API 33 asynchronous path and an IO path on older Android versions. Place forms still accept coordinates manually.
- Added task `latitude`, `longitude`, and `placeId` through Room 9→10; task add/edit forms link a saved place, task rows show Directions, and place/task detail sheets include lite map previews when Maps is configured.
- Verified build, 5 unit tests, lint with 0 errors, and 6 connected emulator tests including migration and geotag persistence. The empty-key UI and task/place flows were smoke tested. Live tiles, marker clustering, and pin placement still need visual verification with a valid key.

### Phase 5 — App integration: complete

- Added a dedicated import review activity for shared plain text, shared calendar files, and opened `.ics` files. It extracts dates, times, and clinic names from text, reviews every event in a multi-event calendar file, and keeps the fields editable before task creation.
- Added a contact picker, per-pet caregiver details, an editable delegation checklist preview, SMS composer handoff, and a system share-sheet option without contact or SMS permissions.
- Added device calendar insertion through `ACTION_INSERT`, care-plan export to `.ics` or `.txt` through the Storage Access Framework, and `petcare://pet/{id}` / `petcare://task/{id}` deep links.
- Verified an emailed vaccination message imports into a pre-filled task, SMS composer launch, `.ics` export and re-import, task deep-link opening, and calendar handoff on the emulator. Build, 10 unit tests, 6 connected tests, and lint passed at the phase gate.

### Phase 6 — Gestures and sensors: complete

- Added shake detection using a gravity filter, three acceleration peaks within one second, scroll suppression, a two-second debounce, lifecycle-bound accelerometer registration, reset confirmation, and Undo. A visible Reset button is the fallback when an accelerometer is unavailable.
- Added long-press drag handles with persistent task ordering through Room 10→11. A recurrence-origin column prevents duplicate future tasks if a reset task is completed again.
- Added pull-to-refresh, a zoomable pet photo gallery with double tap and downward dismissal, pet-to-pet ViewPager2 paging, haptics for gestures and sheet state changes, and a first-run gesture guide replayable from Settings.
- Verified ordering persists after relaunch, reset and Undo, refresh feedback, photo zoom/dismissal, pet paging, guide replay, and accelerometer unregistering after leaving Today. Build, 12 unit tests, 9 connected tests, and lint passed at the phase gate. Phase screenshots were stopped at the user's request.

### Phase 7 — Functional depth: implemented

- Added local multi-account login with PBKDF2 password hashes, account-scoped Room queries, optional persistent sessions, a legacy-account upgrade path, and biometric unlock with device credential fallback.
- Added reminder notification actions, task deep links, and a home-screen widget for today's remaining care.
- Added Canvas expense insights, per-pet and month comparisons, expense editing, CSV/PDF export, cross-record search and filters, and full care-data JSON backup/restore through the Storage Access Framework.
- Corrected monthly recurrence to calendar months, expanded Settings, added undoable clear-data, and added a skippable three-step onboarding flow.
- Fixed selector interaction on expense, task, and place forms; added accessibility labels to those fields; and made the expense chart grow with larger text.
- Verified expense creation and edit prefill on the emulator. The debug build, 20 unit tests, 13 connected tests, and lint passed at the phase gate. No phase screenshots were captured at the user's request.

### Phase 8 — Architecture, comments, and tests: partially complete

- Added repository-provided data access, `StateFlow` ViewModels and explicit Loading/Empty/Content/Error states to Today, Pets, Money, Places, and Search. Some forms and detail screens still use repositories from Fragments and do not have all four screen states, so the spec's "MVVM throughout" requirement remains open.
- Added KDoc to classes, reasoning comments for non-obvious logic, and a purpose comment to each layout. Kept explicit migration tests 8→9 through 11→12, account-scoping tests, backup tests, and an Espresso registration-to-completion journey.
- Verified debug build, 20 unit tests, 14 connected tests, and lint at the phase gate.

### Phase 9 — Release hygiene: implemented

- Replaced template backup rules with credential and biometric exclusions, removed the tracked root database, and expanded Git ignores for databases, secrets, build output, IDE state, and keystores.
- Added adaptive, legacy, and monochrome launcher icons; set version 2.0.0; and configured owner-supplied release signing through environment variables. An unsigned release APK builds without credentials.
- Removed unused resources and layout literals. The final lint report contains 0 errors and 0 warnings. `app/lint.xml` records the reasons for scoped suppressions.
- Added this overview, a submission-focused README, and an updated status file. No new phase screenshots were collected at the user's request.
- Verified all 14 emulator tests at normal and 200% font scale. At large text sizes, Today moves Add task into the scrollable content so the floating button cannot cover a task control.

## Current known limitations

- No valid Maps API key is present in this workspace; the live map path has not been visually checked.
- The emulator did not return a device location during the Phase 4 smoke test. Distance arithmetic has unit tests, and the no-location fallback was observed.
- Live biometrics and widget placement need a compatible physical or configured device for a full manual check; automated tests cover their data paths.
- Release signing needs the owner's private keystore and four `PETCARE_RELEASE_*` environment variables. Without these, the APK is unsigned.
- API 24 and API 36 runtime testing remains open because only an API 37 emulator image is installed in this workspace.
- Phase 5 contact selection could not be exercised on the emulator because it has no contacts with phone numbers. The system picker opens, and the SMS composer receives the checklist; selecting a real contact still needs a populated device.
- The shake threshold and debounce passed unit tests and the accelerometer lifecycle was checked on the emulator. Its virtual sensor could not be driven through this emulator's console, so a physical shake still needs a device check.

[PROJECT_STATUS.md](PROJECT_STATUS.md) records current verification and release limitations.
