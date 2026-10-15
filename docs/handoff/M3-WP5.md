---
wp: M3-WP5
pr: 0
commit: 83a668f, f7a7b5a
branch: feat/bold-font
date: 2026-10-03
---
Appearance settings has a "Bold font" toggle (`Prefs.boldFont`, key `BOLD_FONT`, default false) that swaps the theme attr `mainFontFamily` so home, drawer and settings text use the heavier face; `MainActivity` applies the theme variant.
Contract: text that hardcodes a font family ignores the toggle — the home `TextClock` did until f7a7b5a pointed it at `?attr/mainFontFamily`; `TextDefault` sets no family, so dropping the attribute makes text regular weight even with the toggle off.
Not verified: that the toggle applies live without leaving settings and survives export/import on a device (a plain Boolean, so export carries it).
Easy to get wrong: a new layout with `android:fontFamily="sans-serif-light"` silently opts out of the toggle; use the attr.
