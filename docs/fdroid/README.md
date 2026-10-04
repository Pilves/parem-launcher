# F-Droid

Parem is submitted to F-Droid's main repo as a **reproducible build**: F-Droid
builds the tag from source, checks the result matches the APK on our GitHub
release, and then publishes *our* signed APK. Users can switch between the
GitHub, IzzyOnDroid and F-Droid builds without reinstalling, and the 6.0 Play
build (a bundle, re-signed by Play) stays a separate install either way.

## What the repo already satisfies

- **No proprietary dependencies.** The runtime deps are AndroidX, Material
  Components and WorkManager (all Apache-2.0, from Google's Maven and Maven
  Central). No Play Services, Firebase, analytics or crash SDKs.
- **No dependency-info blob.** `dependenciesInfo { includeInApk = false }` in
  `app/build.gradle` keeps Google's encrypted dependency block out of the APK
  signing block (F-Droid's scanner rejects it). The Play bundle keeps it.
- **Fastlane metadata** in `fastlane/metadata/android/` (title, summary,
  description, icon, feature graphic, screenshots, per-versionCode
  changelogs). F-Droid reads it from the tag, so a release must add
  `changelogs/<versionCode>.txt`, as the release step already says.
- **Prebuilt files.** The only ones are `gradle/wrapper/gradle-wrapper.jar`
  (F-Droid swaps in its own Gradle) and `web/grant/vendor/` (the GitHub Pages
  grant page, not in the APK; the recipe `scandelete`s it).

## Anti-features

`NonFreeNet`: daily wallpapers come from Unsplash (picked from a list on a
GitHub Gist inherited from Olauncher) and currency rates from the ECB, neither a free-software service. Both run only after the user opts in
(daily wallpapers toggle, typing a currency conversion). Weather uses
Open-Meteo, which is AGPL open source, so it does not count. Nothing else
goes online: web search and the GitHub link open in the browser.

## Reproducibility check

```
scripts/check-reproducible.sh [ref] [signed.apk]
```

Builds the unsigned release APK of `ref` twice from fresh clones at different
paths and fails if the two differ. Given a signed APK (the GitHub release
asset), it also compares its entries against the build with signature files
ignored. On CI: Actions, then "Reproducible build", then Run workflow
(optionally with a release tag). It needs two full release builds, so on the
Pi expect it to take a while and run it while nothing else is building.

The entry comparison is a quick check, not F-Droid's own. F-Droid copies our
signature onto its build with `apksigcopier` and runs `apksigner verify`,
which also needs the ZIP layout to match. The release workflow builds with
`assembleRelease` and lets AGP sign, so the layout is the plain AGP output on
both sides. If a tag ever fails F-Droid's check while this script passes, the
JDK is the first suspect: CI builds with Temurin 17. Pin the same major
version in the recipe (`sudo:` installing `openjdk-17-jdk-headless`), and if
that still differs, compare with `diffoscope` on the kept build directories.

## Submitting (Patric)

1. Tag the first release that contains `dependenciesInfo` (6.0) and let
   `release.yml` publish the APK.
2. Run the "Reproducible build" workflow with that tag. It must pass.
3. Fill the TODOs in `com.parem.launcher.yml` (here): author name,
   versionName/versionCode of the tag, and the signing certificate's SHA-256
   (`apksigner verify --print-certs ParemLauncher-<tag>.apk`).
4. Fork https://gitlab.com/fdroid/fdroiddata, add the file as
   `metadata/com.parem.launcher.yml`, check it with
   `fdroid readmeta && fdroid lint com.parem.launcher && fdroid build -v -l com.parem.launcher`
   (the fdroidserver docker image works if you don't want it installed),
   and open a merge request with the text below.

### Merge request text

> **New app: Parem Launcher** (`com.parem.launcher`)
>
> A text-only home screen launcher focused on calm phone use: one search bar
> for apps, calculator, unit and currency conversion and quick actions; screen
> time, app limits, focus mode, quiet notifications and optional grayscale.
> Fork of Olauncher (already in F-Droid), GPL-3.0-only.
>
> - Source: https://github.com/Pilves/parem-launcher
> - Reproducible: our signed release APK is listed under `Binaries` and
>   `AllowedAPKSigningKeys`; the build was verified identical with
>   `scripts/check-reproducible.sh` on the tag.
> - Anti-features: `NonFreeNet` (opt-in Unsplash wallpapers and ECB currency
>   rates). No trackers, no proprietary dependencies.
> - Permissions worth explaining: the accessibility service is used only for
>   double tap to lock the screen, off by default; `WRITE_SECURE_SETTINGS` (grayscale) is
>   granted once over adb, through in-app wireless-debugging pairing on the
>   device itself, a WebUSB page or a plain adb command; `ACCESS_HIDDEN_PROFILES` is for Private Space support;
>   `PACKAGE_USAGE_STATS` powers screen time and app limits, read on the
>   device only; the notification listener is disabled until the user turns
>   on quiet notifications; device admin is an optional fallback for screen
>   lock when the accessibility service is off.
> - Fastlane metadata, screenshots and changelogs are in the repo.
>
> I am the developer and agree to the app being published in F-Droid.
