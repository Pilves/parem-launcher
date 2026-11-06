---
wp: M3-WP3
pr: 0
commit: 1a4f70a, 8109425
branch: test/prefs-registration-guard (merged 447cb8f, 996ef0c)
date: 2026-10-03
---
`app/src/test/.../data/PrefsRegistrationTest.kt` scans every `.getLong(`/`.getFloat(` read in `app/src/main` and fails when the key is neither in `LONG_PREF_KEYS`/`FLOAT_PREF_KEYS` nor in `exportExcludeKeys`.
Contract: a key resolves from a string literal, a `val` constant in the same file, or a unique constant elsewhere; `Settings.Global/Secure/System` reads are skipped; an unresolvable token fails the test.
Not verified: nothing beyond the JVM run; the test found no real gap in `Prefs.kt` at the time.
Easy to get wrong: a key built by string interpolation or a non-unique constant name reads as unresolvable and fails — make the key a plain `val` the regex can see rather than loosening the test.
