# Google Play listing: Parem Launcher v6.0.0

Prepared 2026-11-05 against `release/v6.0.0` at `0c95907`, rewritten the same
day for Patric's positioning: "no bullshit minimal launcher", blunt and plain,
one fed-up person's point of view. That word stays off every Play surface (see
"Voice" below). Copy in section 1 is what lives in `fastlane/metadata/android/`.
German and Estonian are drafts by a non-native writer: Patric should proofread
the Estonian, and a native speaker the German, before publishing.

## 0. Open before this can go live

| # | Issue | Why it matters | Owner action |
|---|---|---|---|
| 1 | **Privacy policy not live.** `web/privacy.html` now exists as a draft, with `{{CONTACT_EMAIL}}`, `{{PUBLISHER_NAME}}` and `{{PUBLISHER_ADDRESS}}` unfilled. `Constants.URL_PAREM_PRIVACY` is still `""`, so the in-app row is hidden. | Play requires a policy for every app, linked in Console and in the app, at an active public URL (no PDF). The Data safety form cannot be completed without it. | Fill the placeholders, publish, wire the constant (`docs/play/data-flows.md` G10, G11). |
| 2 | **"Donations only" is not true of the code, and is held.** No donation link, billing or sponsor text exists in the app, README or docs. | Rule: claims must match the code. Play's Payments policy also says apps may not lead users to a payment method other than Play billing, and names the listing as one such route. It carves out "tax exempt donations" and says nothing about voluntary tips. | The listing says "no purchases" and has no donation or coffee link, pending `docs/play/compliance.md`, which does not exist yet. Open-Meteo's free tier is also non-commercial (`data-flows.md` G9). |
| 3 | **No Estonian UI, German UI about 18% translated.** No `values-et`; `values-de` has 74 of 403 strings; 290 English strings are `translatable="false"`. M4-WP9 is open. | The home market gets an English app. An `et` listing that implied otherwise would be misleading. | de and et text state the English UI plainly. Remove that line when WP9 lands. |
| 4 | **Wallpaper list is not ours and not only Unsplash.** `URL_WALLPAPERS` is a gist owned by the upstream author. Fetched today it holds 741 `images.unsplash.com`, 10 `i.redd.it`, 8 `images.pexels.com` and 6 `images2.imgbox.com` URLs. | Someone else can change what Parem downloads. `docs/fdroid/README.md` and the fdroid metadata say Unsplash only. | Listing says "third-party hosts", which is accurate today. Host a vetted list under your own account (`data-flows.md` G4). |
| 5 | **Grayscale WebUSB page is down.** `https://pilves.github.io/parem-launcher/grant/`, linked in the app, returned 404 today. | Android 10 users have only the computer route. The listing's "with a computer" stays true through the copied adb command, but the page should work at launch. | Publish the Pages site. |
| 6 | **All store graphics are upstream's** (section 5). The feature graphic is 512×250, failing Play's required 1024×500, and shows another app's name. | Cannot ship. | Codex is making the new graphic in `/home/pi/projects/parem-market`. Screenshots: section 6. |
| 7 | **"No data collected" cannot be claimed.** Play defines collection as transmitting data off the device. Weather sends typed text and the picked place's coordinates to Open-Meteo, and `data-flows.md` proposes declaring approximate location as collected. | The text says what is true: no account, no analytics, and exactly what goes online. It never says nothing leaves the phone. The old screenshot 07 and old description both claimed "no data collection". | Once `compliance.md` fixes the Data safety answers, re-read the "What goes online" section against them. |
| 8 | **Moving from GitHub builds.** `ARCHITECTURE.md` recommends a separate Play signing key, not yet decided; `docs/fdroid/README.md` plans a separate install. | With separate keys, GitHub users must export, uninstall, install, import. What's new says so. | If you choose the shared key instead, cut that line from What's new. |

Not blockers, flagged for later: `README.md` still advertises per-slot swipe-up
apps, which v6 removed. Do not reuse that line.

## 1. Listing text

Counts are Unicode characters including `<b>` tags. Play counts full-width and
half-width characters alike. The fastlane files carry no trailing newline, so
each count is exact.

### en-US (default)

**App name** (29/30)

```text
Parem: Plain Minimal Launcher
```

**Short description** (79/80)

```text
No-nonsense minimal launcher. Focus, app limits. No ads. No accounts. No catch.
```

**Full description** (3962/4000)

```text
I got fed up with my phone. Every icon wanted a tap and every app wanted my attention. Parem is the minimal Android launcher I wanted instead: a short list of app names, notifications that can wait, and a speed bump before the apps that eat your day. It is for less screen time or a proper digital detox, without swapping your smartphone for a dumbphone.

No ads. No accounts. No catch. No analytics or purchases either: every feature is free.

Parem replaces your home screen, so setup asks you to make it your home app. Your apps stay installed and you can switch back any time. Needs Android 10 or newer.

<b>A minimalist home screen made of words</b>
• Up to 8 apps as plain text under your clock and date. Icons and icon packs are optional.
• Folders, website shortcuts and resizable widgets
• Swipes and double tap do what you tell them; optional gesture letters open apps

<b>One search field, no hunting</b>
Swipe up and type. Initials and partial names work, and a single match opens by itself unless you turn that off. The same field also does:
• Sums, units and currency: 2+2, 5 km to mi, 100 eur in usd
• Clock and calendar: alarm 7:30, timer 10m, event friday 3pm dentist
• Phone numbers, app shortcuts and Parem's own settings
• Web search

<b>Calm phone tools</b>
All off until you turn them on, and each says what it needs first.
• Screen time: today's total on home, a 7-day graph and a weekly review.
• App limits: a daily allowance per app, then a warning you can override. Both need usage access.
• Mindful pause: for an app with a limit, a question and a five-second wait before it opens.
• Focus mode: timed sessions or weekly schedules. Up to 5 allowed apps; your phone app is never blocked.
• Quiet notifications: apps you haven't allowed move out of the shade into a quiet list. Calls, alarms, media, navigation and emergency alerts stay visible; hidden items return within 8 hours. A short sound or vibration may still play unless you choose Make silent. Needs notification access.
• Grayscale: now, during focus, after a limit, or per app. Needs a one-time developer-tools permission (about 2 minutes), over Wi-Fi on Android 11+ or with a computer.

Straight talk: limits, pause and focus only cover apps opened through Parem. They are speed bumps, not a lock. You can always get past them.

<b>What goes online, and what doesn't</b>
Parem has no account, no server of its own, and no analytics, ad or tracking code. The source is public, so don't take my word for it. Contacts, notifications and app search are handled on your phone, and Parem never asks for your location.

It does go online for these:
• Weather, off by default: the city you search for and pick goes to Open-Meteo.
• Daily wallpaper, off by default: a list from GitHub, then images from third-party hosts.
• Currency, when you type a conversion: a fixed download of European Central Bank rates, at most daily. Your query isn't in it.

Those services see your IP address, like any connection. Web searches open in your browser (Google after a leading space, DuckDuckGo for !bangs). Parem's crash reports are off by default and sent only by you. If you use Android's device backup, it copies Parem's settings, your weather city and today's screen time to your Google account.

Double tap to lock is optional. After you agree, it uses Android's accessibility service only to lock the screen; the service sees only taps inside Parem and cannot read your screen. A device-admin alternative needs your PIN after each lock. Contact search is opt-in too.

<b>Also included</b>
Tablets and foldables, TalkBack, hardware keyboards, Android font sizes up to 200%, work profiles, and Private Space on Android 15 and later.

<b>Free and open source</b>
GNU GPLv3. Source and credits, including the earlier open-source launcher by tanujnotes that Parem grew from: github.com/Pilves/parem-launcher

Parem is Estonian for "better". As in: better things to do than stare at a phone.
```

### de (Play locale `de-DE`)

Android's own German terms are used (Start-App, Bedienungshilfen, Graustufen,
Zugriff auf Nutzungsdaten). This uses "du", which breaks with the formal "Sie"
in `values-de`, because a fed-up person does not say "Sie". Your call.

**App name** (30/30)

```text
Parem: Klarer Minimal-Launcher
```

**Short description** (79/80)

```text
Minimal-Launcher ohne Unsinn. Mit Fokus. Keine Werbung. Kein Konto. Kein Haken.
```

**Full description** (3995/4000)

```text
Ich hatte genug von meinem Smartphone. Jedes Icon wollte angetippt werden, jede App wollte Aufmerksamkeit. Parem ist der minimalistische Android-Launcher, den ich stattdessen wollte: eine kurze Liste von App-Namen, Benachrichtigungen, die warten können, und eine Bremse vor den Zeitfresser-Apps. Für weniger Bildschirmzeit oder echtes Digital Detox, ohne Umstieg auf ein Dumbphone.

Keine Werbung. Kein Konto. Kein Haken. Keine Analyse, keine Käufe. Alles kostenlos.

Parem wird bei der Einrichtung deine Start-App. Deine Apps bleiben installiert, zurückwechseln geht jederzeit. Ab Android 10. Die App-Oberfläche ist derzeit überwiegend Englisch.

<b>Ein Startbildschirm aus Wörtern</b>
• Bis zu 8 Apps als reiner Text unter Uhr und Datum. Icons und Icon-Packs sind optional.
• Ordner, Website-Verknüpfungen und Widgets mit anpassbarer Größe
• Wischgesten und Doppeltippen machen, was du festlegst; optionale Gestenbuchstaben öffnen Apps

<b>Ein Suchfeld, kein Suchen</b>
Nach oben wischen und tippen. Anfangsbuchstaben und Namensteile genügen, ein einzelner Treffer öffnet sich von selbst (abschaltbar). Außerdem kann das Feld:
• Rechnen und umrechnen: 2+2, 5 km to mi, 100 eur in usd
• Wecker, Timer und Termine (englische Befehle): alarm 7:30, timer 10m
• Telefonnummern, App-Shortcuts und Einstellungen finden
• Websuche

<b>Für ein ruhiges Smartphone</b>
Alle sind aus, bis du sie einschaltest, und sagen vorher, was sie brauchen.
• Bildschirmzeit: Tagessumme, 7-Tage-Grafik und Wochenrückblick.
• App-Limits: Tageskontingent pro App, danach eine Warnung, die du übergehen kannst. Beide brauchen Zugriff auf Nutzungsdaten.
• Achtsame Pause: bei einer App mit Limit eine Frage und fünf Sekunden Wartezeit.
• Fokusmodus: mit Timer oder Wochenplan. Bis zu 5 erlaubte Apps; die Telefon-App wird nie blockiert.
• Stille Benachrichtigungen: Nicht erlaubte Apps wandern aus der Benachrichtigungsleiste in eine stille Liste. Anrufe, Wecker, Medien, Navigation und Notfallwarnungen bleiben sichtbar, Ausgeblendetes kehrt binnen 8 Stunden zurück. Ton oder Vibration sind möglich, solange du nicht „Make silent“ wählst. Braucht Benachrichtigungszugriff.
• Graustufen: sofort, im Fokus, nach einem Limit oder pro App. Braucht einmalig eine Entwicklerberechtigung (etwa 2 Minuten), per WLAN (Android 11+) oder mit Computer.

Klartext: Limits, Pause und Fokus gelten nur für Apps, die über Parem geöffnet werden. Sie bremsen, sperren aber nicht.

<b>Was online geht und was nicht</b>
Kein Konto, kein eigener Server, kein Analyse-, Werbe- oder Tracking-Code. Der Quellcode ist öffentlich, du musst mir also nicht glauben. Kontaktsuche (optional), Benachrichtigungen und App-Suche werden auf dem Smartphone verarbeitet, und Parem fragt nie nach deinem Standort.

Online geht Parem hierfür:
• Wetter, standardmäßig aus: Die gesuchte und gewählte Stadt geht an Open-Meteo.
• Tägliches Hintergrundbild, standardmäßig aus: Liste von GitHub, dann Bilder von Drittanbietern.
• Wechselkurse, bei einer Umrechnung: höchstens täglich ein fester Download der EZB-Kurse, ohne deine Eingabe.

Diese Dienste sehen wie bei jeder Verbindung deine IP-Adresse. Websuchen öffnen sich im Browser (Google nach einem Leerzeichen, DuckDuckGo bei !Bangs). Parems Absturzberichte sind standardmäßig aus und werden nur von dir gesendet. Die Android-Gerätesicherung kopiert, falls genutzt, Einstellungen, Wetterort und heutige Bildschirmzeit in dein Google-Konto.

Doppeltippen zum Sperren ist optional und nutzt nach deiner Zustimmung die Android-Bedienungshilfe nur dafür. Der Dienst sieht nur Tipps innerhalb von Parem und kann deinen Bildschirm nicht lesen. Die Alternative per Geräteadministrator verlangt nach jeder Sperre deine PIN.

Außerdem: Tablets, Foldables, TalkBack, Tastaturen, Schrift bis 200 %, Arbeitsprofile, vertrauliches Profil ab Android 15.

<b>Kostenlos und Open Source</b>
GNU GPLv3. Hervorgegangen aus einem Open-Source-Launcher von tanujnotes. Quellcode und Danksagungen: github.com/Pilves/parem-launcher
```

### et (Play locale `et`)

"Avakuva", "juurdepääs kasutusele", "halltoonid" and "juurdepääsetavus" are
Android's own Estonian terms from AOSP. "Launcher" stays in the name and text
because AOSP's "avakuva rakendus" is what Android calls it but "launcher" is
what people type. "Ilma jamata" is colloquial for "no nonsense" and is not
vulgar, but please confirm it reads right. The brand is kept uninflected: "Parema" and "Paremi" would
read as the adjective, so the text says "Parem Launcheri kaudu" or "launcheri".

**App name** (29/30)

```text
Parem: minimalistlik launcher
```

**Short description** (79/80)

```text
Minimalistlik launcher ilma jamata. Fookus, limiidid. Ilma reklaami ja kontota.
```

**Full description** (3980/4000)

```text
Mul sai oma telefonist villand. Iga ikoon tahtis puudutust, iga rakendus tähelepanu. Parem telefon ei pea olema uus telefon. Piisab samast telefonist, mis segab vähem.

Parem on minimalistlik Androidi launcher ehk avakuva rakendus: lühike nimekiri rakenduste nimedest, märguanded, mis võivad oodata, ja piduri ajasööjate ette. Sobib, kui tahad vähem ekraaniaega või korralikku digipaastu, aga nupptelefoni tagasi ei taha.

Reklaami pole. Kontot pole. Konksu pole. Analüütikat ja oste ka pole. Kõik on tasuta.

Seadistamisel määrad Parem avakuva rakenduseks. Sinu rakendused jäävad alles ja võid igal ajal tagasi vahetada. Alates Android 10-st.

Kasutajaliides on praegu inglise keeles, aga otsing mõistab ka eestikeelseid käske: äratus kell 7, kohtumine reedel kell 15 hambaarst.

<b>Avakuva sõnadest</b>
• Kuni 8 rakendust lihttekstina kella ja kuupäeva all. Ikoonid ja ikoonipakid on valikulised.
• Kaustad, veebisaitide otseteed ja muudetava suurusega vidinad
• Pühkimised ja topeltpuudutus teevad seda, mida valid; žestitähed avavad rakendusi

<b>Üks otsinguväli</b>
Pühi üles ja hakka tippima. Töötavad algustähed ja nimeosad ning ainus vaste avaneb ise (saad välja lülitada). Sama väli oskab veel:
• arvutada ja teisendada: 2+2, 5 km to mi, 100 eur in usd
• seada äratusi, taimereid ja sündmusi
• leida telefoninumbreid, rakenduste otseteid ja seadeid
• teha veebiotsinguid

<b>Rahuliku telefoni tööriistad</b>
Kõik on väljas, kuni need sisse lülitad.
• Ekraaniaeg: tänane summa, 7 päeva graafik ja nädala kokkuvõte.
• Rakenduste piirangud: päevane limiit, seejärel hoiatus, millest saad mööda minna. Mõlemad vajavad juurdepääsu kasutusele.
• Teadlik paus: piiranguga rakenduse puhul küsimus ja viis sekundit ootust.
• Fookusrežiim: taimeriga või nädala ajakava järgi. Lubada saad kuni 5 rakendust, telefonirakendust ei blokeerita kunagi.
• Vaiksed märguanded: lubamata rakenduste märguanded liiguvad märguandealalt vaiksesse nimekirja. Kõned, äratused, meedia, navigeerimine ja hädaolukorra teated jäävad nähtavale, peidetud märguanded tulevad 8 tunni jooksul tagasi. Heli või värin võib tulla, kui sa pole valinud Make silent. Vajab juurdepääsu märguannetele.
• Halltoonid: kohe, fookuse ajal, pärast piirangut või rakenduse kaupa. Vajab ühekordset luba arendaja tööriistade kaudu (umbes 2 minutit), telefonis üle Wi-Fi (Android 11+) või arvutiga.

Otse öeldes: piirangud, paus ja fookus kehtivad ainult rakendustele, mis avatakse Parem Launcheri kaudu. Need aeglustavad, aga ei lukusta.

<b>Mis läheb võrku ja mis mitte</b>
Kontot pole, oma serverit pole ning analüütika-, reklaami- ega jälgimiskoodi pole. Lähtekood on avalik, nii et ära usu mind sõnapealt. Kontaktiotsing (valikuline), märguanded ja rakenduste otsing töödeldakse telefonis ning asukohaluba ei küsita kunagi.

Võrku läheb see:
• Ilm, vaikimisi väljas: otsitud ja valitud linn läheb teenusele Open-Meteo.
• Igapäevane taustapilt, vaikimisi väljas: nimekiri GitHubist ja pildid kolmandatelt osapooltelt.
• Valuuta, kui kirjutad teisenduse: kuni kord päevas Euroopa Keskpanga kursifail, ilma sinu päringuta.

Need teenused näevad sinu IP-aadressi. Veebiotsingud avanevad brauseris (tühiku järel Google, !bang puhul DuckDuckGo). Krahhiraportid on vaikimisi väljas ja neid saadad ainult sina. Androidi seadme varundus kopeerib kasutamise korral seaded, ilmalinna ja tänase ekraaniaja sinu Google'i kontole.

Topeltpuudutusega lukustamine on valikuline ja kasutab pärast nõusolekut Androidi juurdepääsetavuse teenust ainult ekraani lukustamiseks. Teenus näeb ainult puudutusi launcheri sees ega saa ekraani lugeda. Seadme administraatori kaudu lukustades on pärast iga lukustamist vaja PIN-koodi.

Veel: tahvlid, volditavad seadmed, TalkBack, klaviatuurid, tekst kuni 200%, tööprofiilid, privaatne ruum alates Android 15-st.

<b>Tasuta ja avatud lähtekoodiga</b>
GNU GPLv3. Parem kasvas välja avatud lähtekoodiga launcherist, mille autor on tanujnotes. Lähtekood ja tänusõnad: github.com/Pilves/parem-launcher
```

### Decisions behind the copy

- **Name:** "Parem: Plain Minimal Launcher". "Minimal Launcher" is the head term and must stay. "No-Nonsense Minimal Launcher" with the brand runs to 35 characters, and "Parem: No-Nonsense Launcher" loses "minimal". "Plain" fits, carries the voice and is literally true of a text home screen. "No-nonsense" goes in the short description instead. The on-device label stays "Parem Launcher" (`app_name`). "No Ads" and "Free" stay out of the title because Play's best practices forbid deal words there.
- **Voice.** First person, short sentences, no wellness-speak, and it admits limits ("speed bumps, not a lock"), which suits the brand better than polish. The fed-up target is the phone, never other launchers, because Play bans comparisons. Feature names such as Mindful pause stay, since the app uses them.
- **Word tiers.** "Bullshit" appears on no Play surface: Google's guidance rules out profane or vulgar wording. "BS" is also out, because `compliance.md` does not exist yet to clear it. "Crap" and similar were avoided for the same reason.
- **Tagline: "No ads. No accounts. No catch."** I think this beats the working line "No ads. No accounts. No nonsense." "Nonsense" is an attitude; "catch" is a promise a reader can test, and it holds here (no purchases, no paywall, no account). It also translates well: "Kein Haken", "Konksu pole". "No nonsense" stays approved and lives on as "no-nonsense" in the short description. Swapping one word back is easy if you prefer it.
- **Using "parem" = "better":** yes, carefully. Never in a title or short description, where "better" could read as a comparison with other apps. en-US ends on "better things to do than stare at a phone", which is about your time. The de line was cut for length. In et the word does real work, so the opening ("Parem telefon ei pea olema uus telefon") plays on it, about the user's own phone.
- **Opening line replaced.** The old en-US text opened with the same sentence as the upstream app's Play listing, and upstream's graphic carries it too.
- **Formatting.** The old file used `<p>`, `<ul>`, `<li>`. Google's help does not list supported tags. The rendered descriptions of three competitors showed only `<b>`, `<br>`, and in one case `<h3>`. F-Droid supports `<b>` and turns line breaks into `<br>`. So the text uses `<b>`, line breaks and `•`, which is safe on both. Check the Console preview once.
- **Sentence-case headings.** The old "THE OMNIBOX" style breaks the Metadata policy's ALL CAPS rule.
- **F-Droid** reads these same files: its limits (title 50, summary 80, description 4000) are all met.

## 2. Category and tags

**Category: Personalization.** Google's definition of it names "home screen", and a launcher is the home screen. The closest direct peers checked are there (3 of 5 below). The alternative, Productivity, is defensible because the two peers selling focus and notification filtering use it, and Google's Productivity examples ("notepad, to-do list, keyboard...") fit a utility, not a home screen. Choosing Personalization puts Parem among launchers, where someone already intends to switch. The category can be changed later if data disagrees.

**Tags.** Maximum five, chosen in Console from Google's list. That list sits behind the Console login, so I could not read it. Pick the closest available names, in this order, each obviously relevant as Play requires:

1. Launcher / home screen: the product itself.
2. Digital wellbeing or screen time: the 6.0 theme.
3. Productivity or focus: Focus mode and limits.
4. Minimalism, if it exists: the core style.
5. Privacy, if it exists: the differentiator.

## 3. Search terms and coverage

Play publishes no search volumes, and the old public autocomplete endpoint returned 404 today, so this ranks terms by what live Play results show, not by measured demand. Searches ran 2026-11-05 with `hl=en_US&gl=US`, so German and Estonian demand is unmeasured.

| Target term | Why | Covered by |
|---|---|---|
| minimal launcher, minimalist launcher | Head terms. Both return nearly the same set, so Play appears to stem them. | Title; short; full: "minimal Android launcher", "minimalist home screen" |
| launcher, home screen | Category intent | Title; short; full |
| no-nonsense launcher | Owned brand phrase, nothing seen ranking for it | Short |
| calm phone | 6.0 theme, little competition seen | Full: "Calm phone tools". Dropped from the title for the new voice |
| focus mode, app limits | Feature intent | Short ("Focus, app limits"); full |
| screen time | Peers use it in titles | Full |
| digital detox | Several peer titles use it | Full, once |
| dumbphone | Crowded with look-alike titles | Full, once, honestly: Parem is what you use instead of one |
| launcher without ads, no ads launcher | Results are general launchers, an opening for a minimal one | Short; full |
| notification filter | Peers' selling feature | Full: "Quiet notifications". The exact phrase is not used, a known gap |
| grayscale | Few peers offer it free | Full |
| open source launcher | Reaches the privacy-minded | Full |
| text launcher | One peer owns this exact phrase | Title "Plain"; full: "plain text", "made of words" |

**Deliberately not targeted:** "app blocker". The roadmap excludes hard blocking from 6.0, and Focus only guards launches through Parem, so targeting it would mislead. Also no competitor names, and no repeated keyword lists, because Play's Metadata policy lists word blocks as a violation.

German and Estonian: "minimalistischer Launcher", "Bildschirmzeit", "Digital Detox", "Fokusmodus"; "launcher", "avakuva", "ekraaniaeg", "digipaast", "nupptelefon". Each appears in natural sentences.

## 4. How competitors position themselves

Read 2026-11-05 from each Play listing (`gl=US`), and from https://pausegadget.com/best-minimalist-launchers-android/ for pricing notes.

| App | Title on Play | Category, installs, rating | Angle |
|---|---|---|---|
| Olauncher | Olauncher. Minimal AF Launcher | Personalization, 1M+, 4.83 | Free, ad-free, playful, leads with press quotes |
| minimalist phone | minimalist phone® Screen Time | Productivity, 5M+, 4.66 | Launcher plus blocker, subscriptions |
| Before Launcher | Before Launcher \| Go Minimal | Productivity, 500K+, 4.78 | Notification filter, lifetime purchase, percentage claims |
| Niagara | Niagara Launcher ‧ Home Screen | Personalization, 10M+, 4.76 | Clean, customisable, awards |
| Minimo | Minimo \| Minimalist Launcher | Personalization, 10K+, 4.59 | Text-first, no tracking |

Others seen in results: Yin Yang, Indistract, Flow, MINIMAA, Oasis, Zen Detox, brute, Kern, Dumbphone-titled apps. Titles are near-identical, which is why a distinct word in the title matters.

**The gap Parem takes:** free of charge with the whole Calm stack (peers put notification filtering, timers or blocking behind payment), open source, and a privacy statement that names every network destination. Rivals with stronger simple claims exist: per the review above, two open-source launchers claim no internet access at all, which Parem cannot, because it holds `INTERNET`. So Parem's argument is specificity, not absence: here is exactly what goes online, and all but the currency download sit behind a setting that starts off. The listing never makes these comparisons: Play bans them, and they are here for your planning only.

Awards and quotes used by peers are avoided: Parem has none yet, and Play requires attribution for any it adds.

## What's new in 6.0.0

Paste per language, or save as `changelogs/<versionCode>.txt` once the 6.0.0 code is assigned (104 is beta.2, so that file is not created here). Play allows 500 Unicode characters per language.

**en-US** (462/500)

```text
First release on Google Play. Plain minimal launcher. No ads, no accounts. Calm phone tools, all off until you want them: quiet notifications, mindful pause, scheduled focus, grayscale, weekly screen-time review. Search sets alarms and timers, converts units and currency, finds app shortcuts and settings. Website shortcuts, Private Space, tablets, foldables, TalkBack. Needs Android 10+. On a GitHub build? Export your settings first, then expect to reinstall.
```

**de** (442/500)

```text
Erste Version bei Google Play. Klarer Minimal-Launcher. Keine Werbung, kein Konto. Werkzeuge für ein ruhiges Smartphone, alle aus, bis du sie willst: stille Benachrichtigungen, achtsame Pause, Fokus nach Plan, Graustufen, Wochenrückblick. Die Suche stellt Wecker und Timer, rechnet um, findet Shortcuts und Einstellungen. Dazu Tablets, Foldables, TalkBack. Ab Android 10. GitHub-Version? Erst Einstellungen exportieren, dann neu installieren.
```

**et** (423/500)

```text
Esimene väljalase Google Plays. Lihtne minimalistlik launcher. Reklaami ja kontot pole. Rahuliku telefoni tööriistad, kõik väljas, kuni soovid: vaiksed märguanded, teadlik paus, fookuse ajakava, halltoonid, nädala ekraaniaeg. Otsing seab äratusi ja taimereid, teisendab, leiab otseteid ja seadeid. Lisaks tahvlid, volditavad seadmed, TalkBack. Vajalik Android 10+. GitHubi versioon? Ekspordi enne seaded ja paigalda uuesti.
```

## 5. Audit of existing graphics

File dates are all 2026-11-05 12:42, which is only this worktree's checkout time, so git history is the evidence. All eight image files below date from `39d6dab` (2025-07-30, "initial fastlane structures from IzzyOnDroid") except the icon. Nothing has touched them since, so none can show 6.0: they predate Parem's own UI work.

**Requirements read today:** icon 512×512 32-bit PNG with alpha, max 1024 KB. Feature graphic 1024×500 JPEG or 24-bit PNG, no alpha. Screenshots JPEG or 24-bit PNG, 320 to 3840 px per side, longer side at most twice the shorter, up to 8 per device type, at least 2 to publish. For recommendation formats: at least 4, 1080×1920 portrait or 1920×1080 landscape minimum. Large screens: at least 4, 1080 to 7680 px, 16:9 or 9:16, no text outside the core experience. Taglines under 20% of the image. No device imagery. Clean status bar. Alt text up to 140 characters per graphic.

| File | Actual | Meets spec? | Shows 6.0? | Verdict |
|---|---|---|---|---|
| `icon.png` | 512×512 RGBA PNG, 3 KB | Yes | Yes: `>_` matches shipped adaptive icon (`b0bc7a0`, 2026-07-03) | **Keep** |
| `featureGraphic.jpg` | 512×250 JPEG | **No**: Play requires 1024×500 | No: reads "Olauncher - Minimal AF", other phones and UI | **Replace** (Codex, `parem-market`) |
| `phoneScreenshots/01.jpg` | 368×736 JPEG | Publishable only: under 1080 px, framed | No: "Minimal AF Launcher / AF stands for Ad-Free :D" | **Replace** |
| `02.jpg` | 368×736 | Same | No: upstream "Daily new wallpaper", third-party photo | **Replace** |
| `03.jpg` | 368×736 | Same | No: old drawer, no omnibox, Gboard strip | **Replace** |
| `04.jpg` | 368×736 | Same | No: upstream screen-time text | **Replace** |
| `05.jpg` | 368×736 | Same | No: upstream light theme, not the warm palette | **Replace** |
| `06.jpg` | 368×736 | Same | No: names real apps, including X, YouTube, Netflix | **Replace** |
| `07.jpg` | 368×736 | Same | No: "Zero data collection" (see section 0, item 7) over a mask image | **Replace** |

**All 7 screenshots and the feature graphic must be redone.** 8 of 9 files fail.

Two notes on the icon. `app/src/main/ic_launcher-playstore.png` is a stale ring from upstream (2023), so do not upload it. And `>_` on black reads as a terminal app, which is at odds with "Calm phone": your call, not a policy problem as far as I can tell.

## 6. Shot list

Roadmap M4-WP10 owns the scripted capture, which this document does not implement.

**Capture setup (all phone shots)**

- **Size:** exactly 1080×1920 (9:16). A stock 20:9 emulator (1080×2400) breaks Play's 2:1 limit, so use a 1080×1920 AVD at 420 dpi, or `adb shell wm size 1080x1920`.
- **Build:** release candidate, not debug. M4-WP30 notes a "Parem Debug" label.
- **Status bar:** Android demo mode (`sysui_demo_allowed`), full battery and signal, no notifications, no carrier.
- **Fixture apps:** no real brands. Install a stub with launcher activities named Feed, Clips and News, which also posts the sample notifications. Home slots: Phone, Messages, Camera, Maps, Music, Notes, with stock apps renamed in Parem if needed.
- **No personal data** and no third-party photos. Use a plain dark wallpaper.
- **Theme:** dark unless stated. Language en-US.
- **Caption band:** 300 px above the screenshot (15.6%, under the 20% limit), scaled screenshot below, no phone frame. Surface `#151412`, text `#ECE9E3`.
- **Localisation:** same shots with translated captions. UI stays English until M4-WP9.
- **Shoot last.** Open rows that change these screens: M4-WP47 (button order, shot 4), WP52 (sheets, shots 3 to 6), WP30 (polish), WP51 (Settings naming), WP49 (first-run home).

| # | Screen and state | Caption en / de / et | Alt text |
|---|---|---|---|
| 1 | Home, dark. Six text slots, clock, date, screen-time total. | No ads. No accounts. No catch. / Keine Werbung. Kein Konto. Kein Haken. / Reklaami pole. Kontot pole. Konksu pole. | Dark Parem home screen with a clock, date and six app names as plain text. |
| 2 | Drawer, query `100 eur in usd`, showing the result line with its ECB date, keyboard open. Needs network once for rates. | Apps, sums, alarms. One search field. / Apps, Rechnen, Wecker. Ein Suchfeld. / Rakendused, arvutused, äratused. Üks väli. | App drawer search field showing a currency conversion typed as 100 eur in usd, with the result and rate date. |
| 3 | First run, screen "Calm phone (optional)", all rows untouched. | Everything starts off. You decide. / Alles ist erst aus. Du entscheidest. / Kõik on alguses väljas. Sina otsustad. | First-run screen titled Calm phone, optional, listing tools you can set up or skip. |
| 4 | Mindful pause sheet for fixture app Feed: "Why are you opening Feed?", countdown at 3. | Five seconds to change your mind / Fünf Sekunden zum Umentscheiden / Viis sekundit, et ümber mõelda | Mindful pause sheet asking why you are opening an app, with a countdown before it can open. |
| 5 | Quiet list sheet with four fixture notifications from Feed, Clips and News. | Notifications wait. You don't. / Benachrichtigungen warten. Du nicht. / Märguanded ootavad. Sina mitte. | Quiet list sheet holding four notifications that were moved out of the notification shade. |
| 6 | Focus mode sheet with one weekday 09:00 to 17:00 window and allowed apps Phone, Messages, Maps. | Focus on a schedule. Up to 5 apps. / Fokus nach Plan. Bis zu 5 Apps. / Fookus ajakava järgi. Kuni 5 rakendust. | Focus mode sheet with a weekday schedule and a short list of allowed apps. |
| 7 | Screen-time sheet with the 7-day graph and weekly review card, fixture apps only. | See where your week went / Sieh, wo deine Woche blieb / Vaata, kuhu su nädal läks | Screen-time sheet with a seven-day bar graph and a comparison of this week and last week. |
| 8 | Home, light theme, one generic clock or calendar widget above four slots, one folder. | Light or dark. Widgets if you must. / Hell oder dunkel. Widgets, wenn's sein muss. / Hele või tume. Vidinad, kui vaja. | Light Parem home screen with a calendar widget above a short list of app names. |

**Shot 7 risk:** the weekly card needs two weeks of usage, or it shows "Not enough usage history yet". Run the AVD with usage over that time, or caption the 7-day graph alone. Do not fake the UI, because screenshots must reflect the real app.

**Tablets (optional to publish, needed for large-screen placement):** at least 4 each in `sevenInchScreenshots` and `tenInchScreenshots`, no captions. Use portrait 9:16 so the layouts stay real: 1080×1920 at 288 dpi for sw600dp, 2160×3840 at 480 dpi for sw720dp. Shots 1, 2, 6, 7. Avoid landscape home until M4-WP28 lands.

**Feature graphic.** Codex is producing this in `/home/pi/projects/parem-market`, so no brief here. It must be 1024×500, JPEG or 24-bit PNG, no alpha, with key elements away from the edges, and Google advises against pure white, black or dark grey backgrounds. Device imagery is on Play's list of elements to avoid. Suggested text and alt text, to keep it matched with the listing:

| Locale | Line | Alt text |
|---|---|---|
| en-US | No ads. No accounts. No catch. | Parem wordmark with the line No ads, no accounts, no catch. |
| de | Keine Werbung. Kein Konto. Kein Haken. | Parem-Schriftzug mit dem Satz Keine Werbung, kein Konto, kein Haken. |
| et | Reklaami pole. Kontot pole. Konksu pole. | Parem sõnamärk lausega Reklaami pole, kontot pole, konksu pole. |

Paths: `fastlane/metadata/android/<locale>/images/`. Keep the layout F-Droid already reads.

## 7. Claim check

Every claim in the text, against this worktree. "Tests" are JVM tests in `app/src/test`.

| Claim | Evidence | Status |
|---|---|---|
| No ads, analytics, trackers | `gradle/libs.versions.toml`: AndroidX, Material, WorkManager only | Verified |
| No purchases, every feature free | No billing dependency or paywall | Verified |
| No account, no own server | Network calls exist only in `WeatherManager`, `CurrencyRates`, `WallpaperUtils`; `WirelessAdb` is loopback (plus local mDNS for pairing) | Verified, matches `data-flows.md` |
| Never asks for location | No location permission in the manifest. Weather uses a typed city | Verified |
| Weather and wallpaper off by default | `WEATHER_ENABLED` false, `DAILY_WALLPAPER` false; nothing sets either true without a user tap | Verified |
| Currency fetch fixed, at most daily | `CurrencyRates.kt`: fixed URL, 24 h interval, triggered by a currency query | Verified |
| Weather sends the city searched and picked | `data-flows.md` N1, N2: typed text, then the place's coordinates | Verified |
| Services see your IP address | `data-flows.md` section 1a | Verified |
| Contacts, notifications, app search handled on the phone | `data-flows.md` section 2. Web search goes to the browser, stated next to it | Verified |
| Parem's crash reports opt-in, sent by user | `CRASH_REPORTS_ENABLED` false. Says "Parem's" because Android vitals can still report crashes on Play installs (`data-flows.md` S3) | Verified |
| Device backup copies settings, weather city and today's screen time | `backup_rules.xml`, `data-flows.md` S1 (the whole main prefs file, including `CACHED_USAGE_STATS` and weather coordinates) | Verified |
| Up to 8 apps, default 4; icons off by default | `Prefs.kt`: slots 1..8, `homeAppsNum` 4, `SHOW_ICONS` false | Verified |
| Folders, website shortcuts, resizable widgets | `FolderManager`, `WebShortcut`, `HomeWidgetController` | Verified in code |
| Gesture letters | `GestureLetterManager`, ten letters, off until enabled | Verified |
| Initials and partial names | `SearchMatcherTest` | Tests |
| 2+2, 5 km to mi, 100 eur in usd | `omnibox_hints`, `OmniboxResolverTest`, `UnitConverterTest` | Tests |
| Quick actions in en and et | `QuickActionParserTest`. Only phrases covered by tests are printed in et | Tests |
| Auto-launch can be turned off | M4-WP46, `autoLaunchAllowed` | Tests |
| Pause: a question, 5 s, needs a limit | `MindfulPause.DEFAULT_DELAY_SECONDS`, `AppMenuPolicy`. "Pause only" (WP47) has not landed | Verified |
| Focus: 5 apps, phone never blocked | `MAX_WHITELIST_SIZE`, `FocusModeManager`, Focus schedule tests | Verified |
| Quiet exemptions and 8 h return | `QuietFilter.kt`, `SNOOZE_MS` | Verified |
| Quiet caveat: sound may play | `quiet_card_hint`, said in the app too | Stated |
| Grayscale setup and limits | `grayscale_*` strings; Android 11+ for on-phone pairing | Verified in code |
| Guards only apply through Parem | `onboarding_guide_calm_body` says exactly this | Stated |
| Accessibility used only to lock | `accessibility_service_config.xml`: own package, click events, no window content. Consent sheet exists | Verified |
| Device-admin lock needs PIN | `lock_consent`, M4-WP18 | Verified in code |
| Tablets, foldables, 200% font, TalkBack, keyboard | Smoke CI on tablet and foldable; device pass partial (`v6.0.0-beta.2-validation.md`) | Partly on device |
| Private Space Android 15+ | M4-WP6 | Code; device state transitions marked pending in beta.2 validation |

**Wanted to claim, did not:**

- **Donations or a coffee link:** no mechanism exists, and held pending `compliance.md`.
- **"Nothing leaves your phone" or "collects no data":** false, see section 0, item 7. Note that in-app strings still say "Nothing is collected or sent anywhere", which `data-flows.md` section 5 flags as false with Android backup on.
- **"No trackers" in the short description:** true, but cut for the tagline; the full text says it.
- **"No tricks":** covered by "no catch", since it cannot be pointed at any one fact.
- **Battery under 1% a day:** the soak was deferred, with no pass on record.
- **E-ink support:** code exists, but physical e-ink is "Not verified" in beta.2 validation.
- **Any speed figure:** tablet typing still misses its jank target.
- **Full German or Estonian UI:** not true yet.
- **Sunrise and sunset themes, light and dark schedule:** true, cut for space.

The Accessibility text in the listing does not replace the in-app disclosure, which exists (M2-WP5). The Console declaration and demo video remain owner tasks.

## 8. GPL fork attribution

**In the listing.** Do not name the upstream app. Doing so would sit in search next to a live competitor, and Play's best practices warn against misleading references to other apps. The GPL does not require attribution in a store listing. The text credits the author ("the earlier open-source launcher by tanujnotes that Parem grew from") and points to the repository, which is honest without borrowing the brand. If you would rather drop the handle too, the repository link alone still meets the licence.

**Where the full name belongs:** `README.md` "Origins" (already there), the About page and the privacy policy page.

**What the GPL does require:**

1. Licence text with the source: `LICENSE` is present.
2. Section 5(a): prominent notices that the work was modified, with a date. README and git history cover this. A dated line in the Origins section would make it unarguable.
3. Section 5(d): interactive interfaces display Appropriate Legal Notices (copyright, no warranty, how to view the licence). The app has only a GitHub link, and `URL_ABOUT_PAREM` is empty. The clause exempts you if upstream's interface did not show them, which I did not check. Adding a short licence row when M2-WP3 lands the About row settles it.
4. Section 6: the Play build needs corresponding source available. Tag `v6.0.0` publicly from the exact commit you upload.

Keep upstream branding out of all assets: the old feature graphic and screenshots go, and the fork must not copy upstream's "Minimal AF" line.

## 9. Pages read

All read 2026-11-05.

**Limits and listing rules**

- Create and set up your app (name 30, short 80, full 4000): https://support.google.com/googleplay/android-developer/answer/9859152
- Best practices for your store listing: https://support.google.com/googleplay/android-developer/answer/13393723
- Metadata policy: https://support.google.com/googleplay/android-developer/answer/9898842
- Preview assets (graphics): https://support.google.com/googleplay/android-developer/answer/9866151
- Category and tags: https://support.google.com/googleplay/android-developer/answer/9859673
- Release notes, 500 characters: https://support.google.com/googleplay/android-developer/answer/9859348
- Available languages, referenced from fastlane: https://raw.githubusercontent.com/fastlane/fastlane/master/supply/lib/supply/languages.rb

**Related policy**

- Payments: https://support.google.com/googleplay/android-developer/answer/9858738
- User data and privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- AccessibilityService API: https://support.google.com/googleplay/android-developer/answer/10964491
- Impersonation: https://support.google.com/googleplay/android-developer/answer/9888374
- F-Droid metadata: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
- GPLv3: https://www.gnu.org/licenses/gpl-3.0.en.html

**Market**

- Play listings: `app.olauncher`, `com.minimo.launcher`, `com.qqlabs.minimalistlauncher`, `com.beforesoft.launcher`, `bitpit.launcher`, `com.joalmr.launcher`
- Play search for: minimal launcher, minimalist launcher, text launcher, launcher no ads, dumbphone launcher, digital detox launcher, minimalistischer launcher, launcher ohne werbung
- Review: https://pausegadget.com/best-minimalist-launchers-android/

**Internal, as of 13:01 on 2026-11-05**

- `docs/play/data-flows.md`, `web/privacy.html`, `ARCHITECTURE.md` (Play App Signing). `docs/play/compliance.md` did not exist yet, so anything marked pending it stays pending.
- Android's own Estonian and German terms, from AOSP `Launcher3` and `Settings` `values-et` and `values-de` at https://android.googlesource.com/

The Metadata policy page carries no revision date, so Play's rules could change: re-check at submission. No existing Play app called Parem turned up in a web search, but that is not a full clearance or trademark check.

The folder `de/` is kept as requested. It works for F-Droid, but Play's code is `de-DE`, so `fastlane supply` would need that name. Pasting into Console is unaffected.
