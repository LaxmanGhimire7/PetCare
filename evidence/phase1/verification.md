# Phase 1 verification — September 19, 2026

## Changes

- Added the neutral Material 3 light and dark palettes, spacing and shape tokens, and Fraunces/Manrope text appearances.
- Bundled both variable fonts and their OFL licenses. Downloadable font resources are requested when Google Play services can provide them; bundled faces remain available offline.
- Added six fixed pet identity colours, a profile colour picker, and Room migration 8→9. Existing pet rows receive colour index `id % 6`.
- Replaced the timed in-app splash with AndroidX core splash routing, added predictive back support and dynamic chrome colour, and applied system bar and keyboard insets to the activity content.
- Corrected the dashboard's primary button contrast after visual inspection.

## Checks

- `:app:assembleDebug` — passed.
- `:app:testDebugUnitTest` — passed, 3 tests.
- `:app:lintDebug` — passed, 0 errors and 95 warnings. Warnings include pre-existing issues and new typography resource checks; warning reduction is scheduled for Phase 9.
- `:app:connectedDebugAndroidTest` — passed, 4 tests, including the new 8→9 migration test.
- Debug APK installed and launched on the Medium Phone API 37 emulator. No AndroidRuntime crash appeared in the launch log.
- Dashboard and pet form were visually checked in light and dark themes.

## Evidence

- `dashboard-light.png` and `dashboard-dark.png`
- `pet-form-light.png` and `pet-form-dark.png`
- `lint-results-debug.txt`
- `TEST-*.xml` and `android-tests.xml`

## Notes

- The older dashboard layout still has its former cards and navigation structure. Phase 2 replaces that structure.
- The repository had extensive uncommitted work before this phase. No existing changes were discarded.
