# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@WORKFLOW.md
<!-- ^ Shared engineering canon ("How We Work", identical across all repos). Project-specific guidance continues below. -->

## Project

**Agrihost Android Shared** — a multi-module **Kotlin/Android** library consumed by the
`hael`, `mr`, and `vrugte` apps via composite build. Modules include `app-config`, `auth`,
`bootstrap`, `fcm-inbox`, and `audit` (the shared Firestore audit-log module). In CI the
library is pulled via a public GitHub clone.

## Context

- Part of the **Agrihost** workspace (`../`); see `../CLAUDE.md` for the cross-repo brief
  (the suite, tag conventions, the shared-audit rollout status, and the domain glossary).
- Because three apps consume this library, **a change here ripples into all of them** —
  bump versions deliberately and verify each consumer still builds.

## App updates (`:app-config` 1.2.0, 2026-11-12)

- `AppUpdateBanner` is the red update banner on each app's main screen (hael `MainActivity`, vrugte
  `MainActivity`, mr `ClientActivity`): the app includes `@layout/agri_app_update_banner` and builds the
  banner in `onCreate` (Play's result launcher must register before the screen starts).
- **Required:** below the console's `minVersion` with Force ticked, no X. **Suggested:** below `minVersion`
  without Force, or Google Play has a newer version (Play In-App Updates on every resume; Play installs
  only; 15 min wait after a failed check). The X hides a suggested banner for 24 h (prefs `agri_app_update`).
- Shows only 18:00-06:00 tablet time or on unmetered Wi-Fi (a phone hotspot is metered, so it does not
  count). It redraws by itself at 06:00/18:00, on network changes and on a new config document.
- The tap, and the Update button of the apps' pop-ups (`AppUpdateUi.showUpdateDialog(..., onUpdate)`),
  open Play's full-screen update inside the app. The store page opens only when Play can't (an adb or
  App Distribution copy, or Play not yet at the minimum). The forced pop-up stays each app's own and
  shows at any time.
- Rules and wiring are tested in `app-config/src/test` (Robolectric + Play's `FakeAppUpdateManager`).
  Play's real update screen can only be seen from a Play install (internal app sharing).

---
*This CLAUDE.md is a starting point — expand it with module layout, the composite-build
wiring, and publishing notes as you work in the repo.*
