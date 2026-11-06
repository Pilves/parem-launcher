---
wp: M4-WP7
pr: 0
commit: 9753f3c
branch: feat/website-shortcuts
date: 2026-10-03
---
A home slot can hold a website (slot long-press, or "Add website…" in `CreateFolderDialog`); it opens through `ACTION_VIEW`, or in an installed web app that claims the address, and survives export/import.
Contract: `Prefs.getHomeAppUrl(slot)`/`setHomeAppUrl` on keys `APP_URL_1..8` ("" means the slot holds an app); `FolderApp.url`; `helper/WebShortcut.normalize` accepts only http(s) (a missing scheme becomes https, `host:port` is not a scheme) and `defaultLabel` strips `www.`; during focus the first whitelisted browser is pinned with `setPackage`, no chooser (DECISIONS.md); no globe icon.
Not verified: that `ACTION_VIEW` from Parem reaches a WebAPK rather than Chrome on Android 12+; export/import of a website slot on a device.
Easy to get wrong: slot indexing is `flag`-as-slot (trap #3) and rename writes `setHomeAppName(flag, …)`, which also relabels a website slot; websites branch before `isPackageInstalledCached` and have no app limit or mindful pause because limits are keyed by package.
