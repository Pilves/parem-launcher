---
wp: M1-WP1
pr: 0
commit: f83763c
branch: docs/release-checklist (merged e4a6a02)
date: 2026-10-03
---
`docs/RELEASE_CHECKLIST.md` is the standing device-pass checklist: eleven core sections every release (home gestures, double-tap lock trap #1, slots/menus/folders, clock/date/screen time, widgets, capacity-capped picker, settings walk, export/import, contact search, tablet landscape, sheet scrolling), plus Performance, a 24 h battery soak, "This release touched" and a per-package "6.0 UX device pass"; ARCHITECTURE.md's release steps link to it.
Contract: later packages append to the checklist instead of rewriting it (M3-WP1, M3-WP10, M4-WP11, M4-WP18 and M4-WP32 all did); the UX section is append-only per DESIGN.md 13.2.
Not verified: nobody has run the checklist end to end on a device yet; it was seeded from the f4b55cf watchlist, not from a pass.
Easy to get wrong: the checklist is the only record of the device items the CI smoke tests do not cover, so an item removed from it is an item nobody checks.
