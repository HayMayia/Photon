# Android TV and shared player settings (0.13)

## TV presentation and remote controls

Photon detects television UI mode or Android television/leanback features and selects a separate ten-foot sidebar/grid interface automatically. Phone layout preference is preserved but does not override TV detection. The manifest exposes a Leanback launcher entry and a 320×180 banner; touchscreen and leanback are optional so the same package continues to install on phones.

TV offers remote-focusable Home, Search, subscriptions, Shorts, account library, downloads and settings. Cards/buttons have visible focus rings; search uses the system TV keyboard. The saved TV browsing state survives opening playback. Downloads use their saved artwork when available and expose a confirmed delete action. Account/library/channel actions continue to use the existing backend, not simulated local account data.

Opening a TV video enters fullscreen. **Back: fullscreen → video details → TV browser**, before normal tab/root Back handling. TV landscape is not itself a permanent fullscreen trigger, unlike mobile auto-rotation. Details have an inset-safe two-column player/information layout, player settings, captions, likes, playlists, channel/subscription access, casting, queue advance when available, download/comment actions and related videos. Changing view does not rebind/restart the media item.

Native Media3 controls remain keyboard/remote operable. A Select/DPAD-center hold can temporarily set 2× when playback is running and controls were hidden; release restores the previous parameters without activating the focused play button. Holding an already focused visible control is not repurposed. Mobile long-press retains the touch-control exclusion and cancellation behavior. The badge says only **2×**.

## Shared TV/mobile settings

The player gear opens the same settings dialog on TV, mobile and the Shorts surface:

- Available extracted playback qualities; switching uses the existing position-preserving path. Download quality remains a separate download choice.
- Speed presets every 0.25× from **0.25× to 10×**, including **1× (normal)**. A slider offers **0.1×–10× in 0.1× increments**. Live/unsupported speed control is disabled rather than faking a change. Extreme-speed playback depends on decoder/device/source performance.
- Repeat current video (Media3 repeat-one), independent of the application playlist queue.
- Captions on/off, Automatic and the currently available supported language tracks. No unavailable translation is invented. A fallback selects an available text track when an enabled caption stream has no default selection.
- Caption-size presets **25%, 50%, 75%, 100%, 125%, 150%, 175%, 200%**, plus a **25–200% slider in 1% steps**. 100% is Media3's fractional viewport baseline. Embedded font/styles are overridden so the chosen size and appearance apply consistently.
- White-on-black, yellow-on-black or outlined white caption appearance.

A separate CC/captions control remains outside the gear; its focused dialog also exposes caption choices. Speed, repeat and caption preferences persist locally on each device, not across devices or in Google account settings. Slider drafts are displayed during adjustment and stored at adjustment end to avoid rewriting the entire library on every drag frame. Temporary 2× is not persisted.

Routine action labels no longer say “on YouTube.” Short confirmations for real account mutations/destructive actions remain, including unlike. The unrelated history-reporting opt-in is no longer embedded in every account-change dialog; its explicit Settings → History & privacy consent remains available, with existing Google pause/staleness protections unchanged. Legal notices, errors and genuine app/Google identity are retained.

## Verification boundaries and pending device checks

Four new synthetic tests cover quarter-step presets/range clamping, subtitle presets/fine values, TV versus mobile fullscreen policy, and old-install defaults/preference serialization. They do **not** execute real DPAD routing, native Media3 dialogs, caption rendering or decoder performance. Build/signing verification is recorded in TESTING.md.

This is TV support, **not a 100%-of-TV-models certification or pixel-identical official YouTube TV UI**. Physical checks remain necessary:

- Install-over-existing, phone launcher and TV banner/launcher; TV detection on Android TV/Google TV vendor devices.
- Entire basic browse/search/playback journey with only DPAD/Select/Back: focus visibility, scrolling, restoring the opened card, dialogs/TV keyboard and root exit confirmation.
- Back from fullscreen shows details without stopping playback; another Back returns to browsing. Check captions/controls against overscan, display cutouts and font scaling.
- Gear/CC on phone, TV and Shorts; available qualities, paused quality switches, repeat-one versus queue advance, caption toggle/languages/styles/size extremes and 1% slider changes.
- All speed presets, 0.1-step slider, audio/video sync at extremes, unsupported/live behavior, temporary boost release/cancel/pause/next/rotation and return to prior speed.
- Offline artwork/playback/deletion, account confirmations, channel/playlist access, casting and comments. Google sign-in/WebView, native comment authorization and system keyboard availability remain subject to Google/device restrictions; TV presentation does not approve or bypass those restrictions.


## Build record

Source `887f36827ba0a49d3875c2e936310a4c47e2e0d0`, 0.13.0-alpha/code 16: [push run 41216748013](https://github.com/HayMayia/Photon/actions/runs/41216748013) passed in **4m56s**, including assembly, focused Photon tests (174 methods in source), permanent signing-certificate check and upload. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/41216748013/artifacts/13048367111), **31,417,451 bytes**. Same APK for mobile and TV; install over the existing application. Six local signing tests and 92 XML/SVG parses passed. The physical checklist above remains pending.
