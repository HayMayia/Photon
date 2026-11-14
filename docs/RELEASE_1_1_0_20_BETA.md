# Photon 1.1.0.20 Beta

- Refresh the native TV layout with icon-labelled glass navigation, clearer typography, theme-matched selection and a dedicated Resume action in the section header.
- Home has a stable-height spotlight preview showing the currently focused video's title, creator and video/music/Shorts type. It does not autoplay, steal focus or reorder recommendations.
- Poster cards receive softer rounded frames, clipped artwork and stronger title weight while preserving equal title slots, landscape/contained-Shorts presentation and visible remote focus outlines.
- Show source notices (including paused Google history), empty downloads and search guidance rather than unexplained blank grids. Failures have a remote-focusable Retry action.
- Preserve all native sections, Google account sources, fullscreen Back/details behavior and the independent TV music-background option, OFF by default.

Hosted validation passed: push 43626528104 and PR 43626534785, including compilation, unit tests, registered signing verification and publication. TV regressions cover remote card navigation/activation, stable non-actionable preview updates and the independent background-music switch. Release source 8c80ad3c1a5ea65415c1907db97d9419de2ffde4, version code 49; asset 748405484 is 25,402,160 bytes, SHA-256 87c27fcfadc20773ad48b9d6d0329870c576ede7e615c0b616f417f106efafb7. Physical TV rendering and remote lifecycle have not been device-tested. No claim of resolving private-account Music authentication beyond the preceding protocol repairs.
