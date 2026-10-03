---
wp: M0-WP1
pr: 5
date: 2026-10-03
---
The platvorm agent harness runs here: `docs/ROADMAP.md` is the work queue, `scripts/claim-wp.sh` locks a package via a `claim/<id>` ref on origin, and `HANDOFF.md` is generated from `docs/handoff/`.
Contract: `scripts/claim-wp.sh` and `scripts/build-handoff.mjs` are byte-identical copies of platvorm's; change them there first and copy back, or the two repos drift.
Not verified: `claim-wp.sh claim`/`release` against origin (only `list` was run); the handoff workflow has not run on GitHub yet.
Easy to get wrong: owner-only work is listed without an id on purpose — `claim-wp.sh next` claims any id row, so a row an agent cannot finish must not carry one.
