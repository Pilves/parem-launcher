# M4-WP7: Website shortcuts on home (design proposal)

Status: proposal, waiting for Patric's sign-off. Base: `origin/chore/agent-harness`. No code was changed and gradle was not run.

## Problem

There is currently no way to put a website on a Parem home slot. Parem has no shortcut code: grepping `app/src/main` for "shortcut" returns nothing, and the manifest has no `CONFIRM_PIN_SHORTCUT` activity. The roadmap's done criteria are:

1. the pinned site opens in the default browser or PWA;
2. it survives export/import.

Platform facts that shape the design:

- **Launchers cannot read a pinned shortcut's URL.** When a `ShortcutInfo` comes from `LauncherApps`, `getIntent()` and `getIntents()` "always return null" ([ShortcutInfo.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/ShortcutInfo.java)). A pinned shortcut can only be launched with `startShortcut(pkg, id, …)`, and only by the current default launcher ([LauncherApps.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/LauncherApps.java), `hasShortcutHostPermission`).
- **The system owns pinned shortcuts**, along with their backup and restore ([creating shortcuts](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts)). A Parem export holding `pkg/id/user` would point at nothing on another phone.
- **Installable PWAs usually don't need pinning.** Chrome installs them as WebAPKs. A WebAPK is a real package: it shows up in the app launcher and registers intent filters for its scope ([web.dev: WebAPKs](https://web.dev/articles/webapks)). That means it should already appear in Parem's drawer and fit in a slot today.

## Options

| | What | Cost | Tradeoffs |
|---|---|---|---|
| **A. Parem-owned URL** | The slot menu gets "Add website…", which opens a URL and label dialog. Parem stores the URL in Prefs and launches it with `ACTION_VIEW` | Small. One pure helper, one dialog, launch branch in `selectedApp` | Export/import works for free, because it's a plain String key. Opens in the default browser, or in a WebAPK that claims the URL. Not reachable from Chrome's "Add to Home screen" |
| **B. Accept pin requests** (upstream `02b2eed` + `14b89e9`) | A translucent `PinItemActivity` handles `ACTION_CONFIRM_PIN_SHORTCUT` and `accept()`s the request. Parem stores `pkg/shortcutId/user` and launches with `startShortcut` | Medium: an exported activity, a slot picker after accepting, a second storage kind, and a `LauncherApps.Callback` for removed shortcuts | Matches the "Add to Home screen" path in any app. **Fails done criterion 2**: shortcut IDs are device-local. It also stops working while Parem isn't the default launcher. Upstream's version only puts shortcuts in the drawer, so slot placement would be new code anyway |
| **C. A + B** | Both | A + B | Two storage kinds and two launch paths, for one feature |

## Recommendation

**Build A only.** It is the one option that meets both done criteria, and it is the smallest. The PWA part of the request is mostly already covered by WebAPKs as ordinary apps, and A's `ACTION_VIEW` hands in-scope URLs to an installed WebAPK.

B becomes a follow-up row only if the device pass or user reports show people expect Chrome's "Add to Home screen" to land in Parem (YAGNI).

**Folders:** `FolderApp` gains a `url` field, and `CreateFolderDialog` gets one "Add website…" row that reuses the same dialog. If Patric wants slots only for 6.0, this is the part to cut.

## Exact scope

- **New `helper/WebShortcut.kt`** (Android-free, `java.net.URI`):
  - `normalize(input): String?`: trims the input, adds `https://` when there is no scheme, accepts only `http`/`https` with a non-empty host, and returns null for `javascript:`, `intent:`, `file:`, blank or unparseable input.
  - `defaultLabel(url)`: the host with any leading `www.` removed.
- **`data/AppModel.kt`:** add `val url: String? = null` as the last parameter, with a default so existing call sites don't change.
- **`MainViewModel.selectedApp`:**
  - **`FLAG_LAUNCH_APP`:** if `url != null`, call `launchUrl(url)` and return. This branch goes *before* the existing `isAppAllowed(appModel.appPackage)` check, because a website's package is `""` and that check would block it during focus. `QUERY_ALL_PACKAGES` is already declared. `launchUrl`:
    1. builds `Intent(ACTION_VIEW, Uri.parse(url))` with `FLAG_ACTIVITY_NEW_TASK`;
    2. resolves it with `resolveActivity(intent, MATCH_DEFAULT_ONLY)`;
    3. **if focus is off**, starts the intent as-is (the system chooser is fine);
    4. **if focus is on and the result is a real handler**, checks `FocusModeManager.isAppAllowed(handlerPkg)`;
    5. **if focus is on and the result is the system resolver** (package `"android"`, which happens with no default browser or several equal handlers) or null, takes `queryIntentActivities(intent, MATCH_DEFAULT_ONLY)`, picks the first handler whose package `isAppAllowed`, and `setPackage(thatPkg)` on the intent. If none is whitelisted, it blocks with `app_blocked_focus`. Pinning the package matters: allowing the launch and showing the chooser would let the user pick a non-whitelisted browser.

    If `startActivity` throws, it shows `unable_to_open_link`.
  - **`FLAG_SET_HOME_APP_n`:** also `prefs.setHomeAppUrl(flag, appModel.url ?: "")`, so picking a normal app clears a website. This is the single place a website is written to a slot (see the dialog commit below).
- **`data/Prefs.kt`:** `getHomeAppUrl` / `setHomeAppUrl(slot)` on the key `APP_URL_$slot` (String). Export picks it up automatically. No `LONG_PREF_KEYS` entry and no exclusion are needed.
- **`ui/home/HomeSlotsController.kt`:**
  - **`populateHomeScreen`:** if the slot has a URL, set its text, call `setCompoundDrawablesRelative(null, null, null, null)`, and skip `setHomeAppText`. The installed-package check would otherwise blank the slot, because its package is `""`. Clearing the drawable is required: `setHomeAppText` (HomeSlotsController.kt:177-197) is what normally resets it, so with `showIcons` on, a slot that previously held an app would keep that app's stale icon next to the website label. (A globe icon for websites is out of scope.)
  - **`homeAppClicked` / `launchApp`:** pass the URL through.
  - **`showHomeSlotMenu`:** add an "Add website…" option, and hide the limit option for websites.
  - **Dialog commit:** the dialog's OK runs `WebShortcut.normalize`, shows `invalid_url` on null, and otherwise calls `viewModel.selectedApp(AppModel(label.ifBlank { WebShortcut.defaultLabel(normalized) }, null, "", null, false, Process.myUserHandle(), url = normalized), slot)`. That keeps the AGENTS rule that selection flows go through `selectedApp`, and `FLAG_SET_HOME_APP_n` writes name, package, user, activity and URL together. HomeSlotsController must not write the website prefs directly.
  - **Delete and `showCreateFolderDialog`:** also clear `APP_URL_$slot`.
  - **`toggleFolderExpansion`:** pass `app.url`.
- **New `ui/WebsiteDialog.kt`:** an AlertDialog with URL and label fields, matching `CreateFolderDialog`'s style.
- **`data/FolderData.kt` and `helper/FolderManager.kt`:** an optional `url` JSON field (default `""`), so old folders still parse.
- **`ui/CreateFolderDialog.kt`:** the "Add website…" row.
- **Strings** (`translatable="false"`): `add_website`, `website_url_hint`, `website_label_hint`, `invalid_url`.
- **Manifest:** no change.
- **`CHANGELOG.md`:** one line under `[Unreleased]`.

## Done criteria

- A slot set to `example.com` shows the label and opens `https://example.com` in the default browser.
- A URL inside an installed WebAPK's scope opens that app.
- Export, then clear data, then import: the website slot and folder entry come back and still open.
- Setting a normal app on the slot, deleting the slot, or making it a folder leaves no stale `APP_URL_n` behind.
- During focus, a website whose handler isn't whitelisted is blocked with `app_blocked_focus`.
- `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all exit 0.

## Risks & traps

- **Trap #3:** `FLAG_SET_HOME_APP_1..8` are literal slot numbers, and drawer rename writes `setHomeAppName(flag, …)`. The new URL setter has to use the same `flag`-as-slot indexing. Rename *does* reach websites: on a website slot, "Change app" opens the drawer with `flag = slot`, and the rename button (AppDrawerFragment.kt:450-452) writes only `setHomeAppName(flag, name)`. That relabels the website and keeps `APP_URL_n`. This is intended (it is the way to fix a label after the fact) and needs no code; it goes on the device pass.
- **Trap #5:** don't route websites through `isPackageInstalledCached`. They are not package-derived, so they need no stamp, but they must branch before that check.
- **Trap #1 (the lock view)** and **trap #2 (widget IDs):** not touched.
- **Import of old exports:** with no `APP_URL_n` in the file, the slot behaves exactly as it does today.
- **Android 12+ link handling:** an unverified WebAPK intent filter may lose to the browser, so the URL opens in a tab and not the PWA. See Not verified.
- **App limits are keyed by package.** Websites get none in this WP; a limit on the browser does not apply to them.

## Test plan

- **JVM (`app/src/test/.../helper/WebShortcutTest.kt`):**
  - scheme added
  - `http` kept
  - uppercase host
  - `javascript:`, `intent:`, `file:` and blank all rejected
  - IDN and port preserved
  - `defaultLabel` strips `www.`
- **Device:**
  - set, launch, delete and replace a website slot
  - website in a folder
  - old-folder parse: import (or keep from before the upgrade) a folder whose JSON has no `url` field; it loads and its apps still launch. This is a device item because `app/build.gradle` has no Robolectric and `org.json` is stubbed in JVM unit tests
  - rename a website slot via "Change app" → type name → rename; the label changes and the site still opens
  - with `showIcons` on, replace an app slot with a website; no stale app icon remains
  - export/import round trip
  - focus block, three cases: default browser whitelisted (opens); default browser not whitelisted (blocked); no default browser set with one of several browsers whitelisted (opens directly in the whitelisted one, no chooser), and with none whitelisted (blocked)
  - a WebAPK-installed PWA, such as a Twitter/X or Starbucks PWA
  - slot overflow and dynamic fitting with a website slot

## Not verified

- That an `ACTION_VIEW` launched from Parem opens an installed WebAPK rather than Chrome on Android 12+. The [web.dev article](https://web.dev/articles/webapks) says WebAPKs register scope intent filters, but not whether they are domain-verified.
- That every PWA a user cares about installs as a WebAPK. Non-Chrome browsers and sites that aren't installable fall back to a Chrome shortcut, which only B handles.
- Upstream's B code was read, not run.

## Review notes

- All six review items accepted; none disputed.
- One choice beyond what the review asked: in the focus-on resolver case, the design pins the intent to the whitelisted handler (`setPackage`) instead of only allowing it. If it only allowed the launch, the chooser would still offer non-whitelisted browsers.
- The relayed user request (plug in USB, one click for grayscale) belongs to M4-WP5. Nothing in this WP depends on it.
