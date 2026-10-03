# Parem Launcher — Claude Code Instructions

Read `AGENTS.md` for the project rules, workflow and checks; this file only
adds Claude Code–specific guidance on top. Then `HANDOFF.md` (live state) and
`ARCHITECTURE.md` (code map, conventions, and the "Traps" section: the
invisible lock view, widget ID bookkeeping, the package-change stamp).

## Workflow for a work package

0. Claim it before reading anything: `scripts/claim-wp.sh next` (or
   `claim <id>`). Refused means another session has it — `next` moves on by
   itself.
1. Read the roadmap row and the files the change will touch.
2. Propose approach + tradeoffs — wait for confirmation on anything
   non-trivial, and always for rows marked **design first**.
3. Implement. Surgical changes only.
4. Run the checks in `AGENTS.md`. Fix until green.
5. Closing a work package: add `docs/handoff/<WP-id>.md`, run
   `node scripts/build-handoff.mjs` and commit its output. Never hand-edit the
   generated part of `HANDOFF.md` or a roadmap Done marker.

## Skills

| Task | Skill |
|---|---|
| Over-engineering creep, adding abstractions, choosing a dep | `/ponytail` |
| Review changed code before declaring done | `/simplify` |
| Audit whole codebase for bloat | `/ponytail-audit` |
