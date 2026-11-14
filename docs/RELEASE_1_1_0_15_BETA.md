# Photon 1.1.0.15 Beta

- Enable Prism's existing Haze Liquid Glass renderer in Photon by default, with a persistent Appearance → Liquid Glass switch. This is the actual shared refractive renderer, not a renamed opaque card.
- Floating rounded phone navigation and mini-player use shared GlassSurface optics and a registered page backdrop. Navigation retains system insets and all playback controls.
- TV navigation receives a rounded glass rail over a restrained accent gradient; remote focus outlines and selected-section behavior remain intact. No video-surface blur or continuous decorative animation is introduced.
- Optional YouTube layout retains its native navigation and colors. Disabling Liquid Glass uses Prism's readable solid fallback.

Validation pending. Physical phone/TV GPU appearance, performance, and accessibility have not been device-tested. Rendering support varies with Android/GPU; the existing Prism/Haze renderer controls platform fallback.
