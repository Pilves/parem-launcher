# Signing decision for Parem 6.0.0

**Decided 2026-11-06:** Play App Signing with a Google-generated app-signing key
and a new, separate upload key. The existing GitHub release key stays for the
GitHub APK and is not shared with Google.

Upload key: RSA 4096, alias `parem-upload`, valid to 2076. Certificate SHA-256
`D3:FC:30:BC:93:17:90:5D:CD:74:C3:98:55:DA:71:A7:F3:EF:33:85:75:FF:96:7E:0E:3A:E2:0F:B0:02:F4:94`.

The Play signing certificate and the GitHub certificate would differ. Android
will not accept a cross-channel update signed by a different certificate: users
moving between these channels need to export settings, uninstall, install the
other build and import settings. Uninstalling removes app data, so verify the
export before uninstalling; exports do not promise to restore Android grants or
widget bindings.

The upload key signs the bundle sent to Play; Google signs distributed APKs
with the Play app-signing key. Keeping the upload key separate limits exposure of
the existing GitHub key and permits an upload-key reset if needed. Keep a secure,
offline backup of both self-managed keys and their credentials. Never commit
keystores or secrets.

**Historical pre-upload plan:** check Play Console choices against the
[Android signing documentation](https://developer.android.com/studio/publish/app-signing)
and choose a Google-generated key for Play App Signing. Parem is now in Play
closed testing; check the current Console signing configuration and certificate
fingerprints before any further upload rather than assuming that plan was applied.
`release.yml` signs the Play bundle with the `UPLOAD_KEYSTORE_*` secrets and the
GitHub APK with `KEYSTORE_*`. Confirm the bundle's certificate matches the upload
certificate before uploading. The manual debug CI bundle uses an ephemeral debug
key and is not an upload candidate. Verify Android developer-verification
requirements for both distribution channels as well.
