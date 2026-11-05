# Signing decision for Parem 6.0.0

**Recommendation for Patrick to decide:** use Play App Signing with a
Google-generated app-signing key and a new, separate upload key. Keep the
existing GitHub release key for the GitHub APK. No key has been generated,
exported, uploaded or changed as part of this recommendation.

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

**Before the first upload:** review the current Play Console choices and the
[Android signing documentation](https://developer.android.com/studio/publish/app-signing),
then decide and configure the key path. The current `release.yml` signs the
Play bundle with the same `KEYSTORE_*` secrets as the GitHub APK: its output
must not be uploaded for the recommended arrangement until the workflow is
changed to use separate upload-key secrets and tested. The manual debug CI
bundle uses an ephemeral debug key and is not an upload candidate. Confirm
certificate fingerprints and Android developer-verification requirements for
both distribution channels before launch.
