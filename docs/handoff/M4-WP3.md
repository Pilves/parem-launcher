---
wp: M4-WP3
pr: 0
commit: d27358f
branch: feat/scheduled-focus
date: 2026-10-03
---
The Focus mode sheet can add weekly windows (days plus start/end; cross-midnight and 24 h allowed) that turn focus on with the same allowed apps; `helper/FocusSchedule` computes active and `activeUntil` from the clock at check time, with JVM tests for midnight, overlap, the 7-day horizon and Tallinn DST.
Contract: no alarms, receivers or stored window state — `FocusModeManager.activeNow` unions the schedule with the timed session; the sheet's Disable calls `pauseSchedule` (sets `FOCUS_SCHEDULE_SKIP_UNTIL`, export-excluded) until the current run ends, while `disable`/`checkAndExpire` are untouched; `start == end` means all day; a blocked app's toast names the schedule end and the Settings path (decision: no direct link to the sheet in 6.0).
Not verified: anything on a device; the roadmap's "starts and ends on time ±1 min with the screen off" is not a thing this design does — state is evaluated when a launch is checked, not at a boundary.
Easy to get wrong: nothing fires at a window edge, so a package that needs a side effect at a boundary (grayscale during focus, notification silencing) must add its own trigger on top of the same maths.
