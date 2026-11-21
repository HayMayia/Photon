# Photon 1.1.0.23 Beta

- Add All, Shorts and Full-length filters to the right of Refresh in Liked Music, on phone and TV. All is selected by default. Selection survives rotation; a new account/section starts with All. Refresh does not reset the selection.
- Filter only the existing native Liked Songs rows locally: no new Google request, mutation, classification or change to the working loader/cache. Full-length means non-Shorts, not a duration threshold. Preserve source order and stable committed portrait/landscape metadata.
- Retain explicit Shorts evidence from native Music row renderers. Do not guess Shorts from titles, duration or music genre. Unknown format uses the existing non-Shorts presentation until confirmed.
- Keep Refresh fixed at the left; filter chips can scroll horizontally on narrow screens/large fonts and remain remote-focusable. Empty filter results have a short message without hiding the controls. Playback queue behavior remains unchanged.

Hosted validation passed: push 43729241842 and PR 43729246746, including compilation, unit tests, signing verification and publication. New regressions cover source-order-preserving filters, stable format correction, native Shorts markers, All selection by default, filter placement to the right of Refresh and chip switching. Release source 2cdd3995f3ecf1d56cede96ae7a2b6dd5a665f5b, version code 52; asset 751413446 is 25,484,080 bytes, SHA-256 a70874c1787d2faf868d7e001b0aa1e0132dc913ad3534646986fe8606569286. Physical-device/private-account filter checks have not been performed.
