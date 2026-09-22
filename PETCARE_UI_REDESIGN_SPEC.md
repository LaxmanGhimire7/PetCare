# PetCare — UI Redesign Spec

This is the active UI specification supplied on 22 September 2026. It replaces Section 3 and the visual work in Phases 1–3 of `PETCARE_UPGRADE_SPEC.md`.

## A. Rules

1. Keep XML Views and the existing architecture.
2. Do not change entities, DAOs, repositories, or Room migrations.
3. Preserve every feature and gesture, including swipe complete/delete, double tap edit, long press share, shake reset, drag reorder, delegation, import/export, maps, and reminders.
4. Put text, colours, dimensions, and spacing in resources.
5. Comment every new style, custom view, and layout.
6. Commit one screen at a time with matching before/after screenshots in `docs/redesign/`.
7. Treat the connected Xiaomi Redmi Note API 31 as the visual source of truth.

## B. Device issues to fix first

- Bundle static font files at weights 400–800; no text below 400 and no pale information text.
- Put input hints only on `TextInputLayout`; use identical input padding for every field.
- Give the compact app bar an opaque canvas background, lift-on-scroll edge, and no oversized expanded gap.
- Give scrolling content enough bottom padding for the FAB.
- Use one anchored snackbar helper.
- Match light status/navigation icons to theme brightness.
- Hide tools that require a pet until one exists.
- Use the pet initial on its tint when there is no photo.
- Show “Nothing due today” with no percentage when nothing is scheduled.
- Replace mismatched care tool buttons with quick-action tiles.
- Use the task checkbox as completion control; show the drag handle only in reorder mode.
- Set `android:forceDarkAllowed` to false.

## C. Design system

The chrome is neutral. Pet colours encode identity. Brass is used for primary actions and selected states only.

### Colours

Light: canvas `#F4F5F7`, surface `#FFFFFF`, surfaceMuted `#ECEEF2`, hairline `#E3E5EA`, textPrimary `#1A1D24`, textSecondary `#5A6070`, textTertiary `#6E7482`, brass `#FFC83D`, onBrass `#1A1D24`, brassSoft `#FFF3CC`, error `#D92D20`, errorSoft `#FDECEA`.

Dark: canvas `#0F1115`, surface `#171A20`, surfaceMuted `#20242C`, hairline `#2A2F38`, textPrimary `#F2F3F5`, textSecondary `#A2A8B4`, textTertiary `#7D8390`, brass `#FFD15C`, onBrass `#1A1D24`, brassSoft `#3A3218`, error `#F97066`, errorSoft `#3A1C1A`.

Never use brass for text. Dynamic colour is off by default and can be enabled with a Settings toggle.

Pet colours keep the persisted index: Sky `#2F7DF6/#E6EFFE/#15233D`, Iris `#7A5AF8/#EFEBFE/#221B3F`, Rose `#E5487D/#FCE8EF/#3A1826`, Lagoon `#12A594/#E3F6F3/#0F2E2A`, Ember `#F2701F/#FEEEE3/#3A2212`, Stone `#6B7280/#EEF0F2/#23262C`. `PetColor` returns primary, theme tint, and readable tint text.

### Typography

Use bundled static Plus Jakarta Sans files: regular 400, medium 500, semibold 600, bold 700, extrabold 800. Scale: Display 800 28/34; Headline 700 22/28; Title 600 17/22; Body strong 600 15/20; Body 400 15/22; Supporting 500 13/18; Label 600 12/16; Figure 700 15/20 with tabular digits. Sentence case only.

### Geometry and components

- Spacing grid: 4, 8, 12, 16, 20, 24, 32, 40; screen margin 20; section gap 28.
- Radii: grouped list 20, header/pet/map 24, quick icon well 18, input 14, sheets 28 top, list photos 16, controls fully rounded.
- Flat content surfaces; shadows only for FABs, sheets, and dragged rows.
- Material Symbols Rounded icons, 24dp or 20dp inline.
- Primary buttons: brass, 52dp, pill. Secondary: surfaceMuted. Text buttons: textPrimary. Destructive: error.
- Filled inputs: surfaceMuted, no stroke/underline, 14dp, at least 56dp, one floating label.
- `PetAvatarView`: photo or initial, 3dp progress ring, optional selected outer ring; 56/40/28dp contexts.
- Task rows: 48dp checkbox target with pet colour, title and detail tags, right aligned time or error pill, grouped flat container, completed strikethrough. Preserve row tap, swipe, double tap, long press, and reorder gestures.
- Section headers, four-column quick actions, pill segmented controls/chips, grouped list rows, 72dp bottom navigation, extended brass FAB, compact app bars, Material bottom sheets, consequence-focused destructive dialogs, anchored snackbars, illustrated empty states, and reduced-motion skeletons.

## D. Screens

### Today

Compact date/greeting/profile header; seven-day strip; bold pet progress-ring row; plain-language summary; Overdue/Morning/Afternoon/Evening groups; collapsed Completed; New routine/Share list/Import plan/Export plan tiles; extended “New task” FAB. With no pets, hide planning tools and show “Add your first pet”.

### New and edit task

Borderless title and suggestions, avatar pet picker, category chips, grouped Date/Time/Repeat rows, expandable Place/Supplies/Notes, sticky Save task above IME. Edit reuses the screen and offers Delete task.

### Task detail

Bottom sheet with title, pet/category, grouped schedule/place/supplies/notes, optional lite map, and Mark done/Share/Edit/Delete actions.

### Pets and pet editing

Two-column 24dp pet cards plus Add card. Pet detail has photo hero, age/weight/tasks stats, Care/Health/Profile/Spending tabs, and overflow. Add/edit pet uses grouped Basics/Health/Care/Colour sections and a sticky save button.

### Money

Month selector, monthly total and change, pet-colour split bar, filters, grouped category and recent rows. Add/edit expense is a bottom sheet with amount, category, pet, date, and note.

### Places

Map-first with filters and draggable list sheet when a key is present. Provide complete list-first fallback. Detail sheet includes address/hours/phone/site and Directions/Call/Book. Add supports search and pin drop.

### Settings, authentication, delegation, import

Settings uses profile plus Preferences/Security/Your data/Help groups and isolated Sign out. Welcome/sign-in/register/onboarding use the same tokens and specific inline errors. Delegation shows contact, exact message preview, section toggles, SMS and share. Import review labels detected/missing editable fields and uses the selected pet name in its save action.

## E. Motion and haptics

Pet rings fill once per app session, staggered 60ms, 600ms each. Completion uses a spring checkbox, ring update, 220ms row move, and confirm haptic. Keep container transform, shared axis, fade-through, standard sheets, swipe threshold haptic, and delete undo. Do not animate list entrance, scroll, or every card press. Respect animator scale zero everywhere.

## F. Build order

1. Device bugs.
2. Tokens, static fonts, shapes, themes, pet colours, force-dark/system bars.
3. Reusable components plus a debug-only light/dark component gallery.
4. Today.
5. Task form and task detail.
6. Pets, detail, and add/edit pet.
7. Money.
8. Places.
9. Settings, auth, onboarding, splash, delegation, and import.
10. Motion and haptics.
11. Verification.

After each step run assemble, unit tests, and lint; install on Redmi; inspect touched screens in light and dark; save matching screenshots; then commit.

## G. Debug demo data

Add a debug-only Settings action that loads a fresh demo account with Max (Golden Retriever, Sky) and Luna (cat, Rose), realistic photos, tasks across all states, two months of expenses, and four places. Guard with `BuildConfig.DEBUG` and keep real data untouched.

## H. Verification

Verify on Redmi API 31 and API 24: every screen light/dark; font scales 1.3/2.0; 360dp and landscape; TalkBack names and announcements; recorded contrast ratios; 48dp targets; all existing gestures; every Part B issue; no hardcoded UI values; clean lint.

## I. Report evidence

Keep v1 screenshots. Record the physical-device trigger, neutral/pet/brass/type/grouped-row changes, v1/v2 outcomes, and measured contrast ratios. Document dynamic colour as opt-in to preserve identity and grouped lists as the chosen Material deviation.
