# How We Work — Shared Engineering Canon

> Shared across all Effekt Studios / Wyobi project repos and imported into each repo's
> `CLAUDE.md` via `@WORKFLOW.md`. This file is the **general** way we work — the
> processes, guardrails, and terminology that apply everywhere. Project-specific specs
> stay in that repo's own `CLAUDE.md`. If a repo's `CLAUDE.md` genuinely contradicts
> this file, the repo wins **for that repo** — but prefer to fix the divergence.

## Git, tags & releases

- **Bulk git tags: wait ~30 seconds between each tag push.** CI (Codemagic) is triggered
  by git tags. Pushing several tags in quick succession overwhelms the build queue and
  builds can collide or miss their trigger entirely. Push one tag, wait ~30s, push the
  next. Never fire a batch of tags in a tight loop.
- **Check existing tags before creating new ones:** `git tag --sort=-creatordate | head`.
  Tags are lowercase `{flavor}-{version}` (e.g. `agrihosttest-8.0.83`). Flavor/platform
  tag prefixes are mutually exclusive on purpose — use the exact one you mean (e.g. "tag
  guardapp" means the `guardapp` tag only — not `all-*`, not test/32-bit variants).
- **Releases build from the trunk (`master`/`main`).** Merge your branch back and **push
  the trunk before pushing release tags** — CI checks out the trunk, so tagging while your
  code is still on a branch builds stale state and collides versionCodes.
- **"commit" means commit *and* push — but NOT tag.** When the user says "commit", that
  authorizes the full commit + `git push` with no separate confirmation, but it does **not**
  authorize a git tag. Ship every fix/feature to the remote in the same step — don't leave
  unpushed commits piling up.
- **Never tag unless explicitly told — each tag is a paid CI build.** A git tag triggers a
  Codemagic build that costs money and often deploys to a store/production channel. Do NOT
  infer a tag from "commit", "ship it", "implement this", or a prior round's cadence — tagging
  is a separate, explicit instruction every time. The order is **(1) test on the repo's
  designated device, (2) commit and push, (3) tag only when the user explicitly asks**.
  Deleting a pushed tag does not cancel a Codemagic build already triggered.
- **Bump the version on every release/deploy.** Each repo has one authoritative version
  source (e.g. `pubspec.yaml`, `version.json`, `<Version>` in a `.csproj`, `_version.py`);
  bump it and keep any mirrored constants in sync. Surface it where you can verify it
  (`/healthz`, a UI version chip, an about screen).
- **One feature/fix per commit**, with a clear one-line subject. Reviewable units.
- **Never bypass safety checks.** No `--no-verify`, no `git push --force` to shared
  branches, no skipping pre-commit hooks. If a hook fails, fix the underlying issue.
- **CI-safe commit messages.** Several repos pipe the commit message into a Codemagic →
  Discord webhook (a JSON field inside a single-quoted shell arg). Keep messages plain: no
  double quotes `"`, no backslashes `\`, no backticks `` ` ``, no `$`, no raw mid-message
  newlines — any of these can break the JSON/shell payload or fail the build step. Plain
  ASCII; emoji only if asked.

## Deploys & Firebase

- **Confirm the active project before any deploy** (`firebase use`). A wrong-project deploy
  is *silent* — the CLI prints "Deploy complete!" against the wrong project and you lose
  hours to fake `permission-denied` / `not-found` because the live app talks to a project
  you never updated. (The stray `agrihost-*` project on the shared Google account has
  caused exactly this more than once.)
- **Deploy rules/indexes in the same change as the client code that needs them.** Don't
  ship client code that depends on un-deployed Firestore/Storage rules or indexes. Run any
  pre-deploy rule check the repo provides first.
- **Deploy only when asked.** Don't auto-deploy after a feature. Build one feature, deploy
  if requested, then the next.
- **Gen-2 Cloud Functions callables need an explicit public invoker.** `invoker: 'public'`
  does not reliably bind on every deploy — grant `allUsers` / `roles/run.invoker` via
  `gcloud run services add-iam-policy-binding` for every `onCall` and any browser-hit
  `onRequest`. Trigger-only functions (`onDocumentCreated`, `onSchedule`) stay private.

## Firestore / Storage rules

- **Set the rules up *before* writing the client read/write.** Default-deny is real; a
  `permission-denied` is a preventable configuration bug, not a UX state. Identify the
  path, find/add the `match` block, verify the operation (`set(merge:true)` is `create` on
  a missing doc, `update` otherwise), and check field-level constraints.
- **Optional-field trap:** `resource.data.<field>` on a doc that lacks that field is an
  *error* (which denies), **not** null — and a `!= null` guard does NOT save you. Use
  `resource.data.get('field', default)` or `'field' in resource.data`. For a list query,
  one erroring clause denies the entire query.
- **Exercise every role against every screen/query** before trusting the rules. Bugs hide
  in roles that were never load-bearing before (parent vs student, advisor vs admin, …).
- **Sensitive writes go through a Cloud Function.** Anything money-, role-, claim-, or
  audit-related defaults to `allow write: if false` and is written by an Admin-SDK callable
  with a corresponding `auditLogs/{id}` entry.

## Errors must teach you

- **Surfaced errors are copyable and logged.** Use a selectable-text + Copy affordance
  (never plain text the user can't copy), and log to a durable store (a Firestore
  `appLogs`/`errorReports` collection and/or Crashlytics). A bare `[plugin/code]` is a
  debugging dead-end — capture the user action, the path/callable hit, what the rule
  expected, and the caller identity.
- **Cloud Functions use phase tracking:** `let phase = "init"; try { … } catch`, set
  `phase` before each external call (load / api / parse / write), and rethrow the error
  with `{ phase, stack, …details }` so the client can show which step failed.
- **Keep a running error log in the repo.** When the user reports a runtime/build error
  during testing, append an entry (newest first): short title → trace excerpt → root cause
  → fix → file(s) → lesson. Future sessions read it so the same mistake isn't repeated.
- **When you find a root cause, sweep for every other place it caused silent rot.** A
  wrong-project deploy, a shared-helper bug, or a copied rule clause usually broke more
  than the one thing in front of you. Fix the class, not the instance.

## Testing & verification

- **Tests accompany changes.** New utilities/models/logic ship with tests; run them. Where
  a repo follows TDD, follow it (red → green → commit).
- **Static analysis/compile must be clean before commit** (e.g. `flutter analyze`,
  `npm run lint`, the repo's compile task).
- **Build-passing ≠ correct.** Only running it confirms behaviour — manual/device testing
  is the real gate. Use the repo's designated test device (and avoid the ones it warns
  against). **Test on-device *before* you commit**, not after — get the change working on the
  device first, then commit and push; don't let the next fix land until the current one is
  confirmed.

## UI & architecture defaults

- **Mobile-first and responsive.** Build to the smallest target width first
  (~360 dp / 320 px), provide a desktop layout above the breakpoint, and check for overflow
  on every new screen. (Flutter: walk the constraint chain — constraints go down, sizes go
  up — before nesting layouts.)
- **Use the design-system tokens** (colors, type, spacing, components) — never hardcode
  brand colors or ad-hoc styles. Extend the token set instead.
- **Platform-native SDKs**, with API keys restricted per platform (Android SHA + package,
  iOS bundle id, web referrer) — don't consolidate to one unrestricted key.
- **All user-facing text is localized** — never hardcode a visible string. Add every new
  key to *all* locale files in the repo.

## Docs, process & terminology

- **brainstorm → spec → plan → execute**, one per feature. Specs and plans live under
  `docs/superpowers/specs/` and `docs/superpowers/plans/`, dated `YYYY-MM-DD-<topic>`. Keep
  the repo's `CLAUDE.md` a current cold-start brief for the next session.
- **Don't name brand dependencies in user-facing copy** ("Claude", "Anthropic", "Firebase",
  "Codemagic", …) — use generic terms.
- **Don't bake local machine paths** (`/Users/…`, `C:\Users\…`) into shipped code.
- **Prefer `trash` over `rm`** for anything you might want back; inspect existing
  config/scheduler/state before changing it and merge rather than overwrite.

### Shared context & terminology

- **Market:** South Africa. POPIA (data protection — field-level encryption + consent +
  audit), money as **ZAR integer cents**, 15% VAT, `en_ZA` locale. Afrikaans is a
  first-class language in the Agrihost apps.
- **CI:** Codemagic (`codemagic.yaml`), tag-triggered, publishing to Google Play (+ Huawei
  AppGallery / App Store / TestFlight where relevant).
- **Backend:** Firebase (Firestore, Auth, Storage, Cloud Functions, FCM) is the common
  stack; some services run on the Wyobi-operated `prometheus` host (nginx behind
  Cloudflare).
- **White-label "flavor" model:** one codebase → many branded apps (flavors). Flavor config
  drives `applicationId`, branding, API base URL, and feature flags.
- **"OpenItem"** is the Wyobi access-control platform that several Wyobi repos integrate
  with.

---
*To change how we work everywhere, update this file in each repo (it is intentionally
identical across repos). Project-specific guidance belongs in the repo's `CLAUDE.md`, not
here.*
