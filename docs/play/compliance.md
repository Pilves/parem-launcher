# Parem Launcher v6.0.0: Google Play compliance audit

**Source baseline:** `release/v6.0.0` at `881bd27`, reviewed 2026-11-06. This is a source review, not an exact-head build or traffic capture. The earlier 2026-11-05 audit at `217b303` is historical and is available in Git history; do not use its old findings as current release status. Policy references below were researched on 2026-11-05 and must be checked against the current Console wording before submission.

**Release status:** not ready for submission. CI results, the merged manifests of the exact-head debug APKs and the uploaded release bundle, a physical-phone pass, the accessibility demonstration, live policy and grant URLs, closed testing, signing and the owner's final declarations remain open. No human code review or independent security audit is claimed. `docs/play/data-flows.md` contains the detailed network and storage inventory; its original source audit has older line numbers, so recheck against the final build.

## 1. Release and publication gates

- `Constants.URL_PAREM_PRIVACY` points to `https://pilves.github.io/parem-launcher/privacy.html`; the Settings privacy row uses it. `web/privacy.html` contains the approved public first-name wording and a contact. Publishing and checking the live URL remain owner-controlled; source presence is not evidence that Pages has deployed.
- `web/grant/` contains the WebUSB grant page. The page checks installation with `pm path`, grants with `pm grant`, and checks the result with `dumpsys package` (`web/grant/grant.js`). Verify the live page, its links and actual USB behavior before launch.
- The Play listing and images exist in `fastlane/metadata/android/`. Recapture and review the final assets from the final Play build; no claim here that old screenshots show the final build. Verify the actual listing per locale and all live URLs before submission.
- Play account verification, the closed test, production access and the accessibility demonstration require owner action. Do not infer a Play Console decision from this document.

## 2. Public wording and source

The approved public first name is Patrick; the policy names Patrick, developer name CHAIDLA, Parem Launcher and `info@chaidla.ee`. Google Play's User Data policy asks the policy to identify the entity named in the store listing or app and provide a privacy contact. Verify the exact current listing and live policy together.

No donation or coffee link ships in 6.0. Avoid profanity on Play surfaces, the landing page, the app and pages the app opens. Do not claim that no data leaves the phone. The README and launch material disclose AI authorship and credit the Olauncher fork. Assess each final listing and translated asset against actual code and the current [store listing policy](https://support.google.com/googleplay/android-developer/answer/13393723).

## 3. Flavours and grayscale

`app/build.gradle` defines `play` and `full`. On-phone ADB pairing code lives under `app/src/full/java/com/parem/launcher/helper/adb/` and `GrayscalePairing.kt`; the Play channel's `ChannelFeatures.kt` does not provide that route. The full channel retains it. Play users can grant `WRITE_SECURE_SETTINGS` using a computer and the grant page or the shown adb command. This source layout is not a substitute for inspecting the final Play APK and bundle. The Play policy treatment of the grant remains an owner review item.

## 4. Manifest and build review

`app/build.gradle` specifies package `com.parem.launcher`, target SDK 36, versionCode 105 and versionName `v6.0.0`; debug adds `.debug`. The main source manifest declares `READ_CONTACTS`, `PACKAGE_USAGE_STATS`, `WRITE_SECURE_SETTINGS`, `ACCESS_HIDDEN_PROFILES`, `INTERNET`, `SET_WALLPAPER`, `SET_ALARM`, `EXPAND_STATUS_BAR`, and `REQUEST_DELETE_PACKAGES`; inspect the manifest itself for other declarations and `<queries>`. It declares no device-location permission or `QUERY_ALL_PACKAGES`. Camera and flash features are optional.

The main manifest uses `tools:node="remove"` for `android.permission.FOREGROUND_SERVICE` and WorkManager's `SystemForegroundService`. This is a merge directive, not proof of the final binary. The enabled wallpaper fixture was added in `SmokeTest`, but its CI conclusion at the final head is pending. Inspect **both debug APKs** with `aapt2 dump permissions` and `aapt2 dump xmltree`; identify them as debug artifacts. Inspect the real uploaded release bundle separately, including any library-added permissions and services. Do not answer a foreground-service or advertising-ID form from an uninspected manifest.

## 5. Accessibility and contacts

`res/xml/accessibility_service_config.xml` limits the lock service to Parem's package names and `typeViewClicked`; it does not request window content. `MyAccessibilityService` and `LockConsentSheet` implement the home lock and disclosure. The restricted-settings note is channel-gated by `ChannelFeatures`; verify behavior on a Play install. An accessibility declaration and an actual demonstration video remain pending. The full device accessibility pass, including TalkBack, is not claimed.

Contact search is optional: `HomeScreenSettingsCard` displays `contact_search_disclosure` before requesting `READ_CONTACTS`, and `ContactSearchManager` reads names and numbers for matching. Test the grant, denial and permanent-denial routes on a phone. A Contacts declaration may be requested; answer the actual form, including why a type-ahead search needs access, based on the final feature.

## 6. Other sensitive features and background work

The optional quiet notification listener is initially disabled in the source manifest and needs user-granted notification access. Usage access underpins screen time and related controls. Device admin is an optional lock fallback. The app explains these routes; grant and revocation behavior still needs the physical-phone pass. Consult `docs/RELEASE_CHECKLIST.md` for the required routes and limitations.

`WallpaperWorker` is scheduled by `MainViewModel.setWallpaperWorker` for periodic work with network and battery constraints. The current manifest removes the foreground-service merge entries as described above. Verify the enabled worker and resulting wallpaper update on exact-head CI and a phone; scheduling alone is not proof that a wallpaper was applied.

## 7. Data flows and backup

- Weather is off until enabled. The geocoder receives the text typed by the user. Forecast latitude is clamped to −84°…84° before both coordinates are rounded to 0.05° (`WeatherPrivacy.kt`, `WeatherManager.kt`). A cell at 84° is about 3.2 km²; places beyond the clamp receive a forecast at the clamped latitude. Exact-head CI and the owner's classification on the actual form remain pending.
- Currency conversion fetches public ECB rates. Optional daily wallpaper fetches `web/wallpapers.json` from the publisher's Pages URL, then an image from a listed host or fallback. During the first ten days the selected image depends on install age and theme; afterwards it depends on date and theme. Image hosts can infer install age during that initial period. Hosts also receive connection metadata.
- Cloud backup rules exclude the main preferences, notification keys and search history. Android 12+ device transfer includes main preferences while excluding the other two files (`res/xml/data_extraction_rules.xml`); legacy rules exclude all three. Import/export and device transfer are separate routes, and real device behavior remains to be tested.
- `web/privacy.html` describes deletion of local data, exports and outside-service data, including the browser grant-page ADB key and phone debugging authorizations. Verify the directions on actual devices; uninstall alone does not remove those keys.

## 8. Draft Console answers

These are proposals, not submitted or verified answers. Patrick must review the actual form, current policy, exact uploaded bundle and live privacy page. Do not infer the release bundle manifest from the debug APK.

| Field | Draft and evidence |
|---|---|
| Data safety: collects or shares required types | Yes: optional weather sends typed place searches and selected-place forecast coordinates to Open-Meteo. Review all endpoints and user-initiated hand-offs in `docs/play/data-flows.md`. |
| Location | Approximate location is proposed. The source clamps forecast latitude before rounding; the geocoder separately receives typed text. Verify the final build and current form definitions before the owner decides the classification. |
| Weather purpose and processing | Optional app functionality; the prior audit found Open-Meteo terms describe server logging, so do not claim ephemeral processing without current evidence. |
| Encryption | App-owned weather, ECB and wallpaper request URLs are HTTPS. Verify all final network flows and exact Console wording before answering. |
| Accounts and deletion | No in-app account. The policy describes local deletion and states the publisher cannot delete outside-service logs. Review the form's exact deletion questions. |
| Independent security review | No independent security audit has been performed. |
| Ads / advertising ID | No ad feature or SDK is identified in source; advertising-ID and library permissions await merged-manifest inspection. |
| Accessibility | Optional home-lock functionality; declaration, reviewer instructions and video are pending. The service configuration is described in section 5. |
| Foreground service | No app feature requests foreground execution; source removes WorkManager's foreground service. Verify merged APK and bundle manifests and the enabled worker before completing any applicable form. |
| App category / contact / privacy | Personalization is proposed; contact `info@chaidla.ee`; policy URL is in section 1 and must be live before submission. |

The Console may ask about contacts, usage access, notification access, device admin, content rating, target audience, financial features and app access. Use the final build and actual questions; do not present a predicted content rating or a declaration as an established result.

## 9. Translation and owner review

The contact, lock and quiet-notification consent strings have shipped-locale translations prepared in the repository. They are machine-translated drafts and need native-language review, including the full disclosure and buttons, before publication. Verify weather and other disclosures in each locale and at large font on a device. The owner must decide signing, final declarations, current policy interpretation, live publication and the accessibility video. Do not infer any of those decisions from source.

## 10. Final verification

1. Run Debug, Handoff and Smoke against the exact final head for both flavours and all required configurations; record run IDs and actual conclusions. Run the required repeated smoke passes for drawer/home changes.
2. Inspect permissions and merged manifest XML in each CI debug APK; repeat against the actual upload bundle. Verify the enabled wallpaper job and minified release behavior in the required CI checks.
3. Complete the physical-phone checklist per flavour, including backup and transfer, permission routes, drawer gestures and accessibility. Review final store captures and translations.
4. Verify live privacy and grant pages only after owner-approved publication; review the actual Console form and declarations. No source review substitutes for the phone pass or a Play review.

Policy references for the owner to recheck: [User Data](https://support.google.com/googleplay/android-developer/answer/18265346), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [accessibility](https://support.google.com/googleplay/android-developer/answer/10964491), and [content ratings](https://support.google.com/googleplay/android-developer/answer/9859655).
