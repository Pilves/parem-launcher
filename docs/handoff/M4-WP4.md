---
wp: M4-WP4
pr: 0
commit: 5867410
branch: feat/weekly-review
date: 2026-10-03
---
The screen-time sheet shows a weekly review: this week's daily average against last week's, and the app that rose and fell the most per day; `helper/WeeklyReview.compare(thisWeek, lastWeek)` is Android-free with JVM tests.
Contract: this week is the graph's 7-day total / 7; last week is averaged over the days still in Android's usage log (`recordedDays`) and hidden below `MIN_HISTORY_DAYS = 3`; app changes under `MIN_APP_CHANGE_MS_PER_DAY` (1 min) are ignored; `helper/usageStats/` is read-only here.
Not verified: the numbers against the 7-day graph on a device with a full usage history.
Easy to get wrong: Android keeps roughly ten days of usage events, so "last week" is usually 3–4 days — the sheet states the count, and the comparison is per day, not week totals.
