# Photon 1.1.0.22 Beta

- Add the published dependency `implementation("io.github.kyant0:backdrop:2.0.1")` (Kyant0 AndroidLiquidGlass release 2.0.1), not a placeholder version.
- Photon remains a native Jetpack Compose app. Register Kyant LayerBackdrop sources in the phone page and TV background; consume them with drawBackdrop, blur and lens in the shared GlassSurface. This switches Photon navigation, mini-player and TV glass rail to the requested renderer.
- Keep theme-aware contrast tints, shape clipping, existing click/remote semantics and Performance mode. Only one renderer captures each Photon backdrop, not Haze and Backdrop together. Separate upstream Prism screens retain their existing Haze integration.
- Gate effects to hardware rendering on Android 12+; Backdrop itself gates lens refraction to Android 13+. Android 8–11, software rendering, glass-disabled and Performance mode keep readable solid surfaces. Performance mode and disabled glass skip page backdrop capture.
- Add Kyant0/Backdrop/Shapes attribution to open-source licenses. Existing Apache 2.0 license text is included in the app.

Hosted validation pending. Physical-device GPU appearance/performance and TV rendering have not been verified. This is the Android Compose library, not Apple's platform API. No account/music loader or background-playback policy changes in this release.
