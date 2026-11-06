---
wp: M2-WP3
pr: 0
commit: 89cf2e5
branch: release/v6.0.0
date: 2026-11-05
---
Settings shows "About Parem Launcher" (the GitHub project) and "Privacy" (`https://pilves.github.io/parem-launcher/privacy.html`), and both rows are searchable through `SettingsSearchIndex`.
Contract: rows stay driven by `Constants.URL_ABOUT_PAREM` / `URL_PAREM_PRIVACY` (empty hides them); the Data safety answers Patric needs are in `docs/play/compliance.md` section 8, since this landed without a PR.
Not verified: on a device; the Privacy row opens a live page, because it stays 404 until `web/privacy.html` is committed and Pages deploys from `master`.
Easy to get wrong: About and GitHub open the same URL on purpose; `web/privacy.html` is uncommitted and awaiting owner review, so a merge without it publishes a dead link.
