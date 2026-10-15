---
wp: M2-WP6
pr: 0
commit: 2a2549d
branch: spike/drop-query-all-packages (merged cb8ccb4)
date: 2026-10-03
---
`QUERY_ALL_PACKAGES` is gone from the manifest; a `<queries>` block declares the four intents Parem resolves: MAIN/LAUNCHER (app list, labels, icons, usage-stat names), MAIN/HOME (default-launcher check), `org.adw.launcher.THEMES` (icon packs) and `APPWIDGET_UPDATE` (widget providers).
Contract: any new `PackageManager`/`LauncherApps` lookup not covered by those intents needs its own `<queries>` entry, or it returns nothing on Android 11+.
Not verified: hidden apps, usage-stat names, icon packs and the omnibox on a device — the verdict rests on reading the code paths, not a device pass.
Easy to get wrong: the failure mode is empty results, not an exception, so a missing query looks like "the app has no X" rather than a permission problem.
