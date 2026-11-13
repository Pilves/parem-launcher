# v7 planning — two-agent protocol

> Historical planning protocol from October 2026. Its pane assignments and
> sequencing are not the current work queue; see [HANDOFF](../../../HANDOFF.md)
> and [ROADMAP](../../ROADMAP.md).

Goal: map and project Parem **v7 = everything after v6.0.0 (roadmap M4)** and
turn it into `docs/ROADMAP.md` milestones (M5, M6, …) in the existing WP-row
format. Patric is the product owner and decides; Claude (tmux pane 2:1.1) and
Codex (tmux pane 2:1.2) are two planners working in this same checkout.

## Rules

1. **One owner per file.** `codex/*` is Codex's, `claude/*` is Claude's. Never
   edit the other's files — reply in `LOG.md` instead.
2. **`LOG.md` is the only shared file, append-only.** Each entry starts with
   `## <codex|claude> — <YYYY-MM-DD HH:MM>`. End an entry with a line
   `STATUS: <step> done` when a step is finished — the other agent watches
   that file.
3. **Planning only.** No code changes, no gradle builds, no commits, no claims
   (`claim-wp.sh`), no edits to `docs/ROADMAP.md`, `HANDOFF.md` or anything
   outside `docs/design/v7/`. Patric promotes the draft to the roadmap.
4. Cite evidence: file:line for code claims, URL for upstream issues or
   platform docs. A guess is marked as a guess.
5. Match the house style of `docs/ROADMAP.md`: one outcome per WP, files it
   owns, a done criterion, **design first** where a proposal is needed.
   Respect `AGENTS.md` constraints (no backend, SharedPreferences, no Compose
   unless the row argues for it explicitly).

## Steps

| step | Codex writes | Claude writes |
|---|---|---|
| 1 diverge | `codex/01-codebase-audit.md`: debt and traps that 6.0 leaves behind, places where the next features will hurt (ARCHITECTURE.md Traps, `helper/`, home/widget/lock paths), candidate refactors with cost. `codex/02-signals.md`: upstream Olauncher issues/PRs since `66712f7` and the most-requested features (gh api on tanujnotes/Olauncher), plus 4–6 competitor launchers (Niagara, Before, minimalist phone, Ratio, etc.): table stakes vs Parem differentiators | `claude/01-gaps.md`: what 6.0 explicitly deferred (roadmap "Not in 6.0", unpicked M3 rows, DECISIONS.md out-of-scope), platform deadlines (Android 17, Play policy), and the 6.0 launch-quality bars as v7 constraints. `claude/02-themes.md`: 3–5 candidate v7 themes with the question each answers |
| 2 cross-review | critique of `claude/*` in LOG.md: what is missing, what is wrong, what is not worth it | critique of `codex/*` in LOG.md, same |
| 3 converge | feasibility + file-ownership review of the draft in LOG.md: hot-file conflicts, hidden deps, rows too big for one PR | `claude/03-roadmap-draft.md`: M5/M6 rows in ROADMAP.md format, built from both sets |
| 4 decide | — | Patric reviews the draft, picks, Claude ports it to `docs/ROADMAP.md` |

Each step waits for the other's `STATUS` line before starting the next.
