# PetCare — Upgrade Spec

**Use:** put this file in the project root and tell your coding agent:
> Read `PETCARE_UPGRADE_SPEC.md`. Work through it phase by phase. After each phase run `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug`, fix anything you broke, then report what changed before moving on. Do not start a phase until the previous one builds clean.

---

## 1. What you are working on

PetCare is an existing, working native Android prototype (Kotlin, XML layouts, Material Components, Navigation, View Binding, Coroutines, WorkManager, Coil, Room v8, SharedPreferences). minSdk 24, targetSdk 36, compileSdk 37.

Already built: splash → login/dashboard routing, local registration and login, pet profiles with photos, care tasks with categories and repeat frequencies, routine generation, task completion with history, WorkManager reminders, SMS sharing, expenses with totals, saved places, and `.ics` appointment import. Room migrations 1→7 exist and pass.

Your job is not to rebuild it. Your job is to make it look and feel like a shipped product, and to take all three "desirable feature" areas — location, app integration, gestures/sensors — to full depth.

**Known gaps to close along the way (they are already written down as limitations, so fixing them is visible progress):**

- Only one local account per installation.
- Expenses and saved places have no edit screens.
- Monthly recurrence advances by 30 days instead of by calendar month.
- Backup rules are still template files.
- `petcare.db` is untracked in the project root and should not be committed.
- Test coverage is 2 unit tests and 3 database tests.

---

## 2. Rules you must not break

1. **Never delete or renumber an existing Room migration.** Add `MIGRATION_8_9`, `MIGRATION_9_10` and so on. Every schema change gets a migration plus a migration test.
2. **minSdk stays 24.** Any API above 24 is wrapped in a version check with a working fallback. Never raise minSdk to make an API compile.
3. **No hardcoded strings in layouts or Kotlin.** Everything goes in `strings.xml`. The brief marks presentation.
4. **No hardcoded colours or dimensions in layouts.** Use theme attributes (`?attr/colorPrimary`) and dimension resources.
5. **Comment the code properly.** KDoc on every class and every non-obvious function, and inline comments on the logic a marker would need explained — gesture thresholds, the sensor filter, migration reasoning, the recurrence calculation. XML layouts get a comment block at the top saying what the screen does. This is explicitly marked.
6. **Every destructive action gets an undo Snackbar.** Delete pet, delete task, delete expense, delete place, shake-to-reset. No silent data loss.
7. **Build must stay green.** If a library or API key is unavailable, degrade gracefully — never leave a crash path.
8. **Do not add heavyweight dependencies without need.** Prefer platform APIs and Material Components. Specifically: build the charts with `Canvas` rather than pulling in a charting library.

---

## 3. Design system

Replace the current theming wholesale. This is a deliberate visual identity, not Material defaults — but it stays inside Material 3 so it can be defended against the marking criteria.

### The idea

In a multi-pet app the single most important piece of information is **which animal a thing belongs to**. So colour is not decoration here — colour identifies the pet. The app chrome stays near-neutral and quiet; each pet is assigned a saturated tag colour at creation, and that colour is the only strong colour in the interface. A task's pet is readable at a glance from a colour rail on the left edge of its row, not from reading the name.

That gives you a genuinely arguable design rationale for the Part A design section: *colour encodes identity, structure encodes state, motion encodes change.*

### Colour tokens

Chrome (light):

| Token | Hex | Use |
|---|---|---|
| `surface` | `#FAFAF8` | app background |
| `surfaceContainer` | `#F1F1EE` | cards, sheets |
| `onSurface` | `#16171A` | primary text |
| `onSurfaceVariant` | `#5E635F` | secondary text, icons |
| `outlineVariant` | `#DCDCD6` | hairlines, dividers |
| `primary` | `#2E6B5E` | FAB, active nav, primary buttons |
| `onPrimary` | `#FFFFFF` | text on primary |
| `error` | `#B3261E` | overdue tasks, destructive |

Chrome (dark): `surface #101211`, `surfaceContainer #1A1D1B`, `onSurface #EDEEEA`, `onSurfaceVariant #A3A9A4`, `outlineVariant #2C302D`, `primary #7FC9B5`, `onPrimary #08322A`.

Pet tag colours — assign round-robin on pet creation, store the index on the pet row, let the user change it:

`#C2410C` rust · `#0369A1` lake · `#7C3AED` iris · `#B45309` amber · `#15803D` grass · `#BE185D` plum

Each needs a light-mode and dark-mode container variant (tint at ~12% for chips and rails). Generate these as a `PetColor` enum with `primary`, `container`, `onContainer` per theme rather than 36 loose colour resources.

Enable dynamic colour (`DynamicColors.applyToActivitiesIfAvailable`) on Android 12+ **for chrome only** — pet tag colours stay fixed, because they are identity, not theme.

### Typography

Two families, clearly distinct:

- **Fraunces** — display and headlines. Variable serif with real character; carries the warmth without being cute.
- **Manrope** — all UI, body, labels, numbers. Use its tabular figures for money and counts so columns align.

Load via downloadable fonts (`res/font/*.xml` with the Google Fonts provider) and bundle `.ttf` fallbacks in `res/font/` so the app never renders in Roboto by accident.

Scale (sp):

| Style | Family / weight | Size / line |
|---|---|---|
| Display | Fraunces 600 | 32 / 38 |
| Headline | Fraunces 600 | 24 / 30 |
| Title | Manrope 600 | 18 / 24 |
| Body | Manrope 400 | 15 / 22 |
| Label | Manrope 600 | 13 / 16 |
| Numeric | Manrope 600 tabular | 20 / 24 |

No all-caps labels anywhere. Sentence case throughout.

### Shape and spacing

- Corner radii: cards 20dp, sheets 28dp top, buttons 14dp, chips full, images 16dp. Different radii for different hierarchy levels — not one radius on everything.
- Spacing scale: 4 / 8 / 12 / 16 / 24 / 32. Screen margin 20dp.
- Elevation: use tonal surface colour, not drop shadows, for cards. Shadow only on the FAB and the bottom bar.

### Copy rules

- Buttons say what happens: "Save task", not "Submit". The action keeps its name through the flow — "Save task" produces "Task saved".
- Empty states are an invitation with a button, not a sad sentence. "No pets yet. Add Max, Luna, or whoever's waiting on you." + **Add a pet**.
- Errors say what went wrong and what to do. Never "Something went wrong."

---

## 4. Motion rules

Motion is spent in three places and nowhere else:

1. **One orchestrated moment** — the dashboard first-load: the progress ring fills from 0 to today's completion, and the task list staggers in at 40ms intervals. Once per app launch, not on every return to the screen.
2. **Response to action** — anything the user triggers: container transform from a pet card into the pet detail screen, swipe reveal behind a task row, the completion checkmark, the sheet opening.
3. **Change of state** — a number counting up when an expense is added, the ring redrawing when a task completes.

Never animate on scroll. No card hover effects. No decorative loops.

**Respect reduced motion.** Read `Settings.Global.ANIMATOR_DURATION_SCALE`; if it is `0f`, skip all non-essential animation and snap to end state. Put this behind one helper (`MotionPrefs.animationsEnabled(context)`) and check it in every animated path.

Durations: 200ms for small state changes, 300ms for screen transitions, 450ms for the container transform. Use `FastOutSlowInInterpolator` for enters, `FastOutLinearInInterpolator` for exits.

---

## Phase 1 — Design system and theming

**Goal:** the new identity exists in resources and the app compiles against it.

- Build `themes.xml` / `themes-night.xml` on `Theme.Material3.DayNight.NoActionBar` with every token above mapped to the correct Material attribute.
- Create the type scale as text appearance styles; override `textAppearanceHeadlineMedium`, `textAppearanceBodyMedium` etc. in the theme so Material components pick them up automatically.
- Create `PetColor` enum + a `ColorAssignment` helper. Add `colorIndex` to the pet entity — **Room migration 8→9**, default value assigned by row id modulo 6 for existing pets.
- Replace the splash with `androidx.core:core-splashscreen`: the paw/collar mark scales up with a slight overshoot and cross-fades into the first screen. Keep the existing routing logic.
- Add `android:enableOnBackInvokedCallback="true"` for predictive back, since you target SDK 36.
- Set up edge-to-edge properly: `WindowCompat.setDecorFitsSystemWindows(window, false)` plus `ViewCompat.setOnApplyWindowInsetsListener` on each root, applying top inset to the app bar and bottom inset to the nav bar and FAB. Nothing may sit under the system bars or the keyboard.

**Acceptance:** app runs in light and dark, no Roboto anywhere, no hardcoded hex in any layout, existing data intact after migration.

---

## Phase 2 — Navigation and dashboard rebuild

**Goal:** the dashboard answers "what do I need to do right now" in under two seconds.

**Bottom navigation**, five destinations: Today · Pets · Money · Places · Settings. Use `BottomNavigationView` wired to the existing Nav graph. On tablets / width ≥ 600dp swap to `NavigationRail` via a `layout-w600dp` variant.

**Today screen, top to bottom:**

```
┌──────────────────────────────────────┐
│  Good morning, Emily        [avatar] │   ← collapsing app bar, Fraunces
│                                      │
│    ╭───────╮   4 of 7 done today     │   ← progress ring, animates on load
│    │  57%  │   Next: Max's walk 5pm  │
│    ╰───────╯                         │
│                                      │
│  ● Max   ● Luna   ● All              │   ← pet filter row, tag colours
│                                      │
│ ┃ 08:00  Feed Max          Feeding   │   ← left rail = pet tag colour
│ ┃ 09:30  Brush Luna        Grooming  │
│ ┃ 17:00  Walk Max          Exercise  │   ← overdue rows use error colour
│                                      │
│  Completed today (4)            ⌄    │   ← collapsed section
└──────────────────────────────────────┘
                              (＋)       ← extended FAB, shrinks on scroll
```

- The progress ring is a custom `View` drawing two arcs on `Canvas` with a `ValueAnimator` sweep. Don't pull in a library.
- The pet filter row is a horizontal `RecyclerView` of circular avatars with the tag colour as a ring. Tapping filters the list with a `MaterialFadeThrough`.
- Task rows: 4dp left colour rail, time on the left in tabular figures, title, category chip. Overdue rows get the error-coloured rail and a small "Overdue" label — state read from structure, not from a red background wash.
- Section headers are plain sentence-case titles with a count, no eyebrow labels.
- Skeleton shimmer placeholders while Room loads, not a spinner.
- Extended FAB "Add task" that collapses to icon-only on scroll.

**Other screens:** apply the same rail/chip/card language to Pets, Money, Places. Every list gets a real empty state with an action button.

**Acceptance:** every screen reachable from the bottom bar, insets correct, filter works, no layout jank on rotation.

---

## Phase 3 — Motion pass

- **Pet card → pet detail:** `MaterialContainerTransform` shared element. Set `transitionName` on the card root to `"pet_${id}"`.
- **Sibling navigation** (Today ↔ Pets ↔ Money): `MaterialFadeThrough`.
- **Forward navigation** (list → form): `MaterialSharedAxis(X, forward = true)`.
- **Dashboard load:** `LayoutAnimationController` stagger, 40ms per item, runs once per launch — guard with a flag in the ViewModel so returning to the tab doesn't replay it.
- **Task completion:** row scales to 0.97 and back, checkmark draws itself (animated vector drawable with `trimPathEnd`), row fades out of Upcoming and slides into Completed, ring re-animates to the new percentage.
- **Swipe reveal:** background colour and icon scale in proportional to swipe distance — complete swipe reveals `primary` + check, delete swipe reveals `error` + trash. Icon pops to 1.1× when the action threshold is crossed. Haptic on threshold cross.
- **Number transitions:** expense totals count up with `ValueAnimator` when the value changes.
- **Deletion undo:** row collapses its height to 0 over 200ms; Snackbar with Undo restores it with the reverse animation.

Everything above checks `MotionPrefs.animationsEnabled()` first.

---

## Phase 4 — Location and geotagging (declare this as the primary desirable feature)

**Dependencies:** `play-services-maps`, `play-services-location`, `android-maps-utils` for clustering. API key in `local.properties`, read via the Secrets Gradle plugin — **never commit the key**, and add a `local.defaults.properties` with an empty key so the project builds for anyone.

**Build:**

1. **Places map screen.** `SupportMapFragment` showing all saved places. Custom marker icons per category (clinic, grooming, park, supply, shelter) tinted distinctly. Marker clustering when markers overlap. Tapping a marker opens a bottom sheet with name, address, distance from the user, opening hours, and actions: Directions · Call · Book · Edit · Delete.
2. **Current location.** `FusedLocationProviderClient`, runtime permission request with a rationale dialog explaining why. Handle: permission denied, permission permanently denied (send to settings), location services off, and no Play Services — each with a usable fallback, never a crash.
3. **Drop-a-pin place picker.** A map screen where the user long-presses to place a marker, then `Geocoder` reverse-geocodes to an address and pre-fills the place form. On API 33+ use the async `getFromLocation` callback; below that, run the blocking call off the main thread.
4. **Search without billing.** Don't use the Places SDK (it needs a billing account). Instead: a search field that geocodes a typed query, plus a "Find vets near me" button that fires `Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=veterinary+clinic"))` to hand off to Google Maps. Document this trade-off in the report — it's a defensible engineering decision, not a shortcut.
5. **Geotag the tasks themselves.** Add `latitude`, `longitude`, `placeId` to the care task entity — **migration 9→10**. A task can be linked to a saved place (vet appointment → the clinic). The task row shows a small location chip; tapping it opens directions.
6. **Distance and sorting.** Show "2.4 km away" on place cards; sort the places list by distance when location is available, alphabetically when not.
7. **Map preview on detail screens.** A small `MapView` (lite mode) in the place detail and in any geotagged task detail.

**Acceptance:** map renders with markers, permission flow handles all four failure cases, a task can be geotagged and reopened with its location intact, app still builds and runs with an empty API key (show a "Map unavailable" placeholder card instead of crashing).

---

## Phase 5 — App integration

This is the Emily scenario: her clinic emails her, she shares it into PetCare, the vaccination lands in Luna's schedule.

1. **Receive shared content.** Intent filters on a dedicated `ImportActivity`:
   - `ACTION_SEND` + `text/plain` — shared email body or message.
   - `ACTION_SEND` + `text/calendar` — shared calendar invite.
   - `ACTION_VIEW` + `text/calendar` with `content` and `file` schemes — opening an `.ics` attachment.
   PetCare now appears in Gmail's and Calendar's share sheets. That is the headline demo moment for this feature — show it in the screencast.
2. **Smarter parsing.** Extend the existing `.ics` parser to also handle plain text: regex out dates (`12/03/2026`, `12 March 2026`, `Mar 12`), times, and clinic names. Show a review screen with every parsed field editable before saving — never save silently. Say clearly in the UI what was recognised and what wasn't.
3. **Contact picker for delegation.** `ActivityResultContracts.PickContact` or `Intent(ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)` — the picker grants temporary read access, so you need **no** `READ_CONTACTS` permission. Store the chosen caregiver's name and number against the pet or task.
4. **Upgrade SMS delegation.** Keep `ACTION_SENDTO` + `smsto:` (no SMS permission). Pre-fill a properly formatted checklist: pet name, each task with time, supplies, and instructions. Add a preview screen so the user sees the message before the SMS app opens. Add a share-sheet fallback (`ShareCompat.IntentBuilder`) for WhatsApp/email.
5. **Write to the device calendar.** `Intent(ACTION_INSERT, CalendarContract.Events.CONTENT_URI)` with title, description, location and begin/end times — sends a vet appointment to the user's real calendar. No permission needed via the intent route.
6. **Export a care plan.** `ACTION_CREATE_DOCUMENT` via the Storage Access Framework, writing either a `.ics` file (so another pet sitter can import the whole routine) or a formatted `.txt`. Round-trip it: your own importer should read back what your exporter writes.
7. **Deep links.** App Links for `petcare://pet/{id}` and `petcare://task/{id}` so notifications and shared links open the right screen.

**Acceptance:** share a plain-text email from Gmail into PetCare and get a pre-filled, editable healthcare task; pick a contact and send a formatted checklist by SMS; export a plan and re-import it successfully.

---

## Phase 6 — Gestures and sensors

Existing: swipe right to complete, swipe left to delete, double-tap to edit, long-press to share. Keep all four, then add:

1. **Shake to reset today's checklist.** `SensorManager` + `TYPE_ACCELEROMETER`. Compute magnitude, subtract gravity, apply a low-pass filter, require the threshold to be crossed 3 times within 1 second, then debounce for 2 seconds so one shake fires once. Register in `onResume`, unregister in `onPause` — an unregistered sensor is a battery bug a marker will look for. Fires a confirm dialog, then resets with an undo Snackbar. Make the threshold a constant with a comment explaining the value.
2. **Drag to reorder tasks.** `ItemTouchHelper` with `dragDirs = UP or DOWN`, long-press to lift (row elevates and scales to 1.02), persist a `sortOrder` column — **migration 10→11**.
3. **Pull to refresh** on the dashboard with `SwipeRefreshLayout`, recalculating today's schedule and overdue state.
4. **Pinch to zoom** on pet photos — a full-screen photo viewer with `ScaleGestureDetector`, double-tap to zoom, swipe down to dismiss.
5. **Swipe between pets** on the pet detail screen with `ViewPager2`.
6. **Bottom sheet drag** for task detail, with a peek state showing time and title and a full state showing supplies and instructions.
7. **Haptics on every gesture.** `HapticFeedbackConstants.CONFIRM` / `REJECT` on API 30+, `KEYBOARD_TAP` below. One shared helper.
8. **Gesture discovery.** First-run coach marks on the dashboard — a short overlay demonstrating swipe and shake, dismissible, shown once (flag in SharedPreferences), and re-runnable from Settings.

**Acceptance:** every gesture works and is discoverable; shake does not fire while scrolling; sensor unregisters on pause; all destructive gestures are undoable.

---

## Phase 7 — Functional depth

Everything here is beyond the stated requirements and is what separates a good prototype from a product.

1. **Multiple accounts, properly.** Replace the single-account model. Hash passwords with PBKDF2 (`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, random 16-byte salt, 120k iterations) — plaintext or a bare hash will be noticed. Scope every Room query to the signed-in user (`ownerId` column — **migration 11→12**). Add a "stay signed in" toggle.
2. **Biometric unlock.** `androidx.biometric` with a device-credential fallback and a Settings toggle.
3. **Notification actions.** "Mark done" and "Snooze 1 hour" buttons on the reminder notification, handled by a `BroadcastReceiver` that updates Room and cancels/reschedules the work. Tapping the body deep-links to the task.
4. **Home screen widget.** `AppWidgetProvider` + `RemoteViews` listing today's remaining tasks, refreshed by WorkManager, tapping a row deep-links into the app. High-impact, very visible in a demo.
5. **Expense insights.** Custom `Canvas` donut chart for spend by category and a bar chart for the last six months. Per-pet breakdown. Month-over-month change.
6. **Export.** Expenses to CSV and a formatted PDF (`PdfDocument`) via SAF. Full-data JSON backup and restore.
7. **Search and filter.** A single search across pets, tasks and expenses, plus filter chips for category, pet, and date range.
8. **Edit screens for expenses and saved places** — closes an existing gap.
9. **Fix monthly recurrence** to use `Calendar.add(Calendar.MONTH, 1)` with proper end-of-month clamping (31 Jan + 1 month = 28/29 Feb), and unit-test that specifically.
10. **Settings screen:** theme (system/light/dark), default reminder time, notification toggle, biometric toggle, replay coach marks, export data, clear data, about (state the declared desirable feature here), version.
11. **Onboarding** — three screens on first run: what the app does, add your first pet, allow notifications. Skippable.
12. **Accessibility pass:** content descriptions on every icon and image, 48dp minimum touch targets, labels associated with inputs, contrast ≥ 4.5:1 verified for both themes, layouts survive 200% font scale, TalkBack announces task completion.

---

## Phase 8 — Architecture, comments and tests

- **MVVM throughout.** ViewModel + `StateFlow`, a repository layer as the single source of truth, DAOs returning `Flow`. No database access from a Fragment.
- **Sealed UI state:** `Loading | Empty | Content(data) | Error(message)` per screen, so every screen handles all four states explicitly.
- **KDoc and inline comments** as set out in section 2. Non-optional — it is directly marked.
- **Tests** — these feed the Part A testing table, which is worth 8 marks:
  - Unit: recurrence (daily/weekly/monthly incl. month-end), password hashing and verification, plain-text and `.ics` parsing, distance calculation, expense totals, colour assignment.
  - Room: every migration 8→9, 9→10, 10→11, 11→12 with `MigrationTestHelper`, plus CRUD and per-user scoping.
  - UI (Espresso): register → login → add pet → add task → complete task → verify it moved to Completed.
- Target 25+ automated tests and export the results so you can paste the table straight into the report.

---

## Phase 9 — Release hygiene

- Write real `backup_rules.xml` and `data_extraction_rules.xml`, excluding the credential preferences.
- Add `.gitignore` entries for `local.properties`, `*.db`, `build/`, `.idea/`. Remove the stray `petcare.db` from the project root.
- Clear the lint warnings — get it to 0 errors and under 10 warnings, and note any you deliberately suppress with a reason.
- Adaptive launcher icon and monochrome icon for themed icons.
- App name, version name/code, and a signed release build config.
- Update `README.md` and `PROJECT_STATUS.md`. **State plainly at the top of the README which desirable feature is the declared submission** (geotagging) and that integration and gestures were implemented as additional work.
- Commit the outstanding work in logical commits — not one giant "final" commit. Commit history is evidence of process.

---

## 10. Definition of done

- [ ] Builds, tests and lint pass clean.
- [ ] Light and dark themes both correct; dynamic colour works on Android 12+.
- [ ] Every screen handles loading, empty, content and error.
- [ ] Every destructive action is undoable.
- [ ] All permissions handle grant, deny, permanent-deny and unavailable-hardware.
- [ ] Reduced-motion setting respected everywhere.
- [ ] Rotation and 200% font scale break nothing.
- [ ] No hardcoded strings, colours or dimensions.
- [ ] Works fully offline.
- [ ] Runs on API 24 and on API 36.
- [ ] 25+ automated tests passing.
- [ ] README declares the primary desirable feature.

---

## 11. Capture evidence as you go

You need this for Parts A and C, and it is much harder to reconstruct afterwards.

- Screenshots of every screen in light and dark, at each phase, so you can show before/after in the report.
- The lint report and test results exported after each phase.
- A short screen recording of each gesture, the share-sheet import, and the map — you will cut these into the 10-minute screencast.
- Notes on anything you tried that didn't work and why. The demo explicitly asks for unresolved issues, and the evaluation section asks for critical reflection — a documented known limitation scores better than a hidden one.

Wireframes for Part A must be drawn in a design tool. Screenshots of Android Studio XML layouts score zero.
