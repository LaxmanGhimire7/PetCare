# PetCare project status

Updated: 22 September 2026. The primary submission feature is **location and geotagging**; app integration and gestures/sensors are additional features.

## Implementation

The main feature work across phases 1–9 of [PETCARE_UPGRADE_SPEC.md](PETCARE_UPGRADE_SPEC.md) is implemented. The app has the Pit Lane light/dark design system, responsive five-tab navigation, time-ordered Today timeline, reduced-motion support, geotagged tasks and places, share/import/export integration, gestures and sensor reset, multiple local accounts, insights, widget, search, settings, onboarding, accessibility work, Room migrations through version 12, and repository/ViewModel screen state for the main lists.

Pets shows live daily progress, notable care and monthly spending. Pet Detail observes live care and expense data. Money adds and edits expenses in a bottom sheet. Places uses a draggable distance-sorted sheet over the map. See [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for the detailed inventory.

Release hygiene includes working backup rules, adaptive and monochrome launcher icons, ignored local secrets/databases, version 2.0.0, and environment-based release signing. The stray root `petcare.db` has been removed. Layout text, colours, and dimensions use resources. Lint suppressions and their reasons are documented in `app/lint.xml`.

## Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass; the lint report contains zero issues.
- The unit suite contains 23 passing tests.
- The emulator suite previously passed 16 Android tests, including the Espresso account-to-task journey, future-task visibility, clinic-share import, and migrations. The automated total is 39.
- `:app:assembleRelease` produced an unsigned APK without signing credentials. Setting all four `PETCARE_RELEASE_*` environment variables signs the release build.
- The v4 build was installed and launched on the Xiaomi Redmi Note 9 Pro Max (API 31) without clearing app data, and AndroidRuntime reported no crash. Physical review found and fixed the 12-hour time-column wrap.
- MIUI blocks ADB input injection. The phone disconnected before the final post-Places reinstall and component-gallery capture.

## Limits that require a configured device or service

- No Maps API key is stored in this checkout. The empty-key fallback works, but live tiles, clustering, and pin placement need a valid key for a visual end-to-end check.
- A real contact selection, biometric prompt, widget placement, and physical shake need a suitably configured device. Their entry points and data paths are implemented.
- API 24 runtime behaviour has not been checked on an API 24 device. Basic launch on Samsung API 36 passed, but full API 36 scenario coverage remains open.
- A distributable release APK requires a private signing key supplied by the app owner. The checked-in project intentionally contains no private key.
- Map imagery, geocoding, SMS, calendars, contacts, and booking depend on device services or external apps; core care records remain available offline.

## Strict specification items still open

- Phase 8 calls for MVVM with `StateFlow` and explicit Loading/Empty/Content/Error handling on every screen. Main lists and Pet Detail use this structure, while some forms and secondary detail screens still obtain repository data directly from Fragments.
- The 4.5:1 contrast target, full TalkBack walkthrough, and rotation coverage across every screen have not been independently measured. The 200% font-scale automated journey passes, but it does not prove the whole accessibility checklist.
- Full API 24 and API 36 acceptance, the live service flows above, and final private-key release signing remain external verification steps.

Screenshots by phase are no longer being collected, per the user's request.
