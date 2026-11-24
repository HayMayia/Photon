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

Version code 55. Published as `v1.1.1.0-beta` from commit
`b905c6b9d9ddac23c62607c4c883324895a9cb35`.

- Actions run 45343197794 passed: Android debug build, Photon unit/rendered tests,
  release build, permanent signing certificate verification and publication.
- Six local signing-safety tests and cumulative diff whitespace checks passed.
- Earlier Robolectric downloads were blocked by Maven HTTP 429. Using the public
  Google-hosted Maven Central mirror resolved the test-runtime download blocker;
  the complete configured Photon test suite then passed.
- Published `Photon.apk`: 25,534,159 bytes, GitHub asset 786064635.
- No physical phone/TV or private-account validation performed.

This is a design integration, not a claim of pixel parity for every prototype
screen or completion of the separate Windows/iOS ports.
