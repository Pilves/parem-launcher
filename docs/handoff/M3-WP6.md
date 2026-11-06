---
wp: M3-WP6
pr: 0
commit: b095cc5, 2edf5e6
branch: feat/keyboard-dpad, fix/keyboard-dpad-focusable
date: 2026-10-03
---
Home slots, clock, date, screen time and drawer rows are D-pad focusable with a focus-only highlight (`bg_focus_highlight.xml`), Enter launches and long-press Enter opens the menu; typing a letter or digit on home opens the drawer search seeded with it, and the Menu key opens the drawer.
Contract: `helper/HomeKeyInput.drawerQuery(isMenuKey, unicodeChar, hasShortcutModifier)` decides what a key press does; the seed travels as nav arg `Constants.QUERY`; the hidden lock view is not focusable; `focusableInTouchMode` stays off so touch users never see the highlight.
Not verified: arrow-key reachability of every slot and row on an emulator with a hardware keyboard (the roadmap's done criterion); the API 24–25 fix in 2edf5e6 is moot since M4-WP20.
Easy to get wrong: slot click listeners exist for keyboard Enter while touch still goes through `ViewSwipeTouchListener` — putting touch logic in the click listener double-fires.
