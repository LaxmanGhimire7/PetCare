# PetCare Project Status

Last updated: September 18, 2026

## Completed application

- Native Android app built with Kotlin, XML layouts, Material 3, Navigation, View Binding, Room, Coroutines, WorkManager, and Coil.
- Professional responsive light and dark themes, consistent cards, typography, icons, validation, empty states, and edge-to-edge system-bar handling.
- Splash, local registration, login, persisted signed-in state, logout, and input validation.
- Dashboard with personalized greeting, pet summaries, upcoming care, completed-care history, care tools, reminder settings, and clear first-use states.

## Pet profiles

- Create, edit, and delete pet profiles.
- Store name, animal type, breed, age, weight, dietary preferences, vaccination history, allergies, favourite toys, medical records, grooming routine, and health notes.
- Select and persist multiple on-device pet photos. The first image is used as the dashboard profile image.
- Existing profile data is preserved by Room migrations.

## Care plans and checklists

- Create and edit care tasks with a pet, title, category, due date, reminder time, repeat frequency, required supplies, and instructions.
- Categories include feeding, exercise, grooming, medication, healthcare, cleaning, and general care.
- Frequencies include one time, daily, weekly, and monthly.
- Mark tasks complete, retain completed history, edit tasks, and delete tasks with confirmation.
- Completing a daily, weekly, or monthly task automatically generates and schedules its next occurrence.
- Routine generator creates selected feeding, exercise, grooming, medication, and healthcare tasks for a pet.
- Local WorkManager notifications are scheduled per task. Default reminder time is configurable.

## Delegation and gestures

- Share the full upcoming checklist through the device SMS app with pet names, dates, frequencies, and instructions.
- Long-press an individual task to share it by SMS.
- Swipe right on an upcoming task to complete it.
- Swipe left to open task deletion confirmation.
- Double-tap a task to edit it.
- The dashboard displays the gesture guide directly above the checklist.

## Expenses

- Add expenses for a selected pet with category, amount, date, and note.
- Categories cover food, grooming, veterinary care, medication, toys, and other costs.
- Expense history shows localized currency and dates.
- Summary shows total spending and individual totals for each pet.
- Delete unwanted expense records.

## Geotagged places and provider integration

- Save veterinary clinics, grooming salons, dog parks, pet supply stores, and animal shelters.
- Store address, optional latitude/longitude, opening hours, phone number, and booking website.
- Open a saved place in a compatible map app using a `geo:` intent.
- Open the dialer for a provider and its booking website in a browser.

## Appointment import

- Import `.ics` or calendar text files with Android's document picker.
- Parse appointment title, date, description, and location.
- Pre-fill a healthcare task so the user can choose the pet and confirm the imported appointment.
- The picker does not require broad storage permission.

## Persistence and migrations

- Room database version 8 stores pets, care tasks, expenses, and service providers.
- Explicit migrations preserve databases created by versions 1 through 7.
- Deleting a pet cascades to its care tasks and expenses, and scheduled task reminders are cancelled.

## Verification completed

- `:app:assembleDebug` passes.
- `:app:testDebugUnitTest` passes, including ICS appointment parsing.
- `:app:lintDebug` passes with zero errors.
- `:app:connectedDebugAndroidTest` passes all three emulator tests.
- Device tests cover task completion/history, recurring-task generation, expense persistence, provider persistence, and Room relationships.
- The APK was installed and launched on the Android emulator. Login, dashboard, and expense screens were visually checked with no runtime crash.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Prototype scope

- User accounts and application records are stored only on the device; there is no cloud synchronization or production authentication server.
- Provider availability is represented by saved opening hours and booking links. Live appointment availability depends on the provider's external website.
