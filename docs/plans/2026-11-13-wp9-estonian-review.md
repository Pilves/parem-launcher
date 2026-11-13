# Estonian review for Patric

These translations are AI drafts; please read them natively before this PR is marked ready. The broader locale drafts are also machine/AI generated and have not received native review.

1. **Voice and labels:** Does “Rahu” work for Calm? Review “teadlik paus”, “vaikne loend”, “viibe”, “hoia sõrme peal” for long press, and the selected-app count.
2. **Consent and privacy:** Read the notification-access disclosure, including the app-chosen tag, snooze time and best-effort release. Check the lock accessibility and device-admin explanations, usage access, contacts, weather and grayscale pairing.
3. **Search and quick actions:** Check the wording of “äratus 6.15 trenn”, “taimer 5 minutit” and “kohtumine reedel kell 15 hambaarst”; existing parser tests cover those exact examples. The guide uses “halltoonid” and “privaatruum” to match the localized search labels.
4. **Accessibility and layout:** Check TalkBack labels, counts and time expressions at one and multiple items, 200% font, and both orientations. Verify the final picker count and its meaning in Focus, folders and quiet notifications.
5. **Full flavor and terms:** Check Wireless debugging pairing instructions against Estonian Android labels, which may vary by device, and the explanation that Parem tries to restore the prior color correction when its grayscale triggers end.

`2026-11-13-wp9-meaning-review.md` records the sentence-level AI review scope and final guide/disclosure hashes. Both flavors, app-specific and system Estonian locale, TalkBack and device layout remain to be verified on the integrated build. Existing locales may still fall back to English for older strings outside this work package.
