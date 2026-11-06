# Fable and Codex work split — 16 October 2026

Planning baseline: `next` at `0b08770`. Fetch the latest `origin/next` before starting; this document assigns lanes, not locks. Read HANDOFF.md, AGENTS.md, ARCHITECTURE.md traps and the full package row in docs/ROADMAP.md. Claim each package immediately before work. Do not claim the entire queue in advance.

## Objective

Finish practical usability gaps before expanding the redesign: accessible controls, discoverable hidden apps, usable tablet sheets, then mindful pause without a time limit. Keep the approved menus and expandable onboarding intact.

## Ownership and sequence

| Stage | Fable | Codex | Gate |
|---|---|---|---|
| 1 | M4-WP35: accessibility semantics and touch targets | Review the recoverable M4-WP24 tablet-sheet patch read-only; prepare its device checks | WP35 owns shared dialog source until it lands. Codex does not edit those files concurrently. |
| 2 | M4-WP45: explicit Hidden apps entry and safe search hint | M4-WP24: integrate/adapt tablet-sheet fix | Both start from integrated WP35. Separate source ownership; serialize builds and shared documentation integration. |
| 3 | Prepare M4-WP49, then WP50/51 design proposals, one claimed package at a time | M4-WP47: any-app limits, pause-only and ending-focus pause | WP47 starts only after WP24 and WP35 land. Fable's lane is proposals only pending approval. |
| Later | Approved first-run, typography and settings packages, in roadmap dependency order | M4-WP55 drawer motion after preceding home/drawer changes settle | Do not start WP55 concurrently with another owner of HomeFragment, AppDrawerFragment or HomeSlotsController. |

Codex coordinates integration into `next` and device QA. Fable delivers one reviewable branch/PR per package. Neither lane merges to `master`, tags a release, or uninstalls the user's existing launcher.

## Claim snapshot at planning time

The remote claim listing on 16 October shows no claim for WP35, WP45 or WP47. WP24 is still claimed on `fix/tablet-sheet-width` (4 October), and WP49/50/51 have claims on `next` (15 October). These existing claims must be reconciled with their original session before implementation or proposal editing; their age alone does not authorize takeover. Fable can start by attempting the WP35 claim. Codex can review the existing WP24 patch without mutating its owned files. Recheck all claims at kickoff because this snapshot is not a reservation.

## Fable: M4-WP35 first

Outcome: settings rows have a single spoken label, role and state; headings are navigable; interactive rows have at least 48dp targets; app-limit rows explain usage and limit; focus-cap and pause-ready changes are announced.

Own the settings row layouts and binding code, relevant drawer/widget accessibility call sites, and limit/focus/pause accessibility changes identified by WP35 and its audit. Name the exact files in the branch handoff before expanding scope. This package owns BottomSheetMenu accessibility basics, but inspect the current implementation first: WP53 already added heading semantics, a skipped decorative handle, minimum-height rows and checked-state semantics. Keep correct work rather than rebuilding it.

WP35 builds on WP30's whole-row targets. WP30 is not globally complete: inspect the relevant prerequisites, identify missing row-target work and coordinate a separately claimed WP30 fix if necessary. Do not silently close or absorb the whole WP30 bundle.

Do not reorganize settings sections, change feature behavior, redesign typography, or remove the invisible lock view/handler. Keep existing screen-reader and keyboard launch protections.

Acceptance: mandatory checks pass; inspect label/role/state and duplicate-node behavior; document real TalkBack speech as unverified unless actually exercised. Include normal and 200% text checks. Finish WP35 before claiming WP45.

## Fable: M4-WP45 second

Outcome: Settings exposes Hidden apps with a count; tapping opens the hidden list; a non-identifying search hint helps recover hidden apps without leaking locked Private Space information.

Own the settings row in portrait/landscape, AppInfoSettingsCard, SettingsSearchIndex, the drawer hint and the legacy HIDDEN-dialog trigger removal, exactly as the roadmap specifies. Hiding already keeps the drawer open after WP53; preserve that behavior and add only missing feedback. Preserve profile-specific hidden identities and the legacy title shortcut.

Acceptance: count and list agree, hide/show works, search-index tests pass, hint respects Private Space gating, and no hidden app label is disclosed by the hint. Update the onboarding guide's hidden-app directions to the newly visible setting in the same package; this is an explicitly assigned integration follow-up in strings.xml, not an onboarding redesign.

## Codex: M4-WP24 then M4-WP47

WP24 has a recoverable candidate: `fix/tablet-sheet-width`, commit `44f68ff`. Verify that ref still exists and review against current code. Do not assume the old patch is ready or cherry-pick blindly. During WP35, review only. After WP35 lands, claim WP24, adapt the patch while preserving accessibility and current lifetime guards, then run the mandatory checks.

WP24 owns ScreenTimeLimitDialog and FocusModeDialog sizing/scrolling. Acceptance requires usable width in both tablet orientations and reachable scheduled-focus save. The connected Nothing phone cannot establish tablet acceptance; report missing tablet evidence explicitly if no tablet/emulator is available.

After WP24 lands, claim WP47. Implement:

- Add another app through the existing picker, including apps with no usage today.
- Mindful pause independent of a daily time limit, with persistence/export coverage.
- Go back first and Open anyway secondary in limit/pause flows.
- A cancellable pause before ending focus early.

Own the roadmap's limit/focus dialogs, AppLimitManager, MindfulPause and their tests. WP53 currently gates the app-menu pause row on a limit, and WP57 explains that prerequisite. This assignment also includes the necessary narrow updates to AppMenu/AppMenuPolicy, policy tests and onboarding strings so pause-only is reachable and accurately explained. Coordinate these extensions before any other package touches those files. Do not redesign the whole menu or onboarding.

Test every launch route named by WP47. WP36 remains a separate launch-routing correctness package: identify bypasses honestly, coordinate its claim if needed, and do not claim universal enforcement based only on a helper test.

## Design work and later motion

The user approved WP53 menus only. That approval is not sign-off for WP49 seeding, WP50 typography/theme resources or WP51 settings structure and the Calm name.

Existing untracked drafts in docs/design/M4-WP49.md, M4-WP50.md, M4-WP51.md and UX-REDESIGN-6.0.md belong to the ongoing workspace. Read and preserve them; do not overwrite or stage them wholesale. Reconcile proposals against the integrated app and request the specific design decisions before implementation. Claim each proposal's work package and stop at the proposal until signed off.

WP55 remains later: destination guards, release-based pull-to-close, reduced-motion behavior and seekable navigation animators. It must follow other changes to its home/drawer files and the relevant design-token work. Keep the existing letter/swipe dispatch behavior covered by regression tests.

## Coordination rules

- Use separate worktrees and branches based on current `origin/next`; never let two sessions switch the shared checkout underneath each other.
- Claim with `scripts/claim-wp.sh claim <id>`. A lane assignment does not override a live claim. If refused, report its holder; do not delete it or assume an old claim is abandoned. Pick another ready unclaimed package only within this agreed scope.
- One package per branch. No silent ownership expansion. Shared strings and CHANGELOG additions must survive integration from both lanes.
- Codex owns phone interaction during this wave; Fable requests a test rather than issuing concurrent ADB input.
- Serialize every Gradle invocation on the Pi with `/tmp/claude-1000/gradle.lock`, including worktrees. Configure the Android SDK and the documented ARM64 aapt2 wrapper.
- Run `flock /tmp/claude-1000/gradle.lock ./gradlew compileDebugKotlin testDebugUnitTest assembleDebug`; preserve its exit status. Also run `node --test scripts/build-handoff.test.mjs` and `git diff --check`.
- Put the handoff in the PR body using `.github/PULL_REQUEST_TEMPLATE.md`. Add the package fragment only when its implementation is complete, regenerate HANDOFF/ROADMAP, and preserve both sides' fragments during merges. Do not hand-edit generated Done states.
- Report commit, files owned, check results, what device work actually ran and what remains unverified. Release the claim after landing or abandoning the package.
- Prefer task-appropriate engineers; ordinary Kotlin/XML implementation does not require Astra-level delegation. Avoid extra agents unless their scopes are independent.

## Copy/paste kickoff for Fable

> Work in /home/pi/parem-launcher. Read docs/plans/2026-10-16-work-split.md and the mandatory repo instructions. Your first lane is M4-WP35 accessibility; M4-WP45 follows after WP35 lands. Claim WP35 before editing and use an isolated worktree based on current origin/next. Check the relevant WP30 prerequisites and preserve the accessibility work already in WP53. Codex owns integration and phone QA, and will review WP24 read-only until you release the shared dialogs. Do not start the settings redesign or motion package. Deliver one scoped branch/PR with all required checks and a truthful handoff. If the claim is held, report who holds it before taking another ready task from the agreed lane.

This document completes coordination only. It does not mark WP24, WP35, WP45, WP47 or any design implementation complete, and does not itself launch another Fable session.
