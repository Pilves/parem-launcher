# M4-WP6 — Private Space support (design proposal)

Status: proposal, awaiting Patric's sign-off. Base: `origin/chore/agent-harness`. No code changed, no gradle run.

## Problem

Private Space (Android 15+) is a hidden user profile. Its apps are visible to a launcher only if the launcher holds
`ROLE_HOME` **and** declares the normal permission `ACCESS_HIDDEN_PROFILES` (API 35). Parem declares neither today, so
its drawer cannot show those apps. Users with a private space have to open their apps through system Settings.

When a launcher declares the permission, Android requires it to:

1. show private apps in a separate container, identified via `LauncherApps.getLauncherUserInfo(h).userType == USER_TYPE_PROFILE_PRIVATE`;
2. let the user hide and show that container;
3. lock and unlock the space with `UserManager.requestQuietModeEnabled(true/false, h)`;
4. keep private apps out of view and out of search while the space is locked, by listening for `ACTION_PROFILE_AVAILABLE`/`UNAVAILABLE` (`EXTRA_USER`), with `isQuietModeEnabled(h)` for the current state.

Today `helper/AppListSource.queryRawApps` loops `UserManager.userProfiles` and does not check profile type or quiet mode.
Once the permission is added, private apps would mix into the main list, the omnibox, the hidden-apps list, and every
picker (home slots, folders, swipe, gesture letters, double-tap).

## Options

**A. Do nothing.** Costs nothing. Without the permission, Parem shows no private apps, so nothing leaks. Users still
have no way to reach those apps from Parem. This is the right choice if Patric judges the feature not worth 6.0.

**B. Minimal compliant section (recommended).** Add the permission. Private apps go in one section at the bottom of the
drawer, under a "Private space" header row that locks and unlocks the space. Private apps are never offered to pickers,
so they cannot end up on home slots or in folders. Everything is gated on `SDK_INT >= 35`. Cost: about 180 LOC across
8 files and one JVM-testable rule change. Tradeoff: users cannot pin private apps to home. The design makes that
impossible on purpose, which removes the whole "home slot shows a locked app" class of leak.

**C. Launcher3 parity.** Adds an install-app button (`getAppMarketActivityIntent`), a settings button
(`getPrivateSpaceSettingsIntent`, API 36), private apps on home that hide while the space is locked, and animations.
That roughly doubles the work. It also makes home slots and folders lock-aware (trap #5 territory), and leaks get much
harder to rule out. YAGNI for 6.0.

## Recommendation

Go with B. "Calm phone" is about less surface, and Private Space is a privacy feature, so the version we can prove has
zero leakage beats the one with more features.

## Exact scope

- **`AndroidManifest.xml`**: add `<uses-permission android:name="android.permission.ACCESS_HIDDEN_PROFILES" />`.
  Older platforms ignore an unknown permission.
- **`helper/AppListRebuilder.kt`**: add `isPrivate: Boolean = false` to `RawApp`. `rebuild(...)` gets two new
  parameters, `includePrivate: Boolean` and `privateLocked: Boolean`. Private entries:
  - are dropped when `privateLocked || !includePrivate`;
  - ignore the hidden-apps set and never appear when `!includeRegularApps`, so the hidden list cannot leak them;
  - sort after all regular entries.
- **`helper/AppListSource.kt`**:
  - `queryRawApps`: on API 35+, tag each profile's apps `isPrivate` using `getLauncherUserInfo`. Skip
    `getActivityList` for a private profile in quiet mode.
  - `getAppsList`: read `isQuietModeEnabled` on every call and pass it as `privateLocked`, so a snapshot taken while
    unlocked never serves locked data. Add `includePrivate: Boolean = false`. The default is `false` so that a caller
    which does not opt in never sees a private app. Only `MainViewModel.getAppList()` passes `true`. Two direct
    callers skip the ViewModel and must keep the default:
    - `ui/home/HomeSlotsController.showCreateFolderDialog` (line 329, feeds `CreateFolderDialog`): the folder picker.
    - `ui/settings/WellbeingSettingsCard.showFocusModeFromSettings` (line 65, feeds `FocusModeDialog`): the focus
      whitelist, which is keyed by package and exported.
    The "no private app in folders / no private package in exported settings" done criteria rely on this default.
  - `getUserHandleFromString`: skip private profiles, falling back to `myUserHandle()` as it does today. This is the
    single choke point for every userString-based launch (home slots, folders, swipe, gesture letters, double-tap).
  - `isPackageInstalledCached`: its snapshot hit (lines 140-146) matches by raw `userString` and never goes through
    `getUserHandleFromString`, and the snapshot holds private entries. Add `!it.isPrivate` to the `any { }` predicate,
    so a private entry never answers "installed" for a slot. The live fallback (`isPackageInstalled`) already resolves
    through `getUserHandleFromString`, so it checks the main-profile copy.
  - New `privateProfile(context): UserHandle?` helper.
- **`data/AppModel.kt`**: add `isPrivate: Boolean = false`.
- **`MainViewModel.kt`**:
  - `getAppList()` always passes `includePrivate = true`. It takes no flag and does not need one: `appList` is a single
    retained LiveData shared by every non-hidden flag, so the flag gate cannot sit here (see `AppDrawerAdapter`).
  - Register a context receiver for `ACTION_PROFILE_AVAILABLE`/`UNAVAILABLE` next to `PackageChangeTracker.register`,
    and unregister it in `onCleared`. Register it with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`, so
    it meets the explicit-export rule for targetSdk 34+ and stays valid at 36. On receive, it invalidates the snapshot,
    then refreshes both lists: `getAppList()` and `getHiddenApps()`. It does not choose `includePrivate` or know which
    flag is on screen. Each list is already correct for every consumer: `appList` always includes private apps, and
    `hiddenApps` never does. This needs `PackageChangeTracker.bump()` to become public (renamed `invalidate()`).
  - Add `setPrivateLocked(locked)`, which calls `requestQuietModeEnabled`. The system shows the credential prompt.
  - In `launchApp`, skip `AppOpenCounter.increment` for private users, so no private package name ends up in exported
    prefs.
- **`ui/AppDrawerAdapter.kt`** (the flag gate and all list decoration live here):
  - **Flag gate.** The first line of `setAppList` drops `isPrivate` entries whenever `flag != FLAG_LAUNCH_APP`. This
    has to sit at the consumer. `HomeFragment.showAppList` (lines 189-190) fetches before navigating. Every non-hidden
    flag observes the same retained `viewModel.appList` (`AppDrawerFragment` line 402). LiveData replays the last
    posted value to a new observer. So a list posted for the launch drawer while unlocked reaches the next picker
    (home slot, swipe, double-tap, clock/calendar, gesture letter) before any refetch. The hide and rename listeners
    (`AppDrawerFragment` lines 339/344) and the profile receiver also refetch with no flag context. `setAppList` is the
    only entry point for `appList` data into any drawer, so one check covers every flag. `FLAG_HIDDEN_APPS` reads
    `hiddenApps`, which never contains private apps, and the gate drops them there anyway.
  - **`appsList` holds real apps only** (regular plus private after the gate). The bottom-padding `AppModel` that
    `setAppList` appends today (line 172) moves out of `appsList`. Search and the usage sort therefore never see the
    padding row or the header.
  - **Decoration step.** A new `decorate(apps, searchBlank)` builds the submitted list in this order: regular rows,
    then the header (if shown), then private rows, then the padding row. The regular/private split is a stable
    partition (`sortedBy { it.isPrivate }`) applied after the usage sort. Padding is appended after the partition, so it
    always ends the list. `decorate` runs in both places that submit today: the unsorted branch of `setAppList` and
    `publishResults`. `appFilteredList` stores the decorated list, so `removeApp(position)` positions still match.
  - **Header row.** A sentinel `AppModel` (empty `appPackage`, `isPrivate = true`) with its own view type: label plus
    Lock/Unlock. It is never in `appsList`, so `performFiltering` never matches or sorts it. The empty package already
    stops the long-press menu (the `appPackage.isNotEmpty()` guard).
    - Shown only when `flag == FLAG_LAUNCH_APP`, API 35+, a private profile exists, and the search text is blank.
    - Hidden during any search. Unlocked private apps still match search and sort below regular matches.
    - Shown when the space is locked, even with zero private rows, so the Unlock affordance always renders.
  - **Enter and auto-launch** skip non-app rows. `launchFirstInList` and `autoLaunch` take the first entry with a
    non-empty `appPackage` instead of index 0. The header is never shown during search, so the guard is
    defence-in-depth. It also stops an empty list (padding only) from launching the padding row.
  - Private rows hide the "hide", "rename" and "time limit" menu entries. Rename and time limit key on package only,
    so they would also affect the main-profile copy and write a private package name into exported prefs.
- **`ui/AppDrawerFragment.kt`**: wire the header's click to `viewModel.setPrivateLocked`. No list or flag changes here.
- **`res/values/strings.xml`**: add `private_space`, `private_space_unlock` and `private_space_lock`, all
  `translatable="false"`.
- **Prefs**: none, and no new exported keys.
- **`CHANGELOG.md`**: add an Unreleased line.

## Done criteria

- On API 35+ with Parem as the default home, unlocked private apps appear only in the bottom section and launch.
- The header locks and unlocks the space.
- While locked: no private app in the drawer, omnibox results, auto-launch, hidden-apps list, or any picker. No private
  app on home slots, folders, swipe, gesture letters or double-tap. No private package in exported settings.
- Locking from outside Parem (system quick tile, auto-lock) updates an open drawer within one broadcast.
- API < 35 behaves exactly as today.
- `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all exit 0.

## Risks & traps

- **Trap #5 (package-change stamp).** Lock state is not a package change. Both broadcasts must invalidate the snapshot.
  Otherwise an unlock serves the empty locked-time query until the next install. This is also why `privateLocked` is
  applied per call and never cached.
- **The snapshot can hold private apps.** That is safe only because filtering happens per call in `rebuild`. Any future
  cache built on top of the rebuilt list must also key on lock state.
- **Trap #3 (flags as slot numbers).** Pickers share `viewModel.appList` with the launch drawer, so the
  `flag != FLAG_LAUNCH_APP` check in `AppDrawerAdapter.setAppList` is the only thing between a private app and a home
  slot. Do not move it into the ViewModel or the fetch call. A fetch-side flag would be defeated by LiveData replay.
- **Collisions after export/import.** A userString like `UserHandle{11}` imported from another device could match the
  private profile here. Two guards cover this: `getUserHandleFromString` skips private profiles, and
  `isPackageInstalledCached` ignores private snapshot entries.
- **Private userString falls back to the main profile, on purpose.** A slot holding a private userString launches the
  main-profile copy of that package if one exists, or is treated as uninstalled. This is intended. Do not "fix" it by
  matching private handles: that would launch locked-space apps from home.
- **Icon caches key on package alone** (`AppIconCache`, `IconPackManager`). Private copies would show the main copy's
  icon, without the system badge. That is acceptable but worth knowing.
- **Without `ROLE_HOME`** (for example during the `FakeHomeActivity` switch), the private profile just disappears from
  the list. That is not an error.

## Test plan

- **JVM** (`AppListRebuilderTest`): private entries are dropped when locked and when `includePrivate = false`; they
  ignore the hidden set and never appear in the hidden list; they sort last; non-private behaviour is unchanged.
  If `decorate` is pulled into a pure helper (e.g. `helper/DrawerRows`), test it too: padding always last; header
  between sections; header present with zero private rows; header absent while searching; no private rows when the
  flag is not `FLAG_LAUNCH_APP`.
- **Device** (Android 15 and 16, Parem as default home, private space set up):
  - unlock and lock from the header;
  - lock via the system tile while the drawer is open;
  - type a private app's name while locked;
  - **stale-replay check:** unlock the space, open the drawer for launch (private section visible), back out, then
    open each picker in turn (home slots 1-8, swipe left/right/up, clock, calendar, double-tap, gesture letter, folder
    create, focus whitelist). Confirm no private row appears in any of them, not even for one frame before a refetch.
    Repeat with the order "unlock from the header, then open a home-slot picker";
  - hide or rename a regular app while a picker is open, then confirm the refetch adds no private rows;
  - with the space locked, open the drawer: the header shows with Unlock and no private rows; Enter on an empty search
    launches the first regular app, never the header;
  - export settings and grep for the private package;
  - reboot while locked;
  - repeat on an Android 14 device as a regression check.

## Not verified

- Whether `UserManager.getUserProfiles()` already filters hidden profiles for callers without the permission (true in
  AOSP as far as I know). If it does not, Parem leaks today; device check needed.
- Whether `getActivityList` on a locked (stopped) private profile returns an empty list or throws.
- Whether `LauncherApps.Callback` fires on lock and unlock. The broadcast receiver covers this either way.
- The "hide entry point when locked" setting: `LauncherUserInfo.getUserConfig()` /
  `PRIVATE_SPACE_ENTRYPOINT_HIDDEN` is API 36, so honouring it waits for M2-WP1's compileSdk 36. How Android 15
  exposes the same setting is unverified.
- That Pixel Launcher also refuses private apps on home. This is my recollection, not something I confirmed.

Sources:
- [Android 15 behaviour changes: launcher apps](https://developer.android.com/about/versions/15/behavior-changes-all#private-space-launcher-apps)
- [Android 15 features: Private space](https://developer.android.com/about/versions/15/features)
- [AOSP Private space](https://source.android.com/docs/security/features/private-space)
- [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
- [LauncherUserInfo](https://developer.android.com/reference/android/content/pm/LauncherUserInfo)
- [UserManager](https://developer.android.com/reference/android/os/UserManager)
- [Manifest.permission.ACCESS_HIDDEN_PROFILES](https://developer.android.com/reference/android/Manifest.permission#ACCESS_HIDDEN_PROFILES)

## Review notes

- **The `includePrivate` parameter on `getAppsList` stays, with default `false`.** One review asked to remove it once
  the gate moves to the consumer. Without it, `getAppsList` would return private apps to every caller. The folder
  picker and the focus whitelist call it directly, not through the ViewModel, so each would need its own `isPrivate`
  filter, and any future direct caller would leak by default. Default-off at the source plus a flag gate at the
  drawer consumer is fail-closed on both paths. The ViewModel is the only caller that opts in.
- **Gate in `AppDrawerAdapter.setAppList`, not a second LiveData.** A separate `privateApps` LiveData would also work,
  but every refresh path would need to post two values, and the launch drawer would need to merge them. The adapter
  already knows `flag` (it uses it for auto-launch), so one line there is the smaller change.
- **Added beyond the review:** the "time limit" menu entry is also hidden on private rows (same package-keyed,
  exported-prefs reason as rename), and the LOC estimate went up to about 180 across 8 files (manifest, rebuilder, source, model, ViewModel, adapter, fragment, strings) for the decoration step
  and the cache-check guard.
