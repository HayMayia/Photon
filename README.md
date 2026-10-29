# Photon

**Your world, refracted.** A YouTube-video Android client built on
[Yigder's Prism](https://github.com/Yigder/Prism), retaining its real Compose theme,
fonts, spectrum accents and hand-drawn icons, adapted to widescreen video.

> **0.1.0 alpha — work in progress, not a finished Prism-equivalent release.**
> Source has been implemented; a successful APK build and device testing must be
> confirmed before calling this release usable. See [testing](#testing).

## Implemented in this preview

- Ordinary YouTube video search, topic discovery and paginated results (not YT Music).
- YouTube watch/Shorts/live links, including Android's Share → Photon.
- Video player with seeking, background media session, lock-screen/headset controls,
  full-screen landscape, explicit picture-in-picture and a mini-player.
- Available MP4 quality selection, preferring up to 1080p by default.
- HD downloads where compatible source streams are available. Separate AVC video
  and AAC audio are downloaded and merged **on the device** into one MP4 using
  Android's MediaMuxer. No server or FFmpeg binary. No silent quality substitution.
- Download queue/progress/cancellation/error messages and optional unmetered-network
  constraint (on by default). Offline playback and deletion of downloaded copies.
- Local saved videos and a 100-entry watch history; light/dark appearance and quality preferences.
- Distinct Photon branding and application ID, installing alongside Prism.

No account, API key or Photon server is required. YouTube may refuse extraction
on some networks or for some videos. No DRM, paid-access or sign-in bypass.
Download only content you own or have permission to save, and follow applicable
terms and laws.

## Get an APK

1. Open this repository's **Actions → Build Photon APK**.
2. Open the latest successful run on branch `arena/c217a41b-photon`.
3. Under **Artifacts**, download **Photon-Android-APK** (GitHub sign-in required).
4. Extract the ZIP, transfer `app-debug.apk` to your Android phone, and open it.
5. Allow installation from that browser/file manager if Android asks.

Artifacts expire after 90 days. Keep a local copy of the APK and source ZIP.
A workflow can also be started using **Run workflow**. If the workflow is red,
there is no verified APK yet: inspect its build log rather than installing an
unrelated Prism release. The sandbox cannot compile locally: it has no JDK/SDK
and Maven/Google/Gradle download hosts are network-restricted.

Debug builds are for evaluation. GitHub runners may generate different debug
signing keys on different runs, which can prevent installing a later APK over
an earlier one. Before a public release, configure a persistent private signing
key (never commit it) and build a signed release.

## Build locally

Use JDK 17+, Android SDK platform 37.2, and internet access to Google Maven, Maven
Central, Gradle and JitPack. The inherited upstream dependency versions are pinned
in the Gradle scripts.

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Testing

Focused Photon unit tests (no YouTube network required):

```sh
./gradlew :app:testDebugUnitTest --tests 'com.prism.music.photon.*'
```

[Device acceptance checklist](docs/TESTING.md). Inherited Prism tests also remain,
but many exercise live music/lyric services and are not a Photon acceptance suite.

## Important limitations

- No Google login, subscriptions, channel pages, comments, Shorts feed, playlists,
  casting or account sync in this first preview.
- Topic discovery uses explicit YouTube searches, not a personalized recommendation
  algorithm. Saved videos are local bookmarks, not YouTube likes.
- Only available progressive MP4/AVC + AAC streams are offered. A video may have no
  compatible format. No guaranteed 1080p/4K for every video; device codecs also vary.
- Live HLS playback is offered if extraction provides it; live downloads are not.
- Downloads restart rather than resume partial files after interruption. Android
  may impose background-work/time/battery restrictions on large downloads.
- Downloads are app-private and **removed when Photon is uninstalled**. Export to
  public storage is not implemented. Thumbnail images are cached, not guaranteed
  offline; offline video files include audio.
- History records opening a video, not exact watched duration or resume position.
- Device playback, full-screen/PiP, HD muxing and background lifecycle require
  real-device testing. A source implementation is not proof of working playback.

## Code map

Active Photon code: `app/src/main/java/com/prism/music/photon/`.

| File | Purpose |
|---|---|
| `PhotonApplication.kt` | Lightweight app startup, no inherited music jobs |
| `VideoRepository.kt` | Standard YouTube search, link parsing, extraction and qualities |
| `PhotonActivity.kt` | Prism-themed video interface, player surface and controls |
| `PhotonPlaybackService.kt` | Media3 session and audio/video merged playback |
| `VideoDownloadWorker.kt` | Download foreground work, progress and MP4 muxing |
| `PhotonStore.kt` | Device-local library/settings persistence |
| `PhotonViewModel.kt` | Retained screen state and user operations |

The original Prism code is preserved for design reuse and future work, but the
manifest launches **only Photon**. Music-only features are not advertised as
Photon features. Future cleanup can remove unused inherited dependencies.

## License & credit

**GPL-3.0-or-later**, as required by the upstream project. Original Prism copyright
(C) 2026 Yigder. See [LICENSE](LICENSE), [UPSTREAM.md](UPSTREAM.md), and the
[original README](docs/upstream/README.md). This is an independent modified app,
not an official Prism product and not affiliated with Google/YouTube.
