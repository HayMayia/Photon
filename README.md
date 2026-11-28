# Photon

## Original Photon — current maintenance target

The phone/tablet and Android TV app currently being maintained is **`old-photon/`**,
package **`io.github.haymayia.photon`**. The latest published original release is
**1.1.1.7 Beta**; subsequent audit changes on this branch are not a new release.
Open the entire repository in Android Studio, using JDK 21 and the SDK requested
by Gradle, then build:

```sh
./gradlew :old-photon:assembleDebug
./gradlew :old-photon:testDebugUnitTest
```

For an independently installed development copy, add `-PsideBySide`. Locally
debug-signed builds cannot necessarily update the officially signed installation.
Do not uninstall or clear a populated installation to work around that error.

See [the architecture audit](docs/ARCHITECTURE_AUDIT.md) for confirmed defects,
repairs, actual verification and explicitly unfinished UI/power-tool requirements.
Internal `com.prism.music` packages are retained upstream APIs, not the app ID.

## Separate supplied Firebase/WebView app

The **user-supplied “Photon v1.1.1.0” Compose project** is retained independently
in **`app/`**; it does not replace the original Photon installation.
All supplied screens, components, artwork, Firebase account/cloud repositories,
YouTube repository, preferences and WebView player are used.

## Build

Use Android Studio or Java 21 and the Android SDK:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The repository's working Gradle wrapper/AGP/Compose compiler and permanent CI
signer are retained. The AI Studio debug/upload keystores were not supplied and
are not required. There are no Gemini API calls in the imported source, so the
unused Secrets plugin and Gemini key instructions were not imported.

## Important: app identity and Firebase setup

The supplied application's ID is **`com.aistudio.photon.qrxmzk`**, not the previous
`io.github.haymayia.photon`. It installs separately; it is **not an in-place
update** and does not migrate the old app's session or library. Keep the previous
app installed until you have tested this replacement.

The supplied `google-services.json` contains a public Android Firebase client
configuration, not server/admin credentials. Its registered Android certificate
is different from this repository's permanent signing certificate. The Firebase
project owner must register the new signing SHA-1 for this application:

```
23:FE:D9:B2:C2:37:F4:C3:B7:CB:67:A7:71:D0:32:56:E3:4A:EB:36
```

Use Firebase Project settings → Your apps → the Android app with the exact package
above → Add fingerprint. Confirm the Google authentication provider is enabled,
re-download `google-services.json` if the console changes it, and confirm the
named Firestore database in `firebase.json` exists with the supplied rules.
No project settings or Firestore rules are deployed by this repository's build.
Do not send private credentials or service-account keys in chat.

Until the project owner completes that setup, Google login and cloud data access
are **not verified**. Compilation alone does not prove these services work.

## Historical replacement import / recovery

The original replacement import replaced the previous Android implementation, including its
native player, updater, downloads, Google account/session integration and TV UI.
Those previous features are not silently retained or claimed as supported by the
supplied app. Its library uses Firebase/Firestore; that is not equivalent to all
mutations being written directly to YouTube.

The last working previous-engine release is `v1.1.1.0-beta` at commit
`b905c6b9d9ddac23c62607c4c883324895a9cb35`. Its source and APK remain available in
GitHub history/releases. A revert can restore that implementation without
rewriting history. The legacy baseline-profile module is excluded from this
replacement's build; separate native-port folders are historical, not parity
claims for this supplied app.

See `docs/RELEASE_1_1_1_1_BETA.md` for import provenance and validation status.

## Original Photon maintenance

The original non-Firebase Photon is maintained separately in `old-photon/`.
Build with `./gradlew :old-photon:assembleDebug`. Its package remains
`io.github.haymayia.photon`, so signed releases update the existing original app
without deleting its data. See `docs/RELEASE_1_1_1_2_BETA.md` for the Next-button
and speaker/headset music-recovery update. The supplied app in `app/` is unchanged.
