# PetCare Project Status

Last updated: September 18, 2026

## Completed foundation

- Android single-module project configured under the `app` module.
- `MainActivity` is the launcher activity and hosts the application navigation container.
- Material 3, AppCompat, ConstraintLayout, and AndroidX Core dependencies are configured.
- View Binding is enabled for layouts.
- A Material 3 PetCare color palette and light theme are defined.
- The activity layout uses a `NavHostFragment` as the full-screen application container.
- A navigation graph has been created with these destinations:
  - Splash
  - Login
  - Register
  - Home Dashboard
- Navigation actions exist for Splash-to-Login, Splash-to-Home, Login-to-Register, Login-to-Home, Register-to-Login, Register-to-Home, and Home-to-Login.
- Fragment classes exist for Splash, Login, Register, and Home Dashboard.
- Each fragment uses View Binding and correctly clears its binding in `onDestroyView`.
- Layout files exist for Splash, Login, Register, and Home Dashboard.

## Completed user flow

- The app opens on the Splash screen.
- Splash waits 1.5 seconds and then opens Login.
- The splash navigation coroutine is tied to the fragment view lifecycle, so it is cancelled when the view is destroyed.
- Splash checks that it is still the active destination before navigating, preventing duplicate navigation.
- Login has email and password fields, password visibility control, input autofill hints, and validation for a valid email and a six-character minimum password.
- Register has full-name, email, password, and password-confirmation fields with validation and clear field-level errors.
- Login can open Register, and Register can return to Login.
- Register saves one device-local account and signs the user in.
- Login verifies the saved email and password before opening the Home Dashboard.
- The signed-in state persists between launches, and Splash opens Dashboard for a signed-in user or Login otherwise.
- This is local-only authentication: it does not create a server-backed account, synchronize between devices, or provide production-grade credential protection.
- Dashboard greets the signed-in user, shows clear empty states for pets and upcoming care, and provides a working Sign out action.
- Pet profiles can be added and edited with a name, animal type, optional health notes, and an optional on-device photo. They are stored locally with Room.
- Dashboard lists saved pet profiles, including their health notes and photo when supplied, and lets the owner edit or remove a profile after confirmation.
- Care tasks can be created for a saved pet with a due date. They are stored locally, sorted by due date, and shown on Dashboard.
- Upcoming care tasks can be marked done. Completed tasks are retained locally and no longer appear in the upcoming list.
- Dashboard includes a completed-care history, ordered by most recently scheduled date.
- Care tasks can be deleted from the upcoming or completed list after confirmation.
- Care tasks can be edited to change their title, pet, or due date while preserving their completion state.
- Each unfinished care task has one local reminder on its due date. The owner chooses the time when adding or editing a task; the default is 9:00 AM. Creating or editing a task replaces its reminder; completing, deleting, or removing its pet cancels it.

## Implemented dependencies

- Room and KSP persist pet profiles and care tasks.
- Navigation, Lifecycle, Coroutines, WorkManager, and Coil support the implemented app flows.

## Build verification

- `assembleDebug` completed successfully after using Android Studio's bundled JDK.
- The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Remaining work

- Add global reminder preferences or multiple reminders per care task.
- Decide which prepared integrations are required and implement them only when needed.
- Complete the dark theme; it currently does not define the PetCare color overrides.
- Replace hardcoded screen-title text with string resources and add accessible labels as forms are introduced.
- Replace the template unit and instrumented tests with feature-focused tests.
- Clean up IDE-specific `.idea` changes before committing.
- Correct the system `JAVA_HOME` path or configure command-line Gradle to use Android Studio's bundled JDK.
