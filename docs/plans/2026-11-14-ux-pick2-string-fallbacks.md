# UX-PICK-2 string fallbacks

`picker_section_selected`, `picker_no_other_apps_match` and `picker_result_count`
are translatable in base English. Estonian has the PO-provided `Valitud (%1$d)`
for `picker_section_selected`; all other non-English locales fall back to English
for that key. `picker_no_other_apps_match` and `picker_result_count` fall back to
English in all 19 non-English locales pending confident translation and review.

New `picker_current_app` and `picker_clear_search` are translatable base English
and fall back to English in all 19 non-English locales. No other translation is
asserted. Native review and the physical-device language walk remain outstanding.
