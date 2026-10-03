# Parem Launcher — Handoff

Current state only. History is `git log`, `CHANGELOG.md` and
`docs/archive/TODO-pre-roadmap.md`. Work queue: `docs/ROADMAP.md`.
Rules: `AGENTS.md`. Code map and traps: `ARCHITECTURE.md`.

**Last updated:** 2026-10-03 — latest handoff date

## Phase

v5.5.1 is the latest published release (2026-07-03). `master` at `f4b55cf`
is the v5.7.0 release candidate (versionCode 102), green since 2026-07-10 and
held for Patric's device pass — roadmap M1. Next: M2 (v5.8.0, Play compliance —
targetSdk 36 due 2026-11-01), M3 (v5.9.0, groundwork), M4 (v6.0.0, "Calm
phone"). What is next and who owns it live in
`docs/ROADMAP.md` — not here.

## What exists

Before the roadmap existed, and owned by no work package: an Olauncher fork
(`upstream` remote, last port `bec09c7`) with the omnibox (fuzzy search, calc,
unit conversion, dial, web, opt-in contact search behind READ_CONTACTS),
8 home slots with folders and home-screen widgets (tablet landscape: widgets
in a right-hand column), gesture letters, double-tap lock via the
accessibility service (trap #1), screen time from a hand-rolled UsageEvents
aggregator with app limits and focus mode, weather from Open-Meteo with
staleness dimming, one bottom-sheet language (`ui/BottomSheetMenu`), settings
split into per-section cards (`ui/settings/`), HomeFragment split into slot,
gesture and clock controllers (`ui/home/`), package-change-stamped app-list
and icon caches, settings export/import, and a JVM test suite for the pure
logic in `helper/`. Releases: tag `v*` → CI signs and publishes the APK.

Everything below is generated from `docs/handoff/` by
`node scripts/build-handoff.mjs`. Do not edit it by hand — add a fragment
instead. A milestone whose roadmap packages are all done is shown as one line
per package; the rest of each of those fragments is still in
`docs/handoff/<WP-id>.md`.

<!-- handoff:begin -->

- **M0-WP1 (PR #5):** The platvorm agent harness runs here: `docs/ROADMAP.md` is the work queue, `scripts/claim-wp.sh` locks a package via a `claim/<id>` ref on origin, and `HANDOFF.md` is generated from `docs/handoff/`.

<!-- handoff:end -->
