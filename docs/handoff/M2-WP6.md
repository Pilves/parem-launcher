---
wp: M2-WP6
pr: 0
commit: 2a2549d
branch: spike/drop-query-all-packages (merged cb8ccb4)
date: 2026-10-03
---
`QUERY_ALL_PACKAGES` is gone from the manifest; a `<queries>` block declares the four intents Parem resolves: MAIN/LAUNCHER (app list, labels, icons, usage-stat names), MAIN/HOME (default-launcher check), `org.adw.launcher.THEMES` (icon packs) and `APPWIDGET_UPDATE` (widget providers).
Contract: assess package visibility for every new `PackageManager`/`LauncherApps` lookup. The declared queries cover the named intents; Android also grants some automatic visibility, so add a `<queries>` declaration when the lookup needs visibility not otherwise provided (see [Android package visibility](https://developer.android.com/training/package-visibility/automatic)).
Not verified: hidden apps, usage-stat names, icon packs and the omnibox on a device — the verdict rests on reading the code paths, not a device pass.
Easy to get wrong: the failure mode is empty results, not an exception, so a missing query looks like "the app has no X" rather than a permission problem.
