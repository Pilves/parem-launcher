# Colour and typography implementation — M4-WP50

Direction B uses the approved moss-teal palette, opaque sheet surfaces and unplated settings sections. Home keeps monochrome wallpaper text. Material You remains deferred.

| Theme | Pair | Contrast |
|---|---|---|
| light | text / surface | 15.81:1 |
| light | secondary / surface | 7.04:1 |
| light | secondary / variant | 6.28:1 |
| light | accent / surface | 5.84:1 |
| light | on_accent / accent | 6.36:1 |
| light | on_accent_container / accent_container | 11.06:1 |
| light | destructive / surface | 6.69:1 |
| dark | text / surface | 15.19:1 |
| dark | secondary / surface | 8.92:1 |
| dark | secondary / variant | 7.79:1 |
| dark | accent / surface | 10.70:1 |
| dark | on_accent / accent | 7.22:1 |
| dark | on_accent_container / accent_container | 7.25:1 |
| dark | destructive / surface | 10.49:1 |

Surface scrims use 230/255 (90%) opacity: the proposed 85% light scrim gives only about 4.14:1 for accent text over black wallpaper. At 90%, all text roles above pass 4.5:1 over either black or white wallpaper; solid surfaces also pass. E-ink uses opaque monochrome roles, outlined sheet controls and existing animation suppression, respecting an explicit dark-theme choice.

Typography follows width-qualified resources, six named text appearances and existing system font scaling. Tablet sheet width is capped by a resource. Wallpaper dimming is independent of daily wallpaper and is suppressed on e-ink. Ordinary captions use at least 14sp; existing graph paint sizes and graph geometry remain unchanged pending device review.

Source validation: every resource XML parses; existing accessibility attributes on identified views in both settings and drawer layouts are preserved. Removed drawable references were searched before deletion (proof in the QA directory). Night styles contain colour overrides only. No dependency was added.

Not verified: screenshot comparison, 200% font fitting, D-pad/TalkBack interaction, keyboard/inset layout, wallpaper compositing on devices, and e-ink rendering. These require the owner device pass; no device commands were run.
