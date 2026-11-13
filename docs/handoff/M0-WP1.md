---
wp: M0-WP1
pr: 5
date: 2026-10-03
---
The platvorm agent harness runs here: `docs/ROADMAP.md` is the work queue, `scripts/claim-wp.sh` locks a package via a `claim/<id>` ref on origin, and `HANDOFF.md` is generated from `docs/handoff/`.
Historical contract: these scripts began as copies of platvorm's. They have since diverged; compare the current scripts and coordinate any cross-repository port instead of assuming they are byte-identical.
Not verified: `claim-wp.sh claim`/`release` against origin (only `list` was run); the handoff workflow has not run on GitHub yet.
Easy to get wrong: owner-only work is listed without an id on purpose — `claim-wp.sh next` claims any id row, so a row an agent cannot finish must not carry one.
