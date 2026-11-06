---
wp: M2-WP4
pr: 0
commit: 0ae0229
branch: chore/remove-dead-wallpaper-fn (merged baa682c)
date: 2026-10-03
---
`setPlainWallpaperByTheme` is deleted from `helper/WallpaperUtils.kt`; it had no callers and the deletion orphaned nothing.
Contract: none — nothing else was touched.
Not verified: the build in the docs lane (grep shows zero references).
Easy to get wrong: evidence is thin by design, a 13-line deletion; there is nothing here to inherit.
