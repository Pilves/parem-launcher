# Parem Launcher v6.0.0: data flows

Evidence behind `web/privacy.html`, and the table the Play Data safety form is checked against.

- **Audited:** `release/v6.0.0` at `0c95907` (`versionName v6.0.0-beta.2`, `versionCode 104`), 2026-11-05.
- **Updated:** 2026-11-05 for the fixes on `release/v6.0.0` through `1540c10` and the weather request clamp and 0.05° rounding. Line numbers in rows those fixes did not touch are from the audit commit and may have drifted.
- **Method:** source and resource reading, plus the live wallpaper list and Open-Meteo geocoder fetched from a desk machine. Nothing ran on a phone. Anything marked *platform default* is Android behaviour and was not observed as traffic.
- **Re-check before each release.** Section 8 has the commands.

Paths are relative to `app/src/main/java/com/parem/launcher/`, except `Manifest` (`app/src/main/AndroidManifest.xml`), `res/` (`app/src/main/res/`) and `fastlane/`, `docs/`, `web/` (repo root).

## 1. Flows that leave the phone

### 1a. Requests the app makes itself

Every row also exposes **connection metadata** to the host: IP address, time, TLS details, and a User-Agent naming the Android version, phone model and build ID (*platform default*, form `Dalvik/2.1.0 (Linux; U; Android <ver>; <model> Build/<id>)`). No code sets a request header (section 4). Nothing in this table goes to the publisher.

| ID | Host and request | Trigger | Default | What leaves, beyond metadata | Frequency | Evidence | Receiver and retention |
|---|---|---|---|---|---|---|---|
| N1 | `geocoding-api.open-meteo.com/v1/search` | Typing in the Weather sheet | Off until the user opens the sheet | Typed text, 2+ chars, debounced 300 ms | Each pause while typing | `helper/WeatherManager.kt:93`, `helper/WeatherManager.kt:97`, `helper/WeatherManager.kt:98`, `ui/settings/WeatherSettingsDialog.kt:114`, `ui/settings/WeatherSettingsDialog.kt:123` | OpenMeteo GmbH, Switzerland, independent controller. Logs may hold coordinates, deleted after 90 days ([terms](https://open-meteo.com/en/terms)) |
| N2 | `api.open-meteo.com/v1/forecast` | Home resume while weather is on | Off | Latitude of the picked place clamped to −84°…84°, then latitude and longitude rounded to the nearest 0.05° (`helper/WeatherPrivacy.kt`) | About hourly on success. After an exception, retry from 10 min. After a non-200 response, no backoff is written. The in-memory 30 min throttle resets with the process | `helper/WeatherManager.kt:175`, `helper/WeatherManager.kt:157`, `helper/WeatherManager.kt:182`, `helper/WeatherManager.kt:162`, `MainViewModel.kt:382`, `MainViewModel.kt:384`, `ui/HomeFragment.kt:103`, `ui/settings/WeatherSettingsDialog.kt:102` | As N1 |
| N3 | `www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml` | Drawer query shaped like `<amount> <code> [to/in] <code>`, both codes known, and saved rates older than 24 h | No switch; dormant until such a query | Nothing: fixed URL, typed text is not sent | At most one success per 24 h; 10 min backoff; one attempt per drawer view | `helper/CurrencyRates.kt:25`, `helper/CurrencyRates.kt:39`, `helper/CurrencyRates.kt:45`, `helper/CurrencyConverter.kt:62`, `ui/AppDrawerFragment.kt:300`, `ui/AppDrawerFragment.kt:347` | ECB, under Regulation (EU) 2018/1725. Logs max 10 months, personal data max 14 months; Myra Security GmbH sees IPs ([statement](https://www.ecb.europa.eu/services/data-protection/privacy-statements/html/ecb.privacy_statement_website.en.html)) |
| N4 | `pilves.github.io/parem-launcher/wallpapers.json` | WorkManager job while Daily wallpaper is on and Parem is default home | Off | Nothing: fixed URL | Every 8 h, needs network and battery not low | `data/Constants.kt:100`, `helper/WallpaperUtils.kt:148`, `helper/WallpaperWorker.kt:21`, `helper/WallpaperWorker.kt:25`, `MainViewModel.kt:359`, `MainViewModel.kt:360`, `MainViewModel.kt:362`, `ui/settings/AppearanceSettingsCard.kt:142` | GitHub, Inc. / GitHub B.V., as the Pages host. The list is `web/wallpapers.json`, owned by the publisher. It is 404 until Pages deploys from `master`, so N5's fallback applies until then |
| N5 | `images.unsplash.com` | As N4, when the list's URL for today differs from the last one; also the hard-coded fallback if N4 fails or the entry is blank | Off | The photo chosen reflects light or dark theme and, during the first 10 days, time since installation; afterwards it reflects the date. An image host could infer install age during that initial period | At most one image per list change | `helper/WallpaperWorker.kt:26`, `helper/WallpaperWorker.kt:30`, `helper/WallpaperUtils.kt:97`, `helper/WallpaperUtils.kt:165`, `helper/WallpaperUtils.kt:173`, `data/Constants.kt:101`, `data/Constants.kt:102` | Unsplash Inc. |
| N6 | Any host named in `web/wallpapers.json`. Tally of 765 URLs, plus one blank entry (`1_30` dark): `images.unsplash.com` 741, `i.redd.it` 10, `images.pexels.com` 8, `images2.imgbox.com` 6 | As N5 | Off | As N5 | As N5 | `helper/WallpaperUtils.kt:160`, `helper/WallpaperUtils.kt:49`, `helper/WallpaperUtils.kt:50` | Those four hosts, until the publisher edits the list. All 765 are HTTPS |

Weather location, exactly: the app has no location permission and calls no location API (section 4). The only source of coordinates is the user picking a named result in the Weather sheet (`ui/settings/WeatherSettingsDialog.kt:102`). They are stored as `Double.toString` of the geocoder's value, 5 decimals, for example Tallinn `59.43696, 24.75353`, and the sunrise theme reads them unchanged on the phone. For the forecast request, latitude is first clamped to −84°…84°, then both coordinates are rounded to the nearest 0.05° (Tallinn `59.45, 24.75`). A grid cell is about 16 km² at 59°N and about 3.2 km² at 84°. Places beyond 84° receive a forecast for the clamped latitude, rather than their actual latitude. Against the live API on 2026-11-05, six places from Sydney to Alert, Nunavut gave raw and rounded temperatures within 1.1 °C. Results include villages (Vana-Roosa, population 108) and airports, so a place is not always city-sized. The value sent is a rounded point, clamped at high latitudes, not the device's position.

### 1b. Hand-offs to other apps

The app opens no socket here. The user acts, and an Android intent passes the data to another app on the phone, which may then transmit it. All are user-initiated.

| ID | What | Trigger | Goes to | Evidence |
|---|---|---|---|---|
| H1 | Typed query | Leading space, then Enter | Default browser, `www.google.com/search?q=` | `ui/AppDrawerFragment.kt:252`, `data/Constants.kt:104` |
| H2 | Typed query | `!bang`, then Enter | Default browser, `duckduckgo.com/?q=` | `ui/AppDrawerFragment.kt:257`, `data/Constants.kt:103` |
| H3 | Typed query | Enter with no matching app | Whichever app handles `ACTION_WEB_SEARCH` | `ui/AppDrawerFragment.kt:260`, `helper/Extensions.kt:94`, `helper/Extensions.kt:95` |
| H4 | Website shortcut URL | Tap on a home slot or folder item | Browser | `ui/home/HomeSlotsController.kt:299`, `helper/FolderManager.kt:83` |
| H5 | About and GitHub project, privacy policy, wallpaper source URL | Tap on the settings row | Browser | `ui/settings/AppInfoSettingsCard.kt:100`, `ui/settings/AppearanceSettingsCard.kt:76` |
| H6 | Phone number, from the query or a contact match | Enter | Dialer (`ACTION_DIAL`, no call placed) | `ui/AppDrawerFragment.kt:248`, `ui/AppDrawerFragment.kt:251`, `ui/AppDrawerFragment.kt:665` |
| H7 | Alarm, timer or event details | Enter on a quick action | Clock or Calendar app | `helper/SystemActions.kt:104`, `helper/SystemActions.kt:108` |
| H8 | Crash report text | Send on the crash sheet | App the user picks; no preset recipient | `ui/CrashReportSheet.kt:22`, `ui/CrashReportSheet.kt:25`, `ui/CrashReportSheet.kt:26` |
| H9 | Crash report text | Copy on the crash sheet | Clipboard | `ui/CrashReportSheet.kt:29` |
| H10 | Grant page link, `adb` command | Share or Copy on the grayscale sheet | App the user picks, or clipboard | `ui/GrayscaleSheet.kt:102`, `ui/GrayscaleSheet.kt:106`, `ui/GrayscaleSheet.kt:110` |
| H11 | Settings export file | User moves or syncs it | Wherever the user puts it | Section 3 |

### 1c. Off-device flows the app does not perform

| ID | Flow | Controls | What leaves | Evidence |
|---|---|---|---|---|
| S1 | **Android Auto Backup** to the user's Google Drive | Device backup setting | Nothing Parem stores: all three prefs files are excluded from cloud backup, and the `<include>` keeps every other domain out. A backup made by an earlier version stays until Android replaces it | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `data/BackupRulesTest.kt` |
| S2 | **Device-to-device transfer** | User, when moving phones, Android 12+ | Main prefs file (section 3), including `CACHED_USAGE_STATS`, `OPEN_COUNTS`, weather coordinates, app choices, website URLs, folders. Android 10 and 11 read the legacy rules and transfer nothing | `res/xml/data_extraction_rules.xml`, `ParemBackupAgent.kt:15` |
| S3 | **Android vitals** to Google, for crashes and ANRs | Device-level usage and diagnostics sharing; Play installs only | Crash data, independent of Parem's own setting. The handler deliberately passes crashes on | `helper/CrashReporter.kt:28`, `helper/CrashReporter.kt:29`, `helper/CrashReporter.kt:42`, `helper/CrashReporter.kt:43` |
| S4 | **GitHub Pages** logs for `pilves.github.io/parem-launcher/` | Visitor opens the page | IP and browser details to GitHub. The grant page's CSP blocks page-initiated connections | `res/values/strings.xml:337`, `web/grant/index.html:6`, `.github/workflows/pages.yml:33` |
| S5 | **Support mail** to the publisher | User writes | Whatever the user includes | Policy sections 4 and 7 |

Device transfer (S2) and settings import also bring back on/off flags, because only `Prefs.deviceOnlyKeys` are cleared (`data/Prefs.kt:512`) and export leaves out none of the switches below (`data/Prefs.kt:522`). So `WEATHER_ENABLED`, `DAILY_WALLPAPER`, `CRASH_REPORTS_ENABLED` and `OMNIBOX_HISTORY_ENABLED` return as saved:

- **Weather:** N2 resumes on the new phone with no action there (`helper/WeatherManager.kt:45`, `ui/HomeFragment.kt:103`). A place that arrives with weather off is erased at the next home resume, unless the sunrise theme uses it (`MainViewModel.getWeather`).
- **Daily wallpaper:** the flag returns, but the WorkManager job does not, because backup covers shared preferences only. It restarts on a theme change (`MainActivity.kt:277`).
- **Contact search:** inert without the permission (`helper/ContactSearchManager.kt:45`).
- **Notification filter:** does not return, because its state is the component, not a pref (`helper/notifications/QuietNotificationsManager.kt:43`).

### 1d. Local network

| ID | Flow | Trigger | What | Evidence |
|---|---|---|---|---|
| L1 | mDNS discovery of `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` | `full` build only: user enters the grayscale pairing code | Service-type queries on the LAN. Only a result matching the phone's own address is used, and the connection is to `127.0.0.1` | `helper/adb/WirelessAdb.kt:49`, `helper/adb/WirelessAdb.kt:150`, `helper/adb/WirelessAdb.kt:168`, `helper/GrayscalePairing.kt:111`, `helper/GrayscalePairing.kt:123` |

## 2. Data handled on the phone

| Data | Source and gate | Held where | Sent by the app? | Evidence |
|---|---|---|---|---|
| Installed apps: labels, packages, work profile, unlocked Private Space | `LauncherApps`; manifest `<queries>`; `ACCESS_HIDDEN_PROFILES` | Memory. User choices in main prefs | No | `helper/AppListSource.kt:102`, `Manifest:18`, `Manifest:32` |
| App shortcuts published by apps: package, ID and a label the other app chose, which can hold a contact or chat name | `LauncherApps.getShortcuts`; no opt-in | Memory. History stores only `package\|user` (`ui/AppDrawerFragment.kt:399`) | No | `helper/AppListSource.kt:176` |
| Contact names and first number, max 2,000 | Opt-in toggle plus `READ_CONTACTS`; no query unless both | Drawer memory only; nothing written | No. Number can go to dialer (H6). Calls and contact matches stay out of history | `helper/ContactSearchManager.kt:52`, `helper/ContactSearchManager.kt:56`, `helper/ContactSearchManager.kt:26`, `ui/settings/HomeScreenSettingsCard.kt:279`, `ui/AppDrawerFragment.kt:87`, `ui/AppDrawerFragment.kt:162`, `ui/AppDrawerFragment.kt:236` |
| Per-app foreground time | `PACKAGE_USAGE_STATS`, granted in Android settings | Today's map in main prefs, otherwise memory | No, but see S2 | `helper/UsageStatsHelper.kt:36`, `helper/usageStats/EventLogWrapper.kt:56`, `ui/AppDrawerFragment.kt:548`, `data/Prefs.kt:280` |
| Foreground app, for per-app grayscale | Same permission, polled while a marked app runs | Session state; marked apps in main prefs | No | `helper/GrayscaleController.kt:245` |
| Open counts per package, today | Always on; no permission; skips Private Space | Main prefs | No, but see S2 | `helper/AppOpenCounter.kt:27`, `helper/AppOpenCounter.kt:29`, `MainViewModel.kt:300` |
| Notifications: package, category, flags, media flag, title | Listener component disabled by default; user grants notification access | Title read only to display it. Body is never read by Parem code. Android holds the snoozed notification | No | `Manifest:146`, `Manifest:147`, `helper/notifications/QuietNotificationListener.kt:64`, `helper/notifications/QuietNotificationListener.kt:76`, `helper/notifications/QuietNotificationListener.kt:84`, `ui/QuietListSheet.kt:192`, `helper/notifications/QuietFilter.kt:11` |
| Notification keys (package, ID, optional app-chosen tag) | Same | `quiet_keys` file. No `onNotificationRemoved`: pruned on listener connect and when the quiet list is built, so turning the filter off with "keep" leaves keys until it is turned on again | No; excluded from backup and export | `helper/notifications/QuietNotificationsManager.kt:37`, `helper/notifications/QuietNotificationsManager.kt:109`, `helper/notifications/QuietNotificationListener.kt:65`, `helper/notifications/QuietNotificationListener.kt:39`, `helper/notifications/QuietNotificationListener.kt:121`, `helper/notifications/QuietNotificationsManager.kt:70`, `res/xml/backup_rules.xml:5` |
| Allowed notification apps | User picks | Main prefs | No, but see S2; exported | `helper/notifications/QuietNotificationsManager.kt:34`, `helper/notifications/QuietNotificationsManager.kt:87` |
| Omnibox text | Typed | Memory | Only by H1 to H3 | `ui/AppDrawerFragment.kt:298` |
| Search history, max 5 | Opt-in, default off | `omnibox_history` file | No; excluded from backup and export; wiped when turned off | `data/Prefs.kt:136`, `data/Prefs.kt:139`, `data/Prefs.kt:102`, `helper/OmniboxHistory.kt:11`, `res/xml/backup_rules.xml:7` |
| Crash report: stack trace with exception message, version, API level, maker, model, thread, time, plus a tag for non-fatals. Capped at 64 KB | Opt-in, default off | `noBackupFilesDir/crash.txt` | Only by H8 or H9. Any exit from the sheet deletes it, as does turning the setting off | `data/Prefs.kt:131`, `helper/CrashReporter.kt:21`, `helper/CrashReporter.kt:31`, `helper/CrashReporter.kt:59`, `helper/CrashReportFormatter.kt:27`, `helper/CrashReportFormatter.kt:33`, `ui/CrashReportSheet.kt:33`, `ui/settings/AppInfoSettingsCard.kt:109`, `MainActivity.kt:147` |
| Weather place, coordinates, last temperature | User picks | Main prefs. Erased with the cached temperature when weather is off, unless the sunrise theme uses it | N1, N2 | `ui/settings/WeatherSettingsDialog.kt:102`, `ui/settings/WeatherSettingsDialog.kt:103`, `ui/settings/AppearanceSettingsCard.kt:94`, `helper/WeatherManager.kt:173` |
| Sunrise and sunset times | Computed from the saved coordinates | Memory | No | `helper/ThemeScheduleManager.kt:80`, `helper/ThemeScheduleManager.kt:92` |
| Accessibility events | Own package only; no window content; click events only | Nothing | No | `res/xml/accessibility_service_config.xml:3`, `res/xml/accessibility_service_config.xml:8`, `helper/MyAccessibilityService.kt:28` |
| Device admin | `force-lock` policy only | Nothing | No | `res/xml/policies.xml:4` |
| Pairing code | `full` build only: typed into a notification reply | Local variable; ephemeral RSA key in memory | Loopback only | `helper/GrayscalePairing.kt:111`, `helper/adb/WirelessAdb.kt:53` |
| Display colour setting | `WRITE_SECURE_SETTINGS`, granted through adb | Android settings; previous state in main prefs (device-only keys) | No | `helper/GrayscaleController.kt:152`, `data/Prefs.kt:516` |
| Battery level, torch state | Read for display | Memory | No | `ui/home/HomeClockController.kt:101`, `ui/home/HomeGesturesController.kt:75` |
| Logcat lines, can include package names | Not stripped in release | Android's device log | No | `MainViewModel.kt:267`, `helper/usageStats/EventLogWrapper.kt:216`, `app/proguard-rules.pro:1` |

## 3. Storage matrix

Cloud backup excludes all three prefs files. Device transfer, on Android 12+, carries the main file only. Android 10 and 11 read `backup_rules.xml`, which excludes all three for both. Every section keeps its shared-preferences `<include>`, because an `<include>` turns off the default of backing up everything.

| Store | Holds | Backup and transfer | Settings export |
|---|---|---|---|
| `com.parem.launcher.xml` (main prefs) | All settings, usage cache, open counts, weather, rates cache | Cloud: **excluded**. Transfer: included on Android 12+ | Yes, minus widgets, usage cache, open counts, cached weather and rates, onboarding, device-only keys (`data/Prefs.kt:522`) |
| `com.parem.launcher.omnibox_history.xml` | Search history | Excluded (`res/xml/backup_rules.xml:7`, `res/xml/data_extraction_rules.xml:8`) | No: separate file (`data/Prefs.kt:100`) |
| `com.parem.launcher.quiet_keys.xml` | Notification keys | Excluded (`res/xml/backup_rules.xml:5`, `res/xml/data_extraction_rules.xml:6`) | No: separate file |
| `noBackupFilesDir/crash.txt` | Crash report | Excluded by Android | No |
| `Downloads/parem_settings.json` | Export, plain JSON, includes weather coordinates and city | Not app data. Survives uninstall and Clear storage | Created by Export (`ui/settings/AppInfoSettingsCard.kt:148`, `ui/settings/AppInfoSettingsCard.kt:153`) |

After a device transfer, device-moment keys are cleared (`ParemBackupAgent.kt:21`, `data/Prefs.kt:512`). Import reads a user-picked document only (`ui/settings/AppInfoSettingsCard.kt:168`, `ui/settings/AppInfoSettingsCard.kt:187`).

## 4. Confirmed absent

Zero hits in `app/src/main`, unless noted:

- **Location:** no `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `LocationManager` or `FusedLocation`. `latitude` appears only in `WeatherManager.kt` and `WeatherSettingsDialog.kt`, both on the geocoder path.
- **WebView:** no `WebView` or `android.webkit`.
- **SDKs:** no Firebase, Crashlytics, Sentry, ACRA, analytics, ads or `play-services`. Dependencies are AndroidX and Material only (`app/build.gradle:86`).
- **Other HTTP clients:** no OkHttp, Retrofit, Ktor or Volley used directly. All requests go through `HttpURLConnection` in three files.
- **Headers:** no `setRequestProperty` or `User-Agent` override.
- **Cleartext:** no `usesCleartextTraffic` or `networkSecurityConfig`, so targetSdk 36 blocks HTTP (*platform default*).
- **Identifiers:** no `ANDROID_ID`, advertising ID, `TelephonyManager` or `getDeviceId`.
- **Other readers:** no `READ_CALENDAR` (calendar use is intents only), no `MediaStore` reads, no `getPrimaryClip`. The contacts read is the only `ContentResolver.query` in the app.

The merged manifest was not inspected, because no build output exists here. WorkManager normally adds `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. Check the final bundle in Play Console.

## 5. Conflicts with existing claims

| Claim | Where | Finding | Status |
|---|---|---|---|
| ECB URL "carrying no user data" and "one daily anonymous download" | `docs/ROADMAP.md:89`, `docs/design/M3-WP4.md:52` | URL is fixed and carries no query, but the IP and User-Agent reach the ECB. "Anonymous" is too strong | Policy words it accurately |
| Crash reports "sent only by the user" | `docs/ROADMAP.md:97` | True of Parem's report. Crashes still reach Android vitals (S3) | Policy discloses it |
| Roadmap destination list | `docs/ROADMAP.md:89` | Omits N4 to N6, H1 to H3, S1 and S3 | Policy lists them |
| "Nothing is collected or sent anywhere. Everything is counted on your device." | `res/values/strings.xml:97`, `res/values/strings.xml:98`, `res/values/strings.xml:390` | Reworded to say what happens, and these counts left cloud backup (S1) | Fixed |
| "No icons, no ads, no data collection." | `fastlane/metadata/android/en-US/full_description.txt:1` | Weather sends a location to a third party, and Play defines collection as sending data off the device | Fixed: the listing no longer says it |
| "Nothing is stored or sent anywhere." | `res/values/strings.xml:317` | Keys and the allowlist are stored. No notification content is | Fixed |
| Crash report holds "No apps, contacts or settings." | `res/values/strings.xml:79` | No lists are included, but free-form exception text can name a package or URL (`helper/CrashReportFormatter.kt:33`). 43 `reportNonFatal` sites | Fixed: the string says so |
| INTERNET "Used only for downloading wallpapers" | `Manifest:8` | Weather and currency use it too. `docs/design/M4-WP15.md:163` says disclosures are written from these comments | Fixed |
| "Daily wallpapers … downloaded from Unsplash" | `docs/fdroid/com.parem.launcher.yml:17`, `docs/fdroid/com.parem.launcher.yml:18` | Also Reddit, Pexels and imgbox (N6) | Fixed |
| "Open counts are exported" | `helper/notifications/QuietNotificationListener.kt:136` | Excluded from export (`data/Prefs.kt:525`) and from cloud backup; device transfer can carry them | Stale comment |

Confirmed as described: READ_CONTACTS is opt-in, read on the device and never transmitted; ECB fires only after a currency query; crash reports are opt-in, kept on the phone and user-sent; the listener stays unbound until enabled; notification text is not stored.

## 6. Data safety mapping (proposal)

`docs/play/compliance.md` owns the final answers. Definitions are from [Play's Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469), which says collection means transmitting data off the device, and on-device-only processing need not be disclosed.

| Form item | Flows | Proposed answer | Reason |
|---|---|---|---|
| Location: Approximate | N1, N2 | **Collected, optional, App functionality. Not ephemeral.** Shared: yes, as the safe answer | Leaves the device, and Open-Meteo keeps logs 90 days. It is an independent controller, so the service-provider exception does not fit. The "user-initiated action" exception is arguable for picking a city, weaker for the hourly refresh |
| Location: Precise | N2 | No | Play defines precise as under 3 km². Forecast latitude is clamped to −84°…84° before 0.05° rounding; a cell is about 16 km² at 59°N and about 3.2 km² at 84° |
| Contacts | H6 only | Not collected, not shared | On-device. The dialer hand-off is user-initiated |
| Messages, notification content | None | Not collected | On-device |
| App activity: Installed apps, App interactions | S2 only | Not collected | Parem sends nothing and this data left cloud backup. A device transfer goes only to the user's own new phone |
| App activity: In-app search history | H1 to H3 | Not shared | User-initiated, to a browser. The help page says open-web browsing need not be declared for a webview; the same logic applies but is not stated for intents |
| App info: Crash logs | H8, S3 | Not collected | User-initiated through an app they pick. S3 is Google's, not app code. Confirm |
| Device or other IDs | Metadata | No | User-Agent names a model, not a device. IP is unused by the app. Play leaves IP to "particular usage and practices" |
| N3 to N6 | Metadata only | No declaration, policy discloses | Same open call as `docs/design/M3-WP4.md:125`, Patric's decision |
| Encrypted in transit | N1 to N6 | Yes | All HTTPS; cleartext blocked |
| Deletion | S5 | No account. Requests by email | Policy "How to delete your data". The publisher cannot delete third-party logs, and the policy says so |

## 7. Gaps and suggested changes

Status after the 2026-11-05 fixes. Still open: G2's wallpaper half, G4's single host, G9's confirmation in `compliance.md`, and G11 to G14.

| ID | Gap | Suggested fix | Cost |
|---|---|---|---|
| G1 | Usage cache and open counts go to Google backup, against the in-app claim | **Done differently:** every prefs file is excluded from cloud backup, and the string is reworded | Small |
| G2 | Weather sheet has no in-app disclosure. Play requires one where data is sent, and the policy's consent basis depends on it. Same for Daily wallpaper | **Done for weather.** Daily wallpaper still has none | Small |
| G3 | Contacts toggle jumps straight to the system prompt. Play says runtime requests must be immediately preceded by an in-app disclosure, where one is required. On-device use may not need it; compliance to decide | **Done:** a sheet before the prompt | Small |
| G4 | The wallpaper list is controlled by a third party, so hosts can change with no release and the policy can never be exhaustive | **Done:** the publisher hosts the list in `web/wallpapers.json`. Limiting it to one image host is still open | Medium |
| G5 | Weather coordinates survive turning weather off, with no in-app removal | **Done:** cleared on turn-off and at home resume, unless the sunrise theme needs them | Small |
| G6 | Coordinates were unrounded | **Clamped and rounded:** forecast latitude is clamped to −84°…84° before both coordinates are rounded to 0.05°. At 84° a cell is about 3.2 km². The geocoder still receives the typed place query; review the final Console declaration against all data flows | Trivial |
| G7 | Listing says "no data collection" | **Done** | Trivial |
| G8 | Backups run without end-to-end encryption on phones with no screen lock | **Moot:** nothing Parem stores reaches cloud backup | Trivial |
| G9 | Open-Meteo's free API is for non-commercial use: "private or non-profit" apps without subscriptions or ads | Confirm donations fit | Decision |
| G10 | The privacy row is hidden (`data/Constants.kt:98`). Play also requires a link or text inside the app | **Done** (M2-WP3). The page is live only after G11 | Small |
| G11 | Pages deploys on merge to `master` (`.github/workflows/pages.yml:7`), so the URL is not live before that. Merging unfilled would publish literal placeholders | Fill the three placeholders first: replace all occurrences in `web/privacy.html` | Trivial |
| G12 | The policy is English only | Add an Estonian version if the app is marketed in Estonia | Decision |
| G13 | A restored phone or imported file restarts weather requests with no action on that phone. The policy discloses this, but it weakens per-device consent | Add `WEATHER_ENABLED` and `DAILY_WALLPAPER` to `Prefs.deviceOnlyKeys`, which also drops them from export, so the user turns them on again | Trivial |
| G14 | Notification keys outlive the filter when it is turned off with "keep" | Clear them on the next start once the filter is off and 8 h have passed | Small |

Publisher commitments in the policy that need Patric's agreement: reply within one month, delete emails and reports once dealt with, use crash data only to fix bugs, update the page before any release that changes data handling.

The legal bases in policy section 6 are a reasoned choice, not legal advice. Weakest spot: consent for daily wallpaper rests on the policy until G2's wallpaper half lands.

## 8. How to re-check

```sh
# URLs and hosts
grep -rnE 'https?://' app/src/main --include='*.kt' --include='*.xml' | grep -v schemas.android
# HTTP entry points and headers
grep -rnE 'openConnection|setRequestProperty|Socket\(' app/src/main --include='*.kt'
# SDKs, WebView, location
grep -rnE 'WebView|webkit|Firebase|Sentry|play-services|LocationManager|ACCESS_.*LOCATION' app/src/main
# Wallpaper hosts (N6): tally the URL hosts in web/wallpapers.json
```
