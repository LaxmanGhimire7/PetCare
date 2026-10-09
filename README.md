# PetCare Android prototype

PetCare is a native Kotlin and XML Android app for keeping pet profiles, recurring care checklists, reminders, expenses, and useful places. Data is stored locally in Room and belongs to the signed-in local account.

## Desirable feature to present

**Location and geotagging** is the one desirable feature selected for assessment. Demonstrate it by saving a clinic, groomer, park, or supply shop from a typed address or map pin, then choosing that saved place while planning a care task. The app stores the chosen coordinates with the task and can show saved places on the map. The other app capabilities below support the core scenario; present location and geotagging as the single assessed desirable feature.

## Core prototype flows

1. Register or sign in. Local accounts have separate pet, task, expense, and place records.
2. Add a pet with breed, age, weight, diet, vaccination, allergy, toy, medical, grooming, and care notes. Photos are optional. Add up to five photos per pet, tap a thumbnail to choose the profile cover, and add or remove photos in Edit Pet.
3. Create one-time, daily, weekly, or monthly care tasks for a pet. Set category, time, supplies, instructions, and an optional saved place. Today and the pet profile show upcoming and completed checklist items; WorkManager schedules reminders.
4. Use the routine generator to create a set of tasks, or share an existing pet checklist with a caregiver. SMS opens the phone's messaging app with text only; the in-app preview can show the pet photo.
5. Record expenses by pet and category and view spending totals and category bars.
6. Save places with opening hours, phone, and booking link. Opening the booking link lets the owner check availability on the provider's site; this prototype does not receive live appointment slots from clinics.
7. Share appointment text or a calendar file into PetCare, review the extracted details, and save a care task. This requires an external message or file; the app does not read email automatically.

## Build

Open this folder in Android Studio with Android SDK 37 and JDK 17. On Windows, run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. The project uses `local.defaults.properties` when `local.properties` is absent. Google sign-in needs a valid `GOOGLE_WEB_CLIENT_ID` in your ignored `local.properties`; local email/password sign-in works without it.

Maps, place search, and directions need network access and a suitable device service. SMS and calendar sharing open external Android apps. The core pet, task, expense, and saved-place data remains local.

## Source map

- `app/src/main/java/com/example/petcare/data/local/`: Room entities, DAOs, account-scoped repositories, and preferences.
- `app/src/main/java/com/example/petcare/ui/`: fragments and screen logic.
- `app/src/main/java/com/example/petcare/design/`: reusable UI binders and presentation models.
- `app/src/main/java/com/example/petcare/reminders/`: scheduled task notifications.
- `app/src/main/res/layout/` and `app/src/main/res/values/`: XML screens, strings, dimensions, and typography.
- `app/src/test/` and `app/src/androidTest/`: local and device tests.
- `licenses/`: notices for the included fonts and icons.
