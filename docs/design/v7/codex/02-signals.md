# v7 upstream and market signals — Codex, step 1

Researched 2026-10-23. Source claims below are linked; recommendations are judgments, not measured Parem demand. Local baseline is `6404d9686ab165f38f431800bef680558be90abb`. `J/` means `app/src/main/java/com/parem/launcher/`. No competitor was installed or benchmarked.

## Upstream window and method

Read-only `gh api` queries used:

```sh
gh api repos/tanujnotes/Olauncher/commits/66712f7
gh api 'repos/tanujnotes/Olauncher/compare/66712f7...HEAD'
gh api -X GET search/issues -f q='repo:tanujnotes/Olauncher updated:>=2026-09-17' -f sort=updated -f per_page=100
gh api -X GET search/issues -f q='repo:tanujnotes/Olauncher is:issue' -f sort=reactions -f order=desc -f per_page=20
```

Also read the bodies of #761, #765, #766 and #769 through the issue API. The compare returned exactly two commits, ending at `f8e8d6497c457734c7a3171d36374157678e1f33`. The updated search returned 19 records, so no second page was needed. Its day-granularity window includes records earlier than the baseline commit on the same day; those are explicitly identified below. GitHub search is an indexed snapshot, not a guarantee that every historical discussion was indexed.

**Baseline correction:** [66712f7](https://github.com/tanujnotes/Olauncher/commit/66712f74de4030dc364d573708bea51d24823b40) is dated **2026-09-17 10:24 UTC**, titled “App version bumped to 113 (v6.9.17)”. The roadmap combines a July review date with that later SHA (`docs/ROADMAP.md:88`). This review uses the SHA as requested, not July as an alternative cutoff. [Comparison](https://github.com/tanujnotes/Olauncher/compare/66712f7...f8e8d6497c457734c7a3171d36374157678e1f33).

### Every commit after the baseline

| Commit | Signal | Parem verdict |
|---|---|---|
| [17d37fb, Sep 29](https://github.com/tanujnotes/Olauncher/commit/17d37fba6a8537f569f0b09b665f4b5cab9dfcd5) | Move screen-time query off main thread, avoid concurrent refresh, allow first value despite minute throttle | **Adapt only the uncovered part after reproduction.** Parem already uses IO and shared usage data (`J/MainViewModel.kt:307`). Persisted timestamp still gates initial value. Candidate C6 in audit; do not blindly port upstream's architecture. |
| [f8e8d64, Oct 17](https://github.com/tanujnotes/Olauncher/commit/f8e8d6497c457734c7a3171d36374157678e1f33) | Revise README descriptions of other apps | **Skip runtime port.** No v7 feature or correctness evidence. |

### Issues and PRs active in the window

States are the API snapshot, not judgments about whether Parem has the problem. Closed does not mean implemented or fixed.

| Record | State | Verdict / follow-up |
|---|---|---|
| [PR #761: IME composition](https://github.com/tanujnotes/Olauncher/pull/761) | Open, unmerged | **Strong fix candidate.** Author describes unknown IME subtype bypassing composition protection, and filtering temporary pinyin. Parem has the same empty-language path (`J/ui/AppDrawerFragment.kt:149`). Reproduce with unknown subtype and Latin IME; adapt semantics, not raw diff. |
| [PR #762: hardware keyboard opens search](https://github.com/tanujnotes/Olauncher/pull/762) | Open, unmerged | Already M3-WP6 (`docs/ROADMAP.md:112`). Carry only if unpicked, do not invent a duplicate v7 feature. |
| [PR #758: search matching tests](https://github.com/tanujnotes/Olauncher/pull/758) | Open, unmerged | Compare test cases when touching search; Parem already has `SearchMatcherTest` and `ContactMatcherTest`. Not justification for a new search framework. |
| [PR #759: CONTRIBUTING](https://github.com/tanujnotes/Olauncher/pull/759) | Open, unmerged | Skip: Parem's own agent/claim/handoff workflow governs contributions. |
| [PR #748: zh-TW](https://github.com/tanujnotes/Olauncher/pull/748) | Open, updated Sep 17 | Older PR, not new since baseline. Demand signal only; Parem-specific strings require its own translation plan. |
| [PR #733: ten locales](https://github.com/tanujnotes/Olauncher/pull/733) | Merged Sep 17 06:52 UTC | **Before baseline**, despite appearing in date-window search. No new port; M4-WP9 owns Parem's chosen locales. |
| [#766: search relaunch loop](https://github.com/tanujnotes/Olauncher/issues/766) | Open | Reporter could not reproduce consistently. Test query/session disposal and queued auto-launch; do not claim a confirmed Parem bug (`J/ui/AppDrawerAdapter.kt:146`). |
| [#767: installed Discord missing](https://github.com/tanujnotes/Olauncher/issues/767), [#768: app not found](https://github.com/tanujnotes/Olauncher/issues/768) | Open | Candidate regression scenarios for install/update/profile availability. Parem has stamp invalidation and stale-component fallback (`J/helper/PackageChangeTracker.kt:37`, `J/MainViewModel.kt:184`). No evidence of identical root cause. |
| [#765: same-label apps](https://github.com/tanujnotes/Olauncher/issues/765) | Closed | Concrete text-launcher ambiguity: Gemini crypto versus Gemini AI. Consider explicit disambiguation on collisions; renaming alone shifts diagnosis to the user. Profile identity matters too. |
| [#764: Outlook clock/date target error](https://github.com/tanujnotes/Olauncher/issues/764) | Closed | Test selected clock/calendar app resolution after updates and across profiles. Not a proven Parem defect. |
| [#770: theme/font appearance](https://github.com/tanujnotes/Olauncher/issues/770) | Open | Add to visual repro intake, especially theme changes without wallpaper. Do not prescribe a typography redesign from one report. |
| [#760: follow system theme](https://github.com/tanujnotes/Olauncher/issues/760) | Closed | Inspect current behavior before proposing; no independent evidence that v7 needs a theme studio. |
| [#580: disable search auto-open](https://github.com/tanujnotes/Olauncher/issues/580) | Closed, older issue updated | Durable control preference; weigh an explicit setting only after search correctness, with accessible default and migration specified. |
| [#755: reproducible build failure](https://github.com/tanujnotes/Olauncher/issues/755) | Closed, older issue updated | Distribution quality signal, not evidence Parem's build is unreproducible. Re-evaluate when owner chooses F-Droid scope. |
| [#689: purchase without Google](https://github.com/tanujnotes/Olauncher/issues/689) | Closed, older issue updated | Evidence some users value non-Play access; no Parem billing work needed under donations-only policy. |
| [#769: private security report contact](https://github.com/tanujnotes/Olauncher/issues/769) | Closed | Public body requests a private reporting channel; no vulnerability details. Do not infer an exploitable flaw in either launcher. |
| [#771: unrelated software repositories help](https://github.com/tanujnotes/Olauncher/issues/771) | Open | Exclude from launcher feature demand. |

## “Most requested” — measured proxy, not a market survey

The query returned 572 issue records; only the top 20 by **total reactions** were retrieved. Counts include all reaction types, are not unique users or +1 votes, favor older issues, and cannot be summed across duplicate topics. Below are leading feature/product signals from that sample, with important defect signals retained separately. This is not a complete ranking of every feature request, since issue labels/titles are inconsistent.

| Topic / source | Total reactions; comments | Interpretation for post-6.0 |
|---|---|---|
| [Website home shortcut #20](https://github.com/tanujnotes/Olauncher/issues/20) | 77; 40 | Dominant sampled issue. Already M4-WP7 (`docs/ROADMAP.md:142`); v7 should improve reliability/coverage, not announce it as new. |
| [F-Droid pictures #40](https://github.com/tanujnotes/Olauncher/issues/40) | 16; 4 | Discoverability/store quality, not runtime functionality. M4 store assets/distribution decisions cover much of this. |
| [Weather #464](https://github.com/tanujnotes/Olauncher/issues/464) | 10; 0 | Existing Parem weather (`J/MainViewModel.kt:278`); suggests table stakes, not novel differentiation. |
| [App-list scrollbar #46](https://github.com/tanujnotes/Olauncher/issues/46) | 8; 3 | Fast navigation through long lists deserves usability testing before adding more home content. |
| [Custom link #556](https://github.com/tanujnotes/Olauncher/issues/556), [PWA #555](https://github.com/tanujnotes/Olauncher/issues/555) | 7; 3 and 6; 3 | Corroborates shortcut topic; not 13 additional independent votes. |
| [More than eight home apps #475](https://github.com/tanujnotes/Olauncher/issues/475) | 6; 0 | Capacity pressure. Parem has folders and eight-slot contract (`J/ui/home/HomeSlotsController.kt:266`, `ARCHITECTURE.md:89`). Test those before expanding slot schema. |
| [Uninstall beside hide #98](https://github.com/tanujnotes/Olauncher/issues/98) | 6; 9 | Basic app management, not a differentiating theme. |
| [Bottom search #14](https://github.com/tanujnotes/Olauncher/issues/14), [web search #132](https://github.com/tanujnotes/Olauncher/issues/132), [battery #162](https://github.com/tanujnotes/Olauncher/issues/162) | 5 each; 1 / 1 / 5 | Reachability, convenient utility and glanceable state. Parem already dispatches web search (`J/ui/AppDrawerFragment.kt:183`). |
| [App aliases #258](https://github.com/tanujnotes/Olauncher/issues/258), [disable auto-open #580](https://github.com/tanujnotes/Olauncher/issues/580) | 4 each; 5 / 8 | Search should be predictable and user-controlled. Validate alias need separately from existing display rename (`J/data/Prefs.kt:462`). |

Defect signals in the same top-20 sample: [screen time reset #569](https://github.com/tanujnotes/Olauncher/issues/569) (7 reactions), [drawer stuck #713](https://github.com/tanujnotes/Olauncher/issues/713) (4), [app-list crash #282](https://github.com/tanujnotes/Olauncher/issues/282) (4), and [launcher disappears #436](https://github.com/tanujnotes/Olauncher/issues/436) (4). These support investing in trustworthy daily operation. They do not establish the same bugs in Parem. Remaining sampled topics were icons #245, new-app discoverability #208, and uninstall wording #616; no new high-cost theme follows from them.

## Competitors: four current references plus a bounded Ratio comparison

Official help/product sources were read on 2026-10-23. Feature descriptions are vendor claims, not measured efficacy. No pricing comparisons or unsupported adoption claims.

| Launcher | Verified signal / primary source | Table stakes it suggests (judgment) | Parem implication (judgment) |
|---|---|---|---|
| Niagara | [Pop-ups](https://help.niagaralauncher.app/article/115-pop-ups) combine apps, shortcuts, notifications and widgets; some customization/widget capabilities are Pro. [Notification help](https://help.niagaralauncher.app/article/31-notifications-not-showing) documents home notification display and device/configuration caveats. | Quick access can coexist with an uncluttered home; recovery explanations matter. | Do not copy the complete pop-up system. Explore one lightweight action surface only if users cannot reach M4 shortcuts comfortably. Widget recovery comes first. |
| Before | [Official FAQ](https://beforelabs.com/faqs) covers filtered notifications, Minimal Alerts, home/app-list folders and notification-access interruptions. | Notification filtering and folders are established; access loss needs understandable recovery. | M4 filtering is competitive parity. A clear local-only retention/visibility contract and reliable recovery could differentiate; neither is yet user-validated. |
| minimalist phone | [Official feature explanation](https://faq.minimalistphone.com/en/article/how-does-minimalist-phone-work-10lkqlz/) describes text home, in-app reminders, blocking schedules, notification filter, per-app monochrome and mindful launch delay. [Delay/extension FAQ](https://faq.minimalistphone.com/en/article/i-keep-extending-the-in-app-time-reminder-instead-of-leaving-the-app-what-can-i-do-1cpfcg8/) addresses users overriding reminders. | Calm-phone mechanisms are already a category, not a new invention. | Do not claim Parem invented pause/focus/filtering. Free, voluntary, on-device controls are a positioning hypothesis. Hard blocking and in-app policing would require a separate scope/permission decision. |
| KISS | [Official site](https://kisslauncher.com/) emphasizes fast search, small footprint, battery efficiency and links to F-Droid. Size/performance statements are marketing claims, not measurements used here. | Fast access and low overhead matter in minimal launchers. | Parem's omnibox plus opt-in attention tools is a plausible combination; preserve M3 performance budgets rather than grow a dashboard. |
| Ratio (limited evidence) | [Blloc's current page](https://www.blloc.com/) says Ratio moved to Prometheus LLC in December 2024 and existing subscriptions/lifetime deals remain valid. | No current feature matrix can responsibly be inferred from this page. | Treat as a historical positioning reference only. Do not promote a unified inbox because old Ratio descriptions mention one; current feature/support status was not verified. |

### What can actually distinguish Parem?

The roadmap promises free/no ads/no accounts/donations only (`docs/ROADMAP.md:152`), with new attention features opt-in and on-device (`docs/ROADMAP.md:129`). These are product commitments, not unique technical inventions or audited competitor deficits. Existing calculator/conversion/contact/web dispatch (`J/ui/AppDrawerFragment.kt:167`) gives Parem utility alongside restraint. **Hypothesis:** “reach intentional tasks quickly, put friction only around chosen distractions” is more coherent than maximum blocking or a feature-heavy home.

Candidate differentiators to validate: (1) dependable and reversible attention controls across every Parem launch route; (2) portable settings with honest handling of device-bound targets; (3) fast local utility search that behaves with IMEs, keyboards and multiple profiles. These overlap current architecture risks, which makes them useful investment filters. None warrants accounts, a backend, Compose, or broader accessibility access.

## Candidate packages to inform convergence, not a selected roadmap

Costs are guesses excluding device review. IDs are discussion handles. Normal repository validation/release rules apply only when implementation is later authorized.

| id | Outcome | Owns | Done |
|---|---|---|---|
| S1 | **Design first.** Decide how duplicate app labels/profile copies are distinguished; 1 day | new design proposal only | Same-name different-package and same-package different-profile examples are unambiguous; avoids exposing locked profiles; names exact drawer/layout files for subsequent implementation |
| S2 | **Design first.** Decide whether M4 shortcut support should expand to app actions; 1–2 days | new design proposal only | Tests actual M4 capabilities first; specifies static/dynamic/pinned/disabled targets, user/profile identity, unavailable-app and restore behavior; owner chooses ship/defer |
| S3 | **Design first.** Decide whether search auto-open needs an explicit user setting; 0.5–1 day | new design proposal only | Specifies default, keyboard/IME behavior and relation to accessibility; separate from C2's correctness fix; no implementation before approval |

S2 must follow final M4-WP7, not invent a second shortcut model. Android's [shortcut lifecycle guidance](https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts) documents static/dynamic ordering, disabled/outdated shortcuts and LauncherApps access. The documentation's Compose URL is not a reason to migrate Parem's Views.

Recommendation for the other planner: prioritize C2/C6-style correctness fixes, decide identity/backup contracts before feature expansion, and select at most one new everyday task after reviewing M3 carryovers. There is insufficient evidence here for a unified inbox, hard blocking, a theming studio or more home slots as a flagship v7 theme. Platform deadlines and launch-quality constraints belong to Claude's step-1 analysis; no cross-review has been performed here.
