# Photon 1.1.0.15 Beta

- Enable Prism's existing Haze Liquid Glass renderer in Photon by default, with a persistent Appearance → Liquid Glass switch. This is the actual shared refractive renderer, not a renamed opaque card.
- Floating rounded phone navigation and mini-player use shared GlassSurface optics and a registered page backdrop. Navigation retains system insets and all playback controls.
- TV navigation receives a rounded glass rail over a restrained accent gradient; remote focus outlines and selected-section behavior remain intact. No video-surface blur or continuous decorative animation is introduced.
- Optional YouTube layout retains its native navigation and colors. Disabling Liquid Glass uses Prism's readable solid fallback.

Hosted validation passed: push run 43618129513 and PR run 43618134304, including compilation, unit tests and registered signing certificate verification. Published release source ec47618ee75617049833768f3ac13bec9015d67f, version code 44; asset 748156954 is 25,402,156 bytes with SHA-256 cde621a9a6ee6c506ed4b096733f649bf9298c805b813bb67faf4a748e810c7c. Physical phone/TV GPU appearance, performance, and accessibility have not been device-tested. Rendering support varies with Android/GPU; the existing Prism/Haze renderer controls platform fallback.
