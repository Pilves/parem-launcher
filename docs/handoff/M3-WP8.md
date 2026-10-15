---
wp: M3-WP8
pr: 0
commit: f2ce914, e11fb5c
branch: feat/widget-picker-profiles
date: 2026-10-03
---
The widget picker lists providers from every profile via `getInstalledProvidersForProfile` (work widgets under the badged app name) and shows Android 15 generated previews where the provider publishes one; picker rows block descendant focus so a preview's Button doesn't swallow the row tap.
Contract: no widget-ID logic changed (trap #2); `prefs.widgetProviders` still stores only the component string.
Not verified: adding a work-profile widget and seeing a live preview on a device.
Easy to get wrong: restore is not profile-aware — `processNextWidgetRestore()` rebinds without `EXTRA_APPWIDGET_PROVIDER_PROFILE`, so a lost work-profile widget comes back as the personal-profile provider (logged as a known limitation in the roadmap); previews are fetched eagerly for every provider before the sheet opens.
