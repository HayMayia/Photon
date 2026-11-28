# Original Photon 1.1.1.7 Beta — fix the service/controller event path

Version code 62. Same original package and signing identity. Install over Photon;
do not uninstall or clear account/download data. The TV sidebar now shows the
installed version; it must say **1.1.1.7 Beta · TV** for this update.

## Actual diagnosis, not another surface-only guess
A real Android API 30 emulator reproduced a service/controller mismatch using a
local AVC/AAC clip: service READY/playing, controller IDLE/0:00/no tracks, available
texture, no first-frame callback (run 46174734538). No YouTube network request was
involved. The queue adapter's Kotlin listener delegation dropped state callbacks.

Replaced that adapter with Media3's coherent state forwarding while retaining all
four queue Next/Previous commands. The same real-render check then passed in run
46175212800: first-frame callback **and nonblack decoded video pixels** through
PhotonPlaybackService/MediaController beneath Compose, plus remote Back routing.
This corrects the event path that left controls/shutter stuck even while the
service played. It does not claim every TV/codec has been physically verified.

## Other changes
- Activity-level TV Back exits playback in one press before a native child can
  swallow it. Pause, close the presentation and invalidate pending navigation.
- Cancel superseded synchronous extraction HTTP calls; previously coroutine
  cancellation could leave their sockets running. Bound stream lookup at 30s
  with an explicit retry message instead of an endless spinner. Public YouTube
  startup has not been benchmarked on the reporting device/network.
- New dark-navy/cyan light-orbit icon, amber accent, Android themed-icon support,
  and matching TV launcher banner. No change to the separate supplied app.
- Retain source-bounded Subscriptions/Shorts, recommendation-off behavior and
  OK-to-adjust speed controls. No unrelated phone redesign.

## Validation
Final versioned checks passed on source `ebe68b3e409c6b64bb5fff030384aa906a296d63`:
- [Real decoded-frame/Back emulator check 46175827262](https://github.com/HayMayia/Photon/actions/runs/46175827262).
- [Original build/unit/signing/release check 46175827287](https://github.com/HayMayia/Photon/actions/runs/46175827287).

Publication was gated on the successful real-frame check for that exact source,
not just a successful compile. [Download Photon.apk](https://github.com/HayMayia/Photon/releases/download/v1.1.1.7-beta/Photon.apk)
(24,626,288 bytes; non-draft release and uploaded asset verified). The debug probe activity and local fixture are not in the release APK.

After installation verify the new icon and version label, open the same video,
check actual picture/clock and press Back. If the picture is still black on the
physical TV, provide TV model, Android version and the app's diagnostics report;
manufacturer-specific rendering/decoder failures remain possible.
