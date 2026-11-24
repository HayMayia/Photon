# Photon 1.1.1.1 — full supplied-project replacement

## Decision and scope

The user explicitly selected **“Use the supplied app as-is”**, including its
Firebase login, cloud library and WebView player, and confirmed the ZIP is the
behaviour reference. This supersedes the partial design integration for Android.

## Imported

All 22 supplied main Kotlin files, all supplied Android resources and artwork,
manifest, tests, public Firebase client configuration, Firestore rules and
blueprint from Drive ZIP `Photon v1.1.1.0`. Original archive SHA-256 is recorded
below. No cloud rules or project settings have been deployed.

Only build integration differs: retain the proven wrapper/AGP/Compose compiler,
permanent signing setup, version 56 / 1.1.1.1 Beta, CI dependency mirror, and
remove unused Secrets/KSP build plugins and nonexistent AI Studio keystores.
Inter's OFL license is retained. Baseline-profile journeys for the previous app
are excluded rather than executed against unrelated screens.

The original application ID `com.aistudio.photon.qrxmzk` is retained; this installs
separately and must not be offered as the previous package's automatic update.
Preview publication therefore uses a different asset name and explicit trigger.
The previous production Beta remains available and untouched.

## Known external setup blocker

The supplied Firebase Android OAuth client registers a different signing SHA-1.
The project owner must register the repository's permanent signing certificate
for this exact package, enable Google Auth and verify the named Firestore database
and security rules. See README. Google login, cloud access and playback have not
been account/device-tested. A successful build is not proof of those services.

## Validation

Hosted Actions run **45344792329** at source commit
`26bc2a02314a26676496a0efaf5f193b95387d63` passed Android debug assembly,
all imported/new `com.example.*` unit tests (including the Robolectric resource
test), and permanent signing certificate verification. Build artifact:
`Photon-Android-APK` on that run. Six local signing-safety tests also passed.

An exact-byte comparison confirmed all **49 supplied main-source, resource and
manifest files** match the ZIP. Additional model tests and the Inter license are
separate additions. No physical-device, Firebase account or playback validation
has been performed. No release publication until Firebase setup is resolved.
4582bc3cccf1bc4ce3febd4f3f2fd3306c5142d1b80ba697ba360143e7a47ef0  artifacts/Photon-v1.1.1.0-original.zip
