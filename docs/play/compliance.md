# Parem Launcher v6.0.0: Google Play compliance audit

**Historical audit, not a current release baseline.** Final-head source and both merged manifests must be rechecked before using section 8. The following 2026-11-05 findings may be stale. Code baseline: `release/v6.0.0` at `217b303`. App code there is identical to tag `v6.0.0-beta.2` (`0c95907`, versionCode 104, targetSdk 36): later commits changed only CI, docs and the fastlane listing. `web/privacy.html` was an uncommitted draft when read at about 13:10 that day. Policy baseline: Developer Program Policy "effective October 29, 2026". I also decoded the manifest of the published `v6.0.0-beta.2` APK, so the library-merged entries below are what actually ships.

Code paths are relative to `app/src/main/java/com/parem/launcher/`. `strings.xml` means `app/src/main/res/values/strings.xml`, and `Manifest` means `app/src/main/AndroidManifest.xml`.

## Verdict

**Not ready to submit.** The app itself is close. Three things stop it outright, and one of them, the closed test, cannot start until the store listing is complete. The biggest open decision is the in-app adb pairing, because its downside is an account-level malware finding and not just a rejection.

| Rating | Count | Items |
|---|---:|---|
| BLOCKER | 3 | Privacy policy not live or in the app. Store graphics are Olauncher's. 12-tester closed test for personal accounts |
| LIKELY-REJECTION | 3 | In-app adb self-pairing. READ_CONTACTS prompt with no disclosure. Grayscale grant page returns 404 |
| DECLARATION-NEEDED | 8 | Accessibility, notification listener, usage access, WRITE_SECURE_SETTINGS, device admin, Open-Meteo credit, wallpaper feed, trader status |
| OK | 15 | Listed in section 4 |

What the ratings mean here:

- **BLOCKER:** Play will not accept it, or a published rule is unambiguous and the current state breaks it.
- **LIKELY-REJECTION:** Play will accept the upload, but a reviewer or scanner applying the published text would probably reject it or act on it later.
- **DECLARATION-NEEDED:** Allowed, and passes once a form, a listing line, a disclosure edit or a privacy-policy line exists.
- **OK:** Nothing to do for this submission.

The brand line and coffee link in section 2 are not in the build, so they are not counted.

---

## 1. Blockers

### B1. Privacy policy is not live and not in the app

- **Evidence:** `app/src/main/java/com/parem/launcher/data/Constants.kt:98` is `URL_PAREM_PRIVACY = ""`, so `ui/settings/AppInfoSettingsCard.kt:58` hides the row. `web/privacy.html` is a good draft: all required parts are there and its placeholders are now filled. It is uncommitted, and `https://pilves.github.io/parem-launcher/privacy.html` returned 404 today. M2-WP3 is still open (`docs/ROADMAP.md:89`).
- **Policy:** "All apps must post a privacy policy link in the designated field within Play Console, and a privacy policy link or text within the app itself." The target audience form cannot be completed without one. [Developer Program Policy](https://support.google.com/googleplay/android-developer/answer/18265346), [App content](https://support.google.com/googleplay/android-developer/answer/9859455)
- **Fix:**
  1. Commit `web/privacy.html` and get it onto `master`. Pages only deploys from there, see L3.
  2. Set `URL_PAREM_PRIVACY` to `https://pilves.github.io/parem-launcher/privacy.html`.
  3. Google Play's User Data policy asks the privacy policy to name the entity in the store listing or app and provide a privacy contact. The draft names Parem Launcher, Patrick, and developer name CHAIDLA, and gives info@chaidla.ee as the contact.

### B2. Every store graphic is Olauncher's

- **Evidence:** `fastlane/metadata/android/en-US/images/featureGraphic.jpg` reads "Olauncher - Minimal AF" and is 512×250. All seven `phoneScreenshots/*.jpg` are Olauncher marketing frames at 368×736: "Minimal AF Launcher / AF stands for Ad-Free :D", "Daily new wallpaper", and "Zero data collection / No login, no ads, no trackers" over a Guy Fawkes mask wallpaper. None shows Parem's current UI. They arrived in `39d6dab` (2025-07-30), before the rebrand. The icon is Parem's own `>_` and is fine; Olauncher's is a green ring.
- **Policy:**
  - Impersonation: "Don't imply that your app is related to or authorized by someone that it isn't."
  - Deceptive Behavior: "Apps must provide an accurate disclosure, description and images/video of their functionality in all parts of the metadata."
  - [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151): feature graphic "Dimensions: 1024px by 500px". The current file cannot be used at all.
- **Fix:** Replace all eight files, following the shot list in `docs/play/listing.md`. F-Droid and IzzyOnDroid read the same directory, so this fixes those too. Treat any "no data" caption as a claim that needs the section 7 answer behind it.

### B3. The closed test gate

- **Evidence:** `docs/ROADMAP.md:235` lists this as an owner task. I cannot see its status.
- **Policy:** Personal accounts created after November 13, 2023 "must run a closed test for their app with a minimum of 12 testers who have been opted in continuously for at least 14 days" before production. Production stays disabled until then, and "You can start a closed test after completing your app setup." [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- **Fix:** Treat B1, B2 and the section 8 forms as prerequisites, because they all sit inside app setup. The 14-day clock starts only after they are done. New accounts also declare trader status at creation (section 2).

---

## 2. Branding rulings

### Brand line: "no bullshit minimal launcher"

**Your treatment is confirmed, and the basis is stronger than the best-practices page.** The ban is in binding policy:

- [Metadata](https://support.google.com/googleplay/android-developer/answer/9898842) lists as inappropriate: "Using profane, vulgar, or other language that is inappropriate for a general audience in your app's Store listing." Its summary says metadata must be "suitable for all audiences".
- [Sexual Content and Profanity](https://support.google.com/googleplay/android-developer/answer/9878810): "We don't allow apps that contain or promote sexual content or profanity", with the example "profanity, slurs, explicit text, or adult/sexual keywords in the store listing or in-app."
- [Best practices](https://support.google.com/googleplay/android-developer/answer/13393723) repeats "Avoid profane or vulgar language" for title, description, screenshots and "Feature graphic, icon, and video", and says the policy applies "to all translations".

The policy names no words and defines no threshold. That "bullshit" counts as vulgar for a general audience is my reading, and I would expect every reviewer to share it.

| Surface | "bullshit" | "No BS" |
|---|---|---|
| Title, short and full description, What's new, all translations | No | No |
| Icon, feature graphic, screenshots, promo video | No | No |
| Developer name | No | No |
| Landing page of the listed developer website | No | No |
| Any page the app opens, including the GitHub README | No | No |
| Other website pages, blog posts, social media | Fine | Fine |
| Inside the app | No | No |

**"No BS": my ruling is no, but the text does not settle it.** Abbreviations are not addressed. The nearest precedent is Olauncher, live with the title "Olauncher. Minimal AF Launcher" (1M+ installs, updated Sep 2, 2026). That listing gives "AF" an innocent meaning ("AF stands for AdFree"), and "BS" has none. It stands for the same word, so it fails the "general audience" test in the same way, and swapping it costs one word. If Patric insists, the only place worth trying is the full description, and not in the first submission. A rejection does not hurt account standing, but "repeated app rejections" can lead to suspension.

**Linked website: yes, it can cause a problem, depending on the page.** [Policy Coverage](https://support.google.com/googleplay/android-developer/answer/18265346) says policies "apply to any content your app displays or links to" and to "the landing page of your listed developer website." So the landing page and every page the app opens are in scope. The app's GitHub row opens the repository (`data/Constants.kt:99`), which makes the README in scope too. A deeper page that is neither of those is outside the text.

**Inside the app: no.** The profanity policy says "in-app" explicitly. The word would also change the content rating, since you would have to answer the language question truthfully: PEGI 3 requires that "No bad language should be heard" and IARC 3+ says "Bad language is not permitted". [Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655)

**What I would use:** "No-nonsense minimal launcher", which is already in `short_description.txt`. It carries the same meaning with no policy exposure. The current title and the de and et lines are clean.

### Coffee link to buymeacoffee.com

No such code exists today (`.github/funding.yml` is comment-only, and no donate string is in the app).

| Placement | Ruling |
|---|---|
| (a) Link inside the Play build | **Not safe. Do not ship it.** |
| (b) Link in the Play listing text | **Not safe. Do not add it.** |
| (c) Only on the website | **Safe, if laid out as below.** |

**The rule, from the [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738):**

- Section 2 requires Play billing for payment "for access to in-app features or services". A tip that unlocks nothing is not caught here.
- Section 4 is the one that bites: "Other than the conditions described in Section 3, Section 8, and Section 9, apps may not lead users to a payment method other than Google Play's billing system." It names "An app's listing in Google Play" and "In-app webviews, buttons, links, messaging, advertisements, or other calls to action". A small link is still a link.

So (a) and (b) need an exception, and none fits:

- **Section 3, tax-exempt donations:** Parem is not a charity.
- **Section 3, peer-to-peer:** the [Payments FAQ](https://support.google.com/googleplay/android-developer/answer/10281818) treats a tip as peer-to-peer only "In cases where 100% of the tip or contribution from a user goes to the creator and the payment does not grant access to any digital content or services". It continues: "If any of these things is not true, then Google Play's billing system must be used".
  - Buy Me a Coffee [keeps 5% and Stripe takes 2.9% + $0.30 on top](https://help.buymeacoffee.com/en/articles/8105744-how-to-calculate-charges-on-your-payment), so the 100% test fails on its face.
  - Even a zero-fee platform has processor costs, and nothing says an app's own developer counts as the "creator".
- **Sections 8 and 9, the programmes:** these do not fit a personal developer.
  - The [EEA external offers program](https://support.google.com/googleplay/android-developer/answer/14372887) says "Developer must be registered as a business."
  - The [US external content links program](https://support.google.com/googleplay/android-developer/answer/16470497) is limited to US users. It requires enrolment, API integration, an information screen, refund and support processes, and fee reporting.

The FAQ also says the block extends to "directly linking to a webpage that could lead to an alternate payment method". Even administrative pages such as a privacy policy are allowed only "as long as the webpage does not eventually lead to an alternate payment method prohibited by the Payments policy."

**Why (c) is safe:** Section 4 restricts "apps" and "An app's listing", and a separate website is neither. The FAQ adds: "Outside of your app, you are free to communicate with your users about alternative purchase options." That sentence is about purchases, and no page addresses tips on a website, so applying it to a tip is my inference. It holds only with these conditions:

1. **Not on the landing page of the listed website.** Policy Coverage names that page, and the text does not say whether listing, then website, then payment counts as leading users "via" the listing. Put it on a subpage such as `/support/`, one step in from the landing page. This is my margin of safety and is not stated in the text.
2. **Not reachable from anything the app opens.** That means `privacy.html`, any About page and the GitHub README stay free of the link and of site navigation leading to it. `web/privacy.html` is clean today. Keep `.github/funding.yml` empty, because a populated one adds a Sponsor button to the page the app opens.
3. **Tips unlock nothing.** No perks, badges or memberships. This also keeps Buy Me a Coffee's membership and shop features switched off.
4. **Off every Play surface.** That includes What's new, screenshots and the promo video.

**What I would do:** (c) only, for 6.0. The GitHub and F-Droid builds can carry an in-app link, since the Payments rules bind "Play-distributed apps". If Patric later wants an in-app tip on Play, the compliant route is a Play Billing one-time product in a Play-only build. That needs a merchant account, and F-Droid will not accept the dependency.

**Effect on declared answers, under (c):**

- **Earning money "No": stays accurate, with one check.** I found no Google page defining this question, and I cannot see it without a Console login. Google's usage is "monetize on Google Play", tied to a merchant account and Play billing, and website tips use neither. Read the actual wording on screen: if it asks about income from the app by any route, answer what it asks. Staying non-monetized also keeps your full address off the listing ("If you monetize on Google Play, Google displays your full address").
- **Trader status non-trader: a link alone does not change it, but it is now a factor to watch.** [Google's test](https://support.google.com/googleplay/android-developer/answer/17594752) says hobby activity is "unlikely" to be a trader, and lists a "Profit-seeking motive: you receive or intend to receive remuneration for your apps ... on a regular or commercial basis." Occasional voluntary tips with no perks fit a hobby. Regular income, memberships, or running this through CHAIDLA would push the other way. Google says it "cannot evaluate your business activities", and a false non-trader claim risks global removal. If tips become regular, re-assess and update the declaration. This is not legal advice.
- **Unchanged:** Data safety, Ads "No", Financial features "none". [Open-Meteo's terms](https://open-meteo.com/en/terms) also allow it: "Voluntary donations, including contributions through a tip jar, are permitted."

---

## 3. Likely rejections

### L1. In-app adb self-pairing

**Rating: LIKELY-REJECTION. Recommended action: leave it out of the Play build.**

**What the code does.** `helper/adb/WirelessAdb.kt:39` is a hand-written adb host: it finds the phone's own adbd over mDNS, pairs with the user's 6-digit code, connects over loopback and sends `shell:$command` (`:120`). `helper/GrayscalePairing.kt` passes it one fixed command, `pm grant <own package> android.permission.WRITE_SECURE_SETTINGS`. `res/values/strings.xml:334` tells users to tap Build number seven times and turn on Wireless debugging.

In its favour:

- Every step is user-driven.
- The command is fixed and only targets Parem.
- The key pair is generated per run (`helper/adb/WirelessAdb.kt:54`) and never stored.
- Only two Secure keys are ever written (`helper/GrayscaleController.kt:54-55`, `:152`).
- `grayscale_pair_done` tells the user to turn Wireless debugging off.

**The policy case against it:**

- WRITE_SECURE_SETTINGS is [documented](https://developer.android.com/reference/android/Manifest.permission) "Not for use by third-party applications."
- Play's restricted permissions include those designated "Signature", and "Use of permissions in violation of Google Play Malware policies (including Elevated Privilege Abuse) is expressly prohibited."
- Elevated Privilege Abuse: "Code that compromises the integrity of the system by breaking the app sandbox, gaining elevated privileges, or changing or disabling access to core security-related functions", with the example "An app that violates the Android permissions model".
- The [Health apps article](https://support.google.com/googleplay/android-developer/answer/13996367) states it plainly: "Apps may not break the Android security and permissions sandbox regardless of user consent."
- Device and Network Abuse example: "Apps that facilitate or provide instructions on how to hack services, software or hardware, or circumvent security protections."

**The case for it:** the published text never mentions adb. [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) (10M+, updated Sep 4, 2026) and [LADB](https://play.google.com/store/apps/details?id=com.draco.ladb) (50K+, updated Jul 27, 2026) are live on Play and connect to the phone's own adbd.

**Would a reviewer accept it?** Possibly, if they only read the listing and manifest, since no declaration form exists for this permission and the grant never happens during a normal review. Those precedents are weak for Parem, though. Shizuku and LADB are Tools apps where adb is the declared core purpose. Parem would be a launcher embedding an adb client so it can raise its own permissions for an optional feature. **Whether that is Elevated Privilege Abuse cannot be settled from the published text.**

**Why I would not test it.** The risk is asymmetric. "Rejections don't impact the standing of your Google Play Developer account", but "Repeated or serious violations (such as malware, fraud, and apps that may cause user or device harm) ... will result in termination". A finding under the Malware policy on the first app of a new personal account is not worth one setup shortcut.

Two more factors point the same way:

- **TLS trust manager.** `helper/adb/WirelessAdb.kt:200-203` accepts any certificate as long as the chain is non-empty. The reason in the comment is sound for loopback, but Google [treats trust managers that ignore validation as a vulnerability](https://support.google.com/faqs/answer/6346016) that can get an app removed, and policy says "We don't allow code that introduces or exploits security vulnerabilities." Whether Play's scanner flags this form, which has a conditional throw, is not public. It would only show as a Console alert after upload.
- **The feature may break anyway.** At target API 37, `NsdManager` discovery needs [local network access](https://developer.android.com/privacy-and-security/local-network-permission), and Play will require a [core-functionality declaration](https://support.google.com/googleplay/android-developer/answer/16935362) that a launcher's optional setup step is unlikely to pass. A [third-party report](https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/) says a Google engineer floated blocking localhost adb. That is unofficial, and I could not read the tracker issue because it requires a login.

**Options:**

| Option | What changes | Cost | Risk |
|---|---|---|---|
| **A. Play build without pairing** (recommended) | A `play` build drops `helper/adb/*`, `GrayscalePairing`, its receiver and POST_NOTIFICATIONS. It keeps the adb command and WebUSB page | Play users need a computer once. A flavour, or a build flag plus a manifest overlay, plus CI. Branches in `GrayscaleSheet`, `WellbeingSettingsCard`, `OnboardingFragment`. R8 dead-code removal alone is not enough: the code must be absent | Low. Key Mapper and MacroDroid (10M+ each, live) are the closest precedent, and search results showed adb-grant guidance for both |
| B. Ship as built | Nothing | None now | The asymmetric risk above |
| C. No grayscale on Play for 6.0 | Also drop WRITE_SECURE_SETTINGS and the per-app grayscale UI | Loses a headline feature on Play until 6.1 | Lowest |

This overrides the 2026-10-03 decision that pairing is primary (`docs/design/DECISIONS.md`) for the Play channel only, so it is Patric's call. GitHub and F-Droid builds keep pairing.

**If A is chosen, also edit:**

- the listing grayscale line (remove "over Wi-Fi on Android 11+") in en-US, de and et;
- the grayscale paragraph in `web/privacy.html`;
- `onboarding_row_grayscale_caption`;
- the source link: Play-only build logic must be in the public repo (see GPLv3 in section 4).

### L2. READ_CONTACTS prompt with no disclosure

- **Evidence:** `ui/settings/HomeScreenSettingsCard.kt:287` launches the runtime prompt straight from the toggle. `helper/ContactSearchManager.kt:56` reads names and numbers, capped at 2,000, into memory. They are never stored or sent. The listing mentions contact search only briefly.
- **Policy:** The [User Data policy](https://support.google.com/googleplay/android-developer/answer/18265346) lists as common violations "An app has a runtime permission requesting access to data before the prominent disclosure" and an app that accesses contacts and does not treat them as sensitive. There is a defence: the disclosure is required where access "may not be within the reasonable expectation of the user", and someone flipping a row called "Contact search" expects it. That is an argument, not a safe harbour, and the fix is one sheet.
- **Fix:** Add a sheet before the prompt: "Contact search reads names and phone numbers from your contacts, only on this phone, to match what you type. Parem never saves or sends them." Use "Agree" and "Not now". Resolves `data-flows.md` G3. Answers for the Contacts declaration are in section 8.
- **Later:** The [Contacts Permission policy](https://support.google.com/googleplay/android-developer/answer/16935362) covers apps targeting API 37+, mandatory January 27, 2027. Target 36 is out of scope today, but the same page says Console prompts apps that "carry READ_CONTACTS", so the form may appear anyway. When Parem moves to target 37 this feature needs that declaration to pass.

### L3. The grant page returns 404

- **Evidence:** `res/values/strings.xml:337` and `ui/GrayscaleSheet.kt:74` send users to `pilves.github.io/parem-launcher/grant/`, which returned 404 today. `web/` and `pages.yml` are not on `master`, the default branch, so Pages has never deployed.
- **Policy:** Mobile Unwanted Software: "Apps should provide all communicated functionality." Under Option A this page becomes the main grant route for anyone without adb installed.
- **Fix:** Merge to `master` (or run the Pages workflow) before the closed test. Check `/grant/` and `/privacy.html` live. Make sure `web/privacy.html` is final first, because the merge publishes it.

The package allowlist means URL input never reaches the shell (`web/grant/grant.js`), and the CSP blocks page-initiated connections. The page runs three commands on the phone: `pm path` to check installation, `pm grant` to grant the permission, and `dumpsys package` to check the grant.

---

## 4. Full audit

| Item | Rating | Evidence | Fix or note |
|---|---|---|---|
| Privacy policy | BLOCKER | B1 | Publish and wire in |
| Store graphics | BLOCKER | B2 | Replace all eight |
| 12-tester closed test | BLOCKER | B3 | Run after app setup |
| Accessibility service | DECLARATION-NEEDED | Section 5 | Form, video, 3 text fixes |
| Notification listener | DECLARATION-NEEDED | Section 6 | 2 string edits, 1 filter tweak |
| PACKAGE_USAGE_STATS | DECLARATION-NEEDED | Section 6 | 2 string edits |
| WRITE_SECURE_SETTINGS, grant from a computer | DECLARATION-NEEDED | Section 6 | Listing line done. Reviewer note |
| In-app adb pairing | LIKELY-REJECTION | L1 | Leave out of Play build |
| Device admin | DECLARATION-NEEDED | Section 6 | Privacy line done in draft. Add uninstall note to listing |
| READ_CONTACTS | LIKELY-REJECTION | L2 | One sheet |
| REQUEST_DELETE_PACKAGES | OK | `helper/SystemActions.kt:186`, `ACTION_DELETE` with Android's own confirmation, from the app menu | Normal permission |
| ACCESS_HIDDEN_PROFILES | OK | Manifest `:18`. Normal permission. Android [requires](https://developer.android.com/about/versions/15/behavior-changes-all) ROLE_HOME plus this declaration, and a launcher fits. I found no Play text on it | Private Space flows were unverified on a device (`HANDOFF.md:96`). Functional, not policy |
| SET_ALARM | OK | `helper/SystemActions.kt:93-101`, only from text the user typed | Normal permission |
| POST_NOTIFICATIONS | OK | Requested only when pairing starts. Only channel is `grayscale_pairing` | Removed under Option A |
| SET_WALLPAPER | OK | Normal permission, used by an opt-in feature | Feed is a separate row |
| Other declared: EXPAND_STATUS_BAR, INTERNET, camera `uses-feature`, `<queries>` | OK | `helper/SystemActions.kt:44-46` uses reflection for the shade, and the policy has no non-SDK rule. No CAMERA permission; torch only. No QUERY_ALL_PACKAGES, and a launcher is a named core purpose under Package Visibility | Manifest `:8` comment "Used only for downloading wallpapers" is stale |
| Library-merged | OK | APK: WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE from WorkManager. `SystemForegroundService` has no type and is never started; no `FOREGROUND_SERVICE_*` | If the FGS form appears, answer none |
| Self-update, update check, APK download | OK | No REQUEST_INSTALL_PACKAGES, no update check, no PackageInstaller, no DownloadManager. `classes.dex` only, no native code, no dynamic loading | Clean against "may not modify, replace, or update itself" |
| Donation links | OK | None exist | Section 2 |
| Listing text | OK | The listing now promotes every permission-backed feature, documents the Accessibility API, and drops "no data collection" and Olauncher's tagline. Limits pass: titles 29, 30, 29; short 79; full under 4,000 | Must be what is entered in Console. Grayscale line depends on L1 |
| Fork: impersonation text, repetitive content | OK | Package unclaimed on Play. The listing credits "the earlier open-source launcher by tanujnotes". Omnibox, widgets, limits, focus and quiet notifications are real differentiation | Depends on B2 |
| GPLv3 | OK | Below | Tag source at release |
| Background work | OK | Section 6 | None |
| `web/` grant page | LIKELY-REJECTION | L3 | Deploy it. Page content is fine |
| App bundle and signing | OK | `release.yml:49` now runs `bundleRelease` (`b5c7123`). [New apps need an AAB](https://developer.android.com/guide/app-bundle) | Trap: the first uploaded bundle's key becomes the upload key. See `ARCHITECTURE.md` draft. `release.yml:72` still hard-codes beta 2 notes |
| Target API, 16 KB | OK | Target 36 meets the [August 31, 2026 requirement](https://support.google.com/googleplay/android-developer/answer/11926878). No native libraries | `versionName` is still `v6.0.0-beta.2` |
| Open-Meteo terms and credit | DECLARATION-NEEDED | Section 6 | One line in the weather sheet |
| Daily wallpaper feed | DECLARATION-NEEDED | Section 6 | Disclosed in privacy draft. Own the list |
| Trader status | DECLARATION-NEEDED | Section 2 | Declare at account creation |

**GPLv3, in detail:**

- §5(a), notice of modification: `README.md` Origins plus dated `CHANGELOG.md`. Satisfied.
- §6(d), source: if source sits on another server you must "maintain clear directions next to the object code". The listing's GitHub link does this. Tag and push `v6.0.0` before the AAB goes live, including any Play-only build logic.
- §5(d), legal notices: not strictly required when the upstream UI lacks them ("your work need not make them do so"). `URL_ABOUT_PAREM` is empty and no licence text shows in-app. A one-line licence and no-warranty note on the About page is cheap and worth adding. I did not check whether upstream's UI shows them.
- Olauncher is GPLv3 with no name or fork restrictions in its README.

---

## 5. Accessibility service

**Rating: DECLARATION-NEEDED.** This is the best-prepared part of the app. Olauncher uses the same pattern on Play.

**What the code does.** `res/xml/accessibility_service_config.xml` limits the service to `typeViewClicked` events from Parem's own packages, with no `canRetrieveWindowContent` and no `isAccessibilityTool`. `helper/MyAccessibilityService.kt:28-31` matches only the invisible lock view and calls `GLOBAL_ACTION_LOCK_SCREEN`. Launchers are named as [not accessibility tools](https://support.google.com/googleplay/android-developer/answer/10964491), so leaving the flag unset is correct.

**Consent flow.** All four routes into Accessibility settings show the sheet first:

- `ui/settings/GesturesSettingsCard.kt:306`
- `ui/home/HomeGesturesController.kt:406`
- `ui/home/HomeGesturesController.kt:420`
- `ui/OnboardingFragment.kt:297`

| Requirement | Status |
|---|---|
| In the app, during normal use, not behind a menu | Pass: home double-tap and onboarding |
| Describes data accessed and how it is used | Pass: `lock_consent_body` (`strings.xml:294`) |
| Affirmative action | Pass: "Agree and open settings" |
| Decline path | Pass: "Not now", and dismiss counts as decline |
| Not bundled with other disclosures | Pass |

**Fix before filming:**

1. **Remove the restricted-settings line on Play builds.** `lock_consent_restricted` ("App info > menu > Allow restricted settings") is added on every API 33+ device by `ui/LockConsentSheet.kt:24`, and the quiet disclosure uses the same helper. Play installs do not need it, and policy bars using this API to "Work around Android built-in platform security controls". Telling a reviewer how to lift one, in the consent text, is an own goal. Gate it to non-Play installs. This reverses a recorded decision (`docs/design/DECISIONS.md`), so it needs Patric's yes.
2. **Fix the translated service description.** It shows on Android's own toggle screen. `values-zh/strings.xml:63` is the wrong string entirely: it tells users to search for Digital Wellbeing. The other 16 translations say, in their own languages, that data obtained through the service will not leave the device, which implies it collects data and conflicts with the English "cannot read your screen". Delete `accessibility_service_description` from all translations so they fall back to English until M4-WP9.
3. **Replace the declaration draft in `docs/design/M2-WP5.md:49`.** It says there is "no alternative API", while Parem ships device admin as an alternative, and policy says "Apps must use more narrowly scoped APIs and permissions in lieu of the Accessibility API when possible." Use the text in section 8, which states the real tradeoff.

The disclosure is English-only in a 19-locale app (`translatable="false"`). See section 9.

**Demo video.** [Required content](https://support.google.com/googleplay/android-developer/answer/10964491): app opening, the route to the disclosure, the full text, consent including the grant, non-consent including re-triggering, and a core feature in use. Use an unlisted YouTube link. Add captions, because the mechanism is invisible.

| Step | Show | Caption |
|---|---|---|
| 1 | Open Parem on a fresh install, set as home | "Parem Launcher, fresh install" |
| 2 | Double-tap empty home space, sheet appears | "Double-tap to lock needs the accessibility service" |
| 3 | Hold on the full text at least 8 seconds, scroll slowly if it scrolls | None |
| 4 | Tap "Not now". Double-tap again, nothing happens | "Declined: the service stays off and the gesture turns off" |
| 5 | Settings › Gestures › Double tap › Lock screen, sheet returns | "The disclosure shows again when the user retries" |
| 6 | Tap "Agree and open settings", enable Parem, accept Android's dialog | "Only after consent does the user reach Android's switch" |
| 7 | Back home, double-tap, screen locks | "The service receives Parem's own invisible lock tap and asks Android to lock" |

---

## 6. Other sensitive items

### Notification listener (DECLARATION-NEEDED)

No Play form exists: the policy text never mentions notification listeners. Notification access falls under restricted permissions as "Special", so the listing, disclosure and privacy-policy rules apply. The listing and the privacy draft cover it. `docs/design/M4-WP1.md:273` says launchers are "listed as allowed", and I could not find that in any current page. Treat it as unverified.

The component is `enabled="false"` until the user opts in, and the flow is app picker, then disclosure, then system access screen (`ui/QuietListSheet.kt:122`). Notification text is displayed but not stored. Nothing is transmitted.

**Fixes:**

- `strings.xml:317` says "Nothing is stored or sent anywhere", but notification keys are stored (`helper/notifications/QuietNotificationsManager.kt:37`) and keys can carry an app-chosen tag. Replace with: "Parem reads each notification's app, type and title on this phone to sort it, and keeps only an ID for the ones it hides. It never stores the text and never sends anything."
- Change the button from "Continue" to "Agree and open settings".
- Mobile Unwanted Software says "Do not suppress alerts to the user from other apps or from the operating system, notably those which inform the user of changes to their OS." `helper/notifications/QuietFilter.kt:18-29` protects calls, alarms, emergency alerts and media, but apps with a launcher entry such as Settings can still be hidden. Add `com.android.settings` to `NEVER_TOUCH` and the `sys` and `err` categories to `EXEMPT_CATEGORIES`.

### Usage access (DECLARATION-NEEDED)

No Play form; "Special" permission rules apply, and "sensitive device or usage data" is named in User Data. The feature is in the listing. Disclosures exist at `ui/BadHabitDialogs.kt:213` and `MainActivity.kt:300`.

**Fixes:**

- `strings.xml:97-98` and `:390` say "Nothing is collected or sent anywhere", but per-app times and open counts are in the backed-up prefs file. Do `data-flows.md` G1, or reword.
- `MainActivity.kt:311` (`grayscale_app_needs_usage`) has no data statement. Add "Everything is counted on your phone" and an "Agree" button.

### WRITE_SECURE_SETTINGS with a computer grant (DECLARATION-NEEDED)

Common practice: Key Mapper and MacroDroid are live on Play, and search results showed adb-grant guidance for both. Deceptive Behavior requires changes "with the user's knowledge and consent and be reversible by the user", which holds: `grayscale_grant_body` and `grayscale_toggles_hint` name Android's Color correction as the way back, and `GrayscaleController` restores colour when the triggers end.

Known edge: uninstalling while grey leaves the phone grey (`HANDOFF.md:93`). The text covers this, and a launcher cannot catch its own uninstall.

`docs/design/M4-WP5.md:177` says this permission is not restricted as far as it found. The current text says otherwise: Signature permissions are restricted permissions. There is still no declaration form.

### Device admin (DECLARATION-NEEDED)

`res/xml/policies.xml` requests only `force-lock`. Activation is an explicit choice (`ui/LockConsentSheet.kt:76`), `admin_permission_message` states the PIN cost, and `ui/settings/GesturesSettingsCard.kt:345` turns it off. The policy has no device-admin rule except the ransomware note. The relevant limits are "Apps that abuse features to prevent them from being uninstalled or stopped" and "Uninstall, where applicable, should be clear." Tasker's listing discloses the same use.

**Fix:** The listing mentions the PIN cost but not uninstall. Add: "Turn it off in Parem's gesture settings before uninstalling."

### Background work (OK)

- Three WorkManager jobs: wallpaper every 8 h when opted in (`MainViewModel.kt:362`), theme schedule (`helper/ThemeScheduleManager.kt:52`), one-shot focus end (`helper/GrayscaleController.kt:291`).
- No AlarmManager, no exact alarms, no wake locks in app code, no app foreground service, no app boot receiver.
- WorkManager's boot receiver is disabled until needed.
- Per-app grayscale polls `queryEvents` only during an active session (`helper/GrayscaleController.kt:226-245`) and backs off with the screen off.

### Open-Meteo (DECLARATION-NEEDED)

Donation-funded use is allowed, which settles `data-flows.md` G9. The data is CC BY 4.0, and the app shows no credit: no string mentions Open-Meteo. Device and Network Abuse lists "Apps that access or use a service or API in a manner that violates its terms of service."

**Fix:** Add to the weather sheet, as [the maintainer recommends](https://github.com/open-meteo/open-meteo/discussions/2245): "Weather data by Open-Meteo.com. Location data by GeoNames via Open-Meteo." Add one sentence saying the city and its coordinates go to Open-Meteo. This also closes `data-flows.md` G2.

### Daily wallpaper feed (DECLARATION-NEEDED)

`data/Constants.kt:100` is a gist owned by Olauncher's author. It held 383 entries today, on `images.unsplash.com` plus `i.redd.it`, `images.pexels.com` and `images2.imgbox.com`. Someone else decides what Parem sets as a user's wallpaper, with no review and unknown licences on the non-Unsplash hosts. Policy: "Downloaded resources must be compliant with all Google Play policies". This is unlikely to surface in review, but it is a real exposure after launch.

**Fix:** The privacy draft discloses it. Move to a list you own (`data-flows.md` G4). Rejection risk at review is low, so this can follow 6.0 if needed.

---

## 7. Network trace

From a grep for URLs and `HttpURLConnection`, confirmed against `data-flows.md`. No SDKs, no WebView, cleartext blocked by default (target 36, no `networkSecurityConfig`).

| Host | Evidence | Trigger | Data beyond IP and User-Agent |
|---|---|---|---|
| `geocoding-api.open-meteo.com` | `helper/WeatherManager.kt:98` | User types a city (opt-in) | Typed text |
| `api.open-meteo.com` | `helper/WeatherManager.kt:155` | Weather on, about hourly on home resume | Place coordinates rounded to 0.05° |
| `www.ecb.europa.eu` | `helper/CurrencyRates.kt:25` | Currency query, at most daily | None, fixed URL |
| `pilves.github.io` | `helper/WallpaperUtils.kt:148` | Daily wallpaper on, 8-hourly | None, fixed URL |
| Image hosts from the gist | `helper/WallpaperUtils.kt:49` | As above | None |
| `127.0.0.1` adbd | `helper/adb/WirelessAdb.kt:49` | Grayscale pairing | Stays on the phone |

Handed to other apps by user action, with no socket in Parem: web search (`ui/AppDrawerFragment.kt:252`, `:257`, `:260`), dialer, clock, calendar, crash report share (`ui/CrashReportSheet.kt:22`).

---

## 8. Form answers

Enter these for the build actually uploaded. They assume B1, L1 Option A and the section 6 fixes. The Console's field wording may differ from the [help page](https://support.google.com/googleplay/android-developer/answer/10787469), because I did not log in.

### Data safety

This section is a draft. The high-latitude rounding limitation below leaves the location classification unresolved; review the final build and Console wording before submitting.

| Field | Answer |
|---|---|
| Does your app collect or share any required user data types? | **Yes** |
| Is all user data collected encrypted in transit? | **Yes.** All HTTPS, cleartext blocked |
| Account creation | **My app does not allow users to create an account** |
| Way to request data deletion | **No.** The only declared data sits in Open-Meteo's logs, which the publisher cannot reach. `privacy.html` says so |
| Independent security review | **No** |

**Provisional data type: Location › Approximate location.** Do not finalize the precise-versus-approximate answer until the high-latitude issue is reviewed.

| Question | Answer | Why |
|---|---|---|
| Collected, shared, or both? | **Both** | "Collect" means "transmitting data from your app off a user's device". Open-Meteo is an independent third party |
| Processed ephemerally? | **No** | Open-Meteo's terms: "webserver log files that may contain sensitive information such as geographical coordinates", deleted after 90 days |
| Required or optional? | **Optional** | Off by default, the user picks a city |
| Why collected? | **App functionality** | Shows the temperature |
| Why shared? | **App functionality** | Same |

Note on "shared": the user-initiated exception could justify answering collected only, and the weather-sheet sentence strengthens that. It is weaker for the hourly refresh. "Both" is safe either way, because over-disclosure breaks no rule. If the privacy label matters more to Patric, answer "Collected" only, after the weather-sheet line ships.

**Not selected:**

| Type | Why |
|---|---|
| Precise location | **Unresolved.** No device-location permission or API is used: the user selects a geocoder result, and its coordinates are rounded to 0.05°. At very high latitudes a 0.05° cell can cover less than 3 km², and the geocoder is not latitude-restricted. Review how Play's classification applies to selected place coordinates before completing this answer. |
| Contacts | "only processed locally on the user's device". Dialer hand-off is user-initiated |
| Messages, notification content | On-device only |
| Installed apps, App interactions, Other actions | On-device only. See Android backup in section 9 |
| In-app search history | On-device. The city query is sent, but it is the same datum as the location already declared |
| Web browsing | Links open in the user's browser |
| Crash logs, Diagnostics | Parem never transmits them. The user sends them through an app they pick, so the user-initiated exception covers the hand-off |
| Device or other IDs | None. No Advertising ID, no AD_ID permission |
| IP address | Not a form type. The help page says to declare based on "particular usage", and Parem uses none. Settles `docs/design/M3-WP4.md:125` |
| Personal info, Financial, Health, Photos, Audio, Files, Calendar | Not accessed or not transmitted. Calendar uses a prefilled intent with no permission |

### Content rating

The [help page](https://support.google.com/googleplay/android-developer/answer/9859655) does not publish the questions, so these are by topic. Answer the on-screen wording truthfully.

| Topic | Answer |
|---|---|
| Category | The general non-game utility or other option. Not game, social, or news |
| Violence, sexual content, language, controlled substances, gambling, horror | **No.** This assumes the in-app ruling in section 2 |
| Users interact or exchange content | **No** |
| Shares location with other users | **No.** Coordinates go to a weather API, never to users |
| Digital purchases | **No** |
| Web browser or search engine | **No.** No WebView. Queries open the user's browser |
| User-generated content | **No** |

Expected, not guaranteed: ESRB Everyone, PEGI 3.

### Target audience and content

| Field | Answer |
|---|---|
| Target age | **18 and over** only |
| Additional restriction of minors | **Off.** No age-restricted content |
| Appeals to children, Teacher Approved | **No** |

Why 18+: Parem uses sensitive system access and has no features designed for children, and "You should only select age groups that include both adults and children, if you truly have designed your app for all ages". Any age group that includes children brings the Families policy. This does not stop older teens installing it. The privacy draft's under-13 wording stays true.

### Other App content declarations

| Declaration | Answer | Basis |
|---|---|---|
| Ads | **No** | No ad SDK in `app/build.gradle`, none in the APK |
| Advertising ID | **No** | No AD_ID in the merged manifest |
| App access | **All functionality is available without special access** | No login anywhere. Reviewer note below goes in the accessibility declaration |
| Government apps | **No** | ECB is a data source, and the app shows the source |
| Financial features | **My app doesn't provide any financial features** | Currency conversion is arithmetic on public rates, with nothing from the [form's list](https://support.google.com/googleplay/android-developer/answer/13849271) |
| Health | **My app does not have any health features** | See section 9 |
| News and magazines | **No** | [Out of scope](https://support.google.com/googleplay/android-developer/answer/16189314) |
| Foreground service, exact alarm, full-screen intent, photo and video | **None used** | Section 6 |

### App category and store settings

| Field | Answer |
|---|---|
| App or game | App |
| Category | **Personalization** ([definition](https://support.google.com/googleplay/android-developer/answer/9859673) names "home screen"; Olauncher is here too) |
| Tags | Up to 5 from Console's list, which is not public. Choose launcher and home-screen tags if offered |
| Email | `info@chaidla.ee`, matching the privacy page |
| Website | The GitHub repo for now. Do not list a Pages landing page until it exists, and keep the section 2 rules for it |
| Privacy policy | `https://pilves.github.io/parem-launcher/privacy.html` |

### Accessibility declaration

| Field | Answer |
|---|---|
| Why does your app need the API? | **App functionality** only |
| Do you collect and/or share personal or sensitive data using these capabilities? | **No** |
| Video | Unlisted YouTube link, section 5 |

Description:

> Parem Launcher is a home-screen app. Its accessibility service provides one optional feature: a double-tap or swipe gesture on Parem's home screen that locks the phone, using performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN). The service is limited by android:packageNames to Parem's own package, receives only TYPE_VIEW_CLICKED events, has no canRetrieveWindowContent, and ignores every event except a click on Parem's own invisible lock view. It cannot read the screen and collects, stores and shares no data. isAccessibilityTool is not set. The narrower alternative, DevicePolicyManager.lockNow(), needs device admin and makes the next unlock require a PIN, pattern or password without biometrics, so Parem offers it only as a fallback the user chooses. Users see an in-app disclosure and must tap "Agree and open settings" before Parem opens Accessibility settings. "Not now" or dismissing leaves the service off.

Review instructions:

> No account is needed. Open Parem and skip any onboarding step. To test the lock, double-tap empty space on the home screen and follow the disclosure. Optional features that need extra access are off by default: Settings > Digital Wellbeing for screen time and quiet notifications, Settings > Gestures for lock method. Grayscale needs a one-time grant from a computer: adb shell pm grant com.parem.launcher android.permission.WRITE_SECURE_SETTINGS. Parem then writes only accessibility_display_daltonizer_enabled and accessibility_display_daltonizer.

### Contacts declaration, only if Console prompts for it

| Field | Answer |
|---|---|
| Which feature needs READ_CONTACTS? | **Auto-Correct/Auto-Complete** |
| Why is the Contact Picker insufficient? | "Parem's optional search suggests contacts as the user types, so it must match the typed text against all contact names before the user has chosen anyone. The Android Contact Picker is a one-time selection screen, exists only on Android 17 and later, and cannot return type-ahead matches. Names and numbers are read into memory on the device, never stored and never transmitted." |

---

## 9. What the published text does not settle

| Question | What I would do |
|---|---|
| Is user-consented adb self-pairing Elevated Privilege Abuse? | Assume it could be. Option A |
| Does Play's scanner flag the trust manager? | Unknown until upload. Option A removes it |
| Is "No BS" profanity? | Treat it as yes |
| Does a tip to the app's own developer count as a "creator" tip? | Assume no. Website only |
| Does listing, then website, then payment count as leading users via the listing? | Keep it off the landing page |
| Does Android Auto Backup count as collection? | Do not declare. It is an OS service to the user's own Google account, and the developer has no access. Closest text is the Drive FAQ ("your app never collects or accesses the data"). Disclose in the privacy policy, which the draft does |
| Must disclosures be translated? | The text is silent, and asks for clarity at "a reading level of a 13-year-old". Translate the three consent strings for shipped locales, or accept the risk until M4-WP9. `docs/play/listing.md` already states the English UI |
| Is "Mindful pause" a health feature? | No. The [form's](https://support.google.com/googleplay/android-developer/answer/14738291) nearest category covers "guidance on stress management, mindfulness, meditation". A five-second delay is none of that. Stay away from words like addiction, therapy or mental health in the listing |
| Does a user-initiated crash report count as collection? | No, and it does not change the form. If Patric starts collecting them by email at scale, declare Crash logs as optional |
| Exact IARC and account-creation wording | Not public. Answer what the screen says |
| Trader status | Patric's legal call. Non-trader is defensible for a hobby |

---

## 10. Fix order

1. **Decide:** L1 option, and the restricted-settings reversal (section 5).
2. **Code:** L2 sheet, weather credit and disclosure, the section 5 and 6 string fixes, the `QuietFilter` additions, coordinate rounding, `URL_PAREM_PRIVACY`, and the Play build if Option A.
3. **Publish:** merge to `master` so `/privacy.html` and `/grant/` go live. Check both.
4. **Assets:** new feature graphic and screenshots.
5. **Console:** account, trader status, section 8 forms, signing choice. Upload to internal testing first and read any Console alerts.
6. **Closed test:** 12 testers, 14 days, then apply for production.

Corrections to earlier docs, not edited here: `docs/design/M4-WP5.md:177`, `docs/design/M4-WP1.md:273`, `docs/design/M2-WP5.md:49`, and `data-flows.md` G6 (2-decimal rounding).

Not audited: the February 2027 memory and code-size quality requirement, and Play Protect behaviour on a live upload.

---

## 11. Pages read

**Policy, read 2026-11-05:**
[Developer Program Policy](https://support.google.com/googleplay/android-developer/answer/18265346),
[Announcement Oct 29, 2026](https://support.google.com/googleplay/android-developer/answer/18100766),
[Announcement Apr 15, 2026](https://support.google.com/googleplay/android-developer/answer/16926792),
[Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780),
[Sensitive permissions](https://support.google.com/googleplay/android-developer/answer/9888170),
[Sensitive permissions preview](https://support.google.com/googleplay/android-developer/answer/16909972),
[Minimum scope permissions](https://support.google.com/googleplay/android-developer/answer/16935362),
[Accessibility API](https://support.google.com/googleplay/android-developer/answer/10964491),
[Prominent disclosure](https://support.google.com/googleplay/android-developer/answer/11150561),
[Metadata](https://support.google.com/googleplay/android-developer/answer/9898842),
[Store listing best practices](https://support.google.com/googleplay/android-developer/answer/13393723),
[Inappropriate Content](https://support.google.com/googleplay/android-developer/answer/9878810),
[Payments](https://support.google.com/googleplay/android-developer/answer/9858738),
[Understanding Payments policy](https://support.google.com/googleplay/android-developer/answer/10281818),
[US policy update](https://support.google.com/googleplay/android-developer/answer/15582165),
[US external content links](https://support.google.com/googleplay/android-developer/answer/16470497),
[EEA external offers](https://support.google.com/googleplay/android-developer/answer/14372887),
[EEA external offers changes](https://support.google.com/googleplay/android-developer/answer/16505463),
[TrustManager fix](https://support.google.com/faqs/answer/6346016).

**Forms and setup:**
[Data safety](https://support.google.com/googleplay/android-developer/answer/10787469),
[App content](https://support.google.com/googleplay/android-developer/answer/9859455),
[Declare permissions](https://support.google.com/googleplay/android-developer/answer/9214102),
[Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655),
[Target audience](https://support.google.com/googleplay/android-developer/answer/9867159),
[Health categories](https://support.google.com/googleplay/android-developer/answer/13996367),
[Health declaration](https://support.google.com/googleplay/android-developer/answer/14738291),
[Financial features](https://support.google.com/googleplay/android-developer/answer/13849271),
[Government information](https://support.google.com/googleplay/android-developer/answer/9514050),
[News declaration](https://support.google.com/googleplay/android-developer/answer/16189314),
[Categories](https://support.google.com/googleplay/android-developer/answer/9859673),
[Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151),
[Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465),
[Target API](https://support.google.com/googleplay/android-developer/answer/11926878),
[Foreground services](https://support.google.com/googleplay/android-developer/answer/13392821),
[Trader status](https://support.google.com/googleplay/android-developer/answer/17594752),
[Account information](https://support.google.com/googleplay/android-developer/answer/13628312),
[Account types](https://support.google.com/googleplay/android-developer/answer/13634885).

**Android:**
[Manifest.permission](https://developer.android.com/reference/android/Manifest.permission),
[Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-all),
[Local network permission](https://developer.android.com/privacy-and-security/local-network-permission),
[App Bundles](https://developer.android.com/guide/app-bundle),
[Unsafe X509TrustManager](https://developer.android.com/privacy-and-security/risks/unsafe-trustmanager).

**Third party:**
[Open-Meteo terms](https://open-meteo.com/en/terms),
[Open-Meteo discussion 2245](https://github.com/open-meteo/open-meteo/discussions/2245),
[Buy Me a Coffee fees](https://help.buymeacoffee.com/en/articles/8105744-how-to-calculate-charges-on-your-payment),
[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt),
[Olauncher README](https://raw.githubusercontent.com/tanujnotes/Olauncher/master/README.md),
[on-device adb report](https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/),
the wallpaper gist, and the Play listings for [Olauncher](https://play.google.com/store/apps/details?id=app.olauncher), [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api), [LADB](https://play.google.com/store/apps/details?id=com.draco.ladb), [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm), [Key Mapper](https://play.google.com/store/apps/details?id=io.github.sds100.keymapper) and [MacroDroid](https://play.google.com/store/apps/details?id=com.arlosoft.macrodroid).

**Could not read:** the Google issue tracker entry cited by the adb report (login required), the Open-Meteo licence page (empty when fetched), and anything inside Play Console.
