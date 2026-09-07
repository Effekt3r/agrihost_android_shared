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

---
*This CLAUDE.md is a starting point — expand it with module layout, the composite-build
wiring, and publishing notes as you work in the repo.*
