# Photon 1.1.1.0 Beta — supplied Compose design integration

## Source

Design supplied by the user as Drive ZIP `Photon v1.1.1.0` (3,563,821 bytes).
The incoming project is a separate `com.example` AI Studio Compose application, not
an upgrade of Photon's existing source. Its flat AMOLED palette, 12dp card shapes,
Inter typography and mode-switcher layout have been ported into the existing app.
The original archive remains an ignored local artifact, not a second app module.
Inter is distributed with the SIL Open Font License in assets/licenses/inter.txt.

## Changes

- Apply the supplied AMOLED theme and Inter scale throughout Photon dark mode;
  retain the existing light-mode preference and independent YouTube layout.
- Replace the large greeting/search hero and horizontal recommendation carousels
  with a compact fixed header, Videos/Music selector, quick destinations, compact
  playlist shelf and vertically accessible account Home recommendations.
- Music opens genuine native Liked Music using the existing cache, All (default),
  Shorts and Full-length filters and same-playlist playback preparation.
- Home preserves server ordering and shows all rows, with two portrait Shorts
  maximum per row rather than hiding recommendations in nested carousels.
- Flatter compact playlist entries, mini-player and navigation; teal active
  navigation. Keep the assigned playlist shortcut and long-press chooser.
- TV uses flat surfaces and 12dp cards with the existing explicit remote focus
  behavior; independent background preferences remain unchanged.
- Retain Kyant Liquid Glass and Performance mode; omit the old gradient canvas.

## Deliberately not imported

The prototype's Firebase identity, Firestore library and WebView playback engine
are not replacements for the working Google session, account mutations and native
player. Prototype search queries labeled as personalized recommendations are not
used. No extra Google/Firebase login or user-owned cloud project is required.
Sample album covers and fabricated playlists are not shown as account content.

## Validation

Version code 55. Beta publication requested after the user reported the update was missing.
Publication remains gated on Android build, tests and permanent signing identity verification.

- Android compilation/assembly passed, most recently in Actions run 45342698042
  at commit e2227b5.
- Six local signing-safety tests passed; cumulative diff whitespace checks passed.
- The full unit/rendered suite is **not verified**: Maven repeatedly returned HTTP
  429 downloading Robolectric native and instrumented Android SDK runtimes. This
  persisted after a dependency retry and switching from the legacy repo1 host to
  canonical Maven Central. The latest diagnostic names Android 9 runtime
  `9-robolectric-4913185-2-i7`; earlier attempts also named Android 14.
- No physical phone/TV or private-account validation performed.

This is a design integration, not a claim of pixel parity for every prototype
screen or completion of the separate Windows/iOS ports.
