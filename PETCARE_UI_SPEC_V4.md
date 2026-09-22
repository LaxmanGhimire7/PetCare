# PetCare — UI Spec v4 “Pit Lane”

This file replaces all earlier UI specifications. `PETCARE_UPGRADE_SPEC.md` still governs functionality; this file governs every visual decision.

## Rules

- Use XML Views and keep the current architecture and Room schema.
- Keep every feature and gesture. Put all text, colours, and dimensions in resources.
- Verify each changed screen on the Xiaomi Redmi Note (API 31), with screenshots in `docs/redesign/v4/`.
- Remove every brass, cobalt, green, and mono theme resource. Dark is the default and dynamic colour is disabled.

## Mandatory fixes

Today is a chronological timeline. Completed tasks remain in place. It contains tasks due on the selected date and, only when today is selected, overdue tasks from earlier dates. A recurring task's next occurrence belongs only to its own date. Replace the generic ring and separate Completed list with a Next-up countdown hero and joined timeline rows on the canvas. Completed nodes use the pet colour with a black tick. Category is a small icon; Today rows have no date chip or separate done control. Task options live in the app-bar overflow. Pets uses the two-column grid without explainer copy, inline controls, or side rails. Every text appearance references an exact font file. Map every Material colour role, disable elevation overlay, implement edge-to-edge, disable navigation-bar contrast enforcement on API 29+, make bottom navigation 64dp plus its inset, and reserve FAB/nav clearance under lists. Use the Max and Luna demo account for screenshots.

## Theme

Dark tokens: canvas `#000000`, surface `#111113`, raised `#1A1A1D`, muted `#222226`, hairline `#27272A`, track `#2A2A2E`, primary text `#FFFFFF`, secondary text `#A1A1AA`, primary `#FF6B00`, on-primary `#000000`, primary container `#2A1408`, on-primary-container `#FFB28A`, error `#FF3B5C`, error container `#3A0F18`, on-error-container `#FF8A9E`.

Light tokens: canvas `#F4F4F5`, surface/raised `#FFFFFF`, muted `#EBEBEE`, hairline/track `#E4E4E7`, primary text `#09090B`, secondary text `#52525B`, primary `#FF6B00`, orange text `#C2410C`, on-primary `#000000`, primary container `#FFEDD5`, on-primary-container `#9A3412`, error `#DC2626`, error container `#FEE2E2`, on-error-container `#991B1B`.

Orange represents action and time. Pet colour represents identity. Dark pet colours/tints: Sky `#38BDF8/#0C2A3A`, Violet `#A78BFA/#241C3F`, Lagoon `#2DD4BF/#0D2E2A`, Lime `#A3E635/#1F2A0C`, Pink `#F472B6/#3A1530`, Silver `#D4D4D8/#26262A`. Light values: `#0284C7/#E0F2FE`, `#7C3AED/#EDE9FE`, `#0F766E/#CCFBF1`, `#4D7C0F/#ECFCCB`, `#BE185D/#FCE7F3`, `#52525B/#F4F4F5`.

## Typography

Use exact static files. Countdown: Barlow Condensed ExtraBold 56/54. Display: Condensed ExtraBold 34/36. Headline: Condensed Bold 26/30. Card title: Condensed Bold 23/26. Figure: Condensed SemiBold 16/20. Amount: Condensed ExtraBold 34/36. Section: Barlow Bold 17/22. Row title: Barlow SemiBold 15/20. Body: Barlow Regular 15/22. Supporting: Barlow Medium 13/18. Label: Barlow SemiBold 12/16.

## Today

Order: header, Next-up card, pet filters, quick actions, week strip, timeline, FAB. The hero shows pet, task, a live `HH:MM:SS` countdown, segmented daily progress, Mark done and Snooze. It handles normal, due now, all done, nothing planned, and another selected day. Run the countdown only while STARTED, recalculate from the clock, and handle midnight.

Sort the timeline by due time and never by status. Each row has a 44dp time/date column, a 22dp checkbox node in a 48dp target, connector, title, pet dot, category icon, optional place, and overdue label. Completing writes immediately, updates node/rings/segments, confirms with haptics and offers Undo without moving the row. Insert a live orange Now marker at the correct minute. Pet filters affect the timeline, hero and segments. The overflow contains Hide completed and Reset today.

## Remaining screens and acceptance

Task screens, Pets and pet detail, Money, Places, Settings, auth, onboarding, delegation, and import use the Pit Lane component system from the supplied v4 brief. Use a local dark/light map style and a usable list fallback. Animate the segmented bar and pet rings once per launch. Completing springs the node but never changes its position. Respect animator scale zero. Verify black canvas, black labels on orange, exact fonts, stable completion, selected-date filtering, countdown lifecycle/rotation/midnight, Now placement, gestures, TalkBack, 200% font scale, contrast, lint, and tests.
