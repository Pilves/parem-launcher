# Estonian translation review

This is a draft, not native-reviewed. Please focus first on terminology and the permission explanations:

1. **Voice and labels:** Does “Rahu” work for Calm? Review “teadlik paus”, “vaikne loend”, “viibe”, and instructions such as “hoia sõrme peal” for long press.
2. **Consent and privacy:** Review the full notification-access explanation, device-admin and accessibility lock explanations, contacts, usage access and grayscale pairing. The notification draft follows PR30; final English and Estonian must match when integrated.
3. **Actions and examples:** Check “äratus 6.15 trenn”, “taimer 5 minutit” and “kohtumine reedel kell 15 hambaarst” sound natural. All three correspond to existing parser test cases; parser and device execution still need CI/device confirmation.
4. **Accessibility and layout:** Listen to TalkBack labels, especially times, counts and app limits; check singular and plural, long labels, and both orientations at 200% font.
5. **Full build and wording:** Check the Wireless debugging pairing steps against Estonian Android labels. Android names can vary between devices. Verify revised grayscale text once WP54 is final.

Coverage and known English fallbacks are summarized below. Other shipped locales have substantial remaining English fallback; none is claimed complete.

## Integration and coverage

This is a draft pending PR30 and PR33. Six baseline keys intentionally fall back to English until their final English source lands: `quiet_card_hint`, `quiet_disclosure_body`, `quiet_needs_access_body`, `quiet_keep_info`, `onboarding_guide_quiet_body`, and `grayscale_toggles_hint`. Seven PR33 quick-action labels are staged separately and must be translated when those labels land. Do not merge this work package as complete until these are reconciled and reviewed.

Of 421 baseline main strings, 385 have Estonian drafts; 30 invariant identifiers, numeric values or fixed examples use the base resource, and six keys await the changes above. The seven-item omnibox array has an Estonian draft. All 17 full-flavor strings have drafts. These are static source counts, not proof of packaged resources. Both the main and full locale resources need CI and device checks. Existing locales `ar`, `de`, `es-rES`, `es-rUS`, `fr`, `he`, `hr`, `hu`, `in`, `it`, `ja`, `pl`, `pt-rBR`, `ru-rRU`, `sv`, `tr`, `uk` and `zh` have extensive English fallback; WP51's new labels and other missing translations must be tracked separately before release.
