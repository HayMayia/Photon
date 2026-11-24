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

Version code 55. No APK publication requested for this change. Android compilation
passed in Actions run 45341656164 at commit 4473bc2. Its unit/rendered-test
stage was blocked before execution by Maven HTTP 429 downloading Robolectric
nativeruntime-dist-compat 1.0.16. A narrowly scoped dependency-rate-limit retry
has been added; test validation remains pending until the new run completes.
The six local signing-safety tests passed. No physical phone/TV or private-account
validation performed. This is a design integration, not a claim of pixel parity
for every prototype screen or completion of the separate Windows/iOS ports.
