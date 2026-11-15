# Photon 1.1.0.23 Beta

- Add All, Shorts and Full-length filters to the right of Refresh in Liked Music, on phone and TV. All is selected by default. Selection survives rotation; a new account/section starts with All. Refresh does not reset the selection.
- Filter only the existing native Liked Songs rows locally: no new Google request, mutation, classification or change to the working loader/cache. Full-length means non-Shorts, not a duration threshold. Preserve source order and stable committed portrait/landscape metadata.
- Retain explicit Shorts evidence from native Music row renderers. Do not guess Shorts from titles, duration or music genre. Unknown format uses the existing non-Shorts presentation until confirmed.
- Keep Refresh fixed at the left; filter chips can scroll horizontally on narrow screens/large fonts and remain remote-focusable. Empty filter results have a short message without hiding the controls. Playback queue behavior remains unchanged.

Hosted validation pending. Physical-device/private-account filter checks have not been performed.
