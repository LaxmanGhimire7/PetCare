# Phase 4 verification — 19 September 2026

## Implemented

- Maps, fused location, clustering, and the Secrets Gradle plugin use an empty checked-in key default. The real key stays in ignored `local.properties`.
- Places screen: optional clustered map with five custom Canvas marker categories, marker detail sheet, directions, call, book, edit, delete with Undo, geocoded search, and vet search handoff.
- Location permission rationale and grant, denial, permanent denial/settings, services-off, and missing Play Services paths. Saved places remain available without maps.
- Long-press pin picker with API 33 asynchronous reverse geocoding and an IO fallback on older Android versions. Manual coordinate entry remains available.
- Room 9→10 adds nullable task coordinates and place ID. Existing task rows survive migration. Add/edit task forms link saved places; task rows and detail sheets open directions. Place and task sheets use lite map previews when maps are available.
- Places sort by distance when a device location is available and by name otherwise.

## Automated checks

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed: 5 tests, 0 failures.
- `:app:lintDebug` passed: 0 errors, 160 warnings. Warning cleanup is scheduled for Phase 9.
- `:app:connectedDebugAndroidTest` passed: 6 tests, 0 failures. Includes Room 9→10 migration and task geotag persistence.

## Emulator checks

- API 36 emulator, empty map key: Places shows the map unavailable card and stays usable; pin picker explains why it cannot open a map.
- Registered a local account, added Luna, saved a clinic with coordinates, opened its detail sheet, and reopened its edit form with its coordinates intact.
- Added a healthcare task linked to the clinic, confirmed its Directions chip, and reopened its detail sheet with Directions available.
- See screenshots in this directory. Device data is demonstration data only.

## Limitations to verify with a real key

- This workspace has no Maps API key. Live map tiles, marker clustering, long-press placement, and lite map imagery cannot be visually checked here. Code compiles and the no-key path is verified. Add `MAPS_API_KEY` to ignored `local.properties` to run those checks.
- The emulator did not provide a current or cached location during the smoke test, so distance ordering was covered by the pure distance unit test and the fallback list was observed; live ordering remains to be checked with a location fix.
