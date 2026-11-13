# Candidate v7 themes (Claude, step 1)

> Historical planning snapshot from October 2026; statements below describe
> that baseline or proposal, not the current queue. See [HANDOFF](../../../../HANDOFF.md)
> and [ROADMAP](../../../ROADMAP.md) for current state.

6.0 answered "can a launcher control attention, not just measure it?" Each v7
theme below is one question. v7 should pick **one** headline theme plus a
maintenance stream; three themes at once is how 6.0 got twelve WPs.

| # | Theme | Question it answers | Builds on | Rough shape | Risk |
|---|---|---|---|---|---|
| A | **Respond to launch** (not optional) | What did real users hit in the first month on Play? | Play vitals, closed test, reviews, IzzyOnDroid issues | M5: crash/ANR fixes, top-3 requests, declaration re-submissions, API 37 bump. 4–6 small WPs | Low. Danger is skipping it to start a theme |
| B | **Calm phone, round two** | Does attention control work over weeks, not days? | M4-WP1 quiet list, WP2 pause, WP3 scheduled focus, WP4 weekly review | Digest/inbox for silenced notifications; pause escalation ("you opened this 6× today"); focus profiles (work/sleep) bundling whitelist + grayscale + notifications; monthly review; stricter mode opt-in with cooldown-to-disable | Medium. Needs M4 telemetry-free feedback loop (closed testers); risk of nagging |
| C | **Hardened launcher** | Can Parem stay the default home on Android 17+ without the accessibility service? | Trap #1, M4-WP8, Private Space, work profiles | Lock alternatives (`DevicePolicyManager.lockNow` via device-admin? screen timeout hack? none?), API 37 edge cases, pin-shortcut requests (M4-WP7 B), `LauncherApps.Callback` coverage, Private Space polish, work-profile widgets if unpicked from M3 | Medium-high on lock: may end with "double-tap lock goes away" as the honest answer |
| D | **Pay down the Android-bound debt** | Can the next feature in home/widgets/drawer be built without fear? | `HomeWidgetController` (749), `AppDrawerFragment` (567), trap #2, M3-WP1 smoke tests, M3-WP10 perf CI | Extract widget-ID reconciliation into a tested `helper/` object; drawer sections (main/work/private) as one source; broaden smoke tests to the 6.0 features; perf budget enforced. No Compose, no DataStore unless a row proves payoff | Low-medium. Invisible to users; easy to under-prioritise, which is exactly why 6.0 shipped with a 749-line file |
| E | **Omnibox as the launcher's command line** | Can search replace the drawer for most launches? | M3-WP2 resolver, M3-WP4 currency, contact search, calc/units/dial/web | Actions ("timer 10", "note …", "call mum"), app shortcuts (`LauncherApps.getShortcuts`) in results, settings search, result ranking by time-of-day. All on-device | Medium. Scope creep magnet; must stay plugin-free per M3-WP2 |

## Not themes (keep saying no)

Theming studio, accounts/sync, ads/analytics, iOS-style widget stacks, a
built-in browser or notes app. Compose/DataStore migrations only as a
design-first row inside D with a measured payoff.

## Suggested shape (for discussion, not decided)

- **M5 — v6.1 "Respond to launch"**: theme A, time-boxed to ~6 weeks after 6.0
  reaches production. Also carries the API 37 bump if the deadline lands here.
- **M6 — v7.0**: one headline theme (B or C — the product/platform fork) plus
  2–3 rows from D as the maintenance stream. E waits for M7 unless launch
  feedback says search is the thing people love.

The open product question for Patric: **is v7 about going deeper on calm (B)
or about staying viable as a launcher (C)?** C is forced by Android 17 timing
only if the lock service actually gets revoked for launchers — M4-WP8's
"confirm on developer.android.com first" is the fact to settle before choosing.
