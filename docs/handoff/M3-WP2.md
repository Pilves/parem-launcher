---
wp: M3-WP2
pr: 0
commit: da366ce
branch: refactor/omnibox-resolver (merged 091f21a)
date: 2026-10-03
---
`helper/OmniboxResolver.resolve(query, contacts, rates, settings, now)` returns a sealed `OmniboxMode` and owns the drawer's mode precedence; `AppDrawerFragment.updateOmniboxState` only renders the result.
Contract: precedence calc → conversion → currency → dial → web → quick action → contact → setting → none; the first mode that both recognises and resolves wins, so an expression that fails to evaluate falls through; dial needs at least 4 digits (`+`, digits, spaces) and hyphenated numbers lose to the calculator.
Not verified: drawer behaviour on a device after the extraction (the JVM tests cover the precedence only).
Easy to get wrong: M3-WP4, M4-WP15 and M4-WP17 each added a mode by inserting one branch in this order with a resolver test — add a new one the same way, never in the fragment.
