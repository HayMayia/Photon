# Upstream attribution and modifications

Photon is a modified distribution of **Prism**, copyright (C) 2026 Yigder,
licensed under GNU GPL version 3 or (at your option) any later version.

- Source: https://github.com/Yigder/Prism
- Imported revision: `4a10666cfaff7114d9a53088e0adf393aebd2463` (main, retrieved 2026-10-29).
- The original license is preserved in `LICENSE`.
- Original project README and contributor guidance are preserved in `docs/upstream/`.
- Original Kotlin packages are retained to avoid breaking resource/code references;
  the installed application ID is independently `io.github.haymayia.photon`.

## Photon modifications (2026-10-29)

- Added `app/src/main/java/com/prism/music/photon/`: video-only application entry,
  standard YouTube extraction/search/pagination, media-session video player, local
  saved/history library, quality selection, on-device adaptive HD MP4 downloads,
  settings and Prism-themed Compose screens.
- Replaced the active manifest: no music account sync, music background jobs,
  recognition microphone/camera permissions, APK installation permissions, or
  upstream launcher aliases. The upstream music screens are retained as reference
  source but are not the active application.
- Renamed the installed app, application ID, project and version. Added a distinct
  Photon launcher icon. Redirected the inactive inherited updater's repository
  constant so it cannot offer Prism releases if reused later.
- Added an Android APK build workflow, preview documentation, and focused tests.
- Resolved upstream SDK 37 to its available explicit minor release 37.2 and
  capped build worker/daemon memory for hosted CI.

## Other works

Prism's source retains its own third-party license descriptions, including
`app/src/main/assets/ejs/` and bundled fonts. See the original README for the
upstream dependencies and credits. NewPipe Extractor is GPL-3.0-or-later;
AndroidX/Media3 are Apache-2.0; Kotlin, Compose, Coil, OkHttp and other inherited
libraries retain their respective licenses. Font license files are kept alongside
the original assets. No music, video, thumbnails or other YouTube media are
redistributed by this repository.

Photon is an independent project, not endorsed by Prism, Google or YouTube.
When distributing an APK, provide its corresponding source under GPL-3.0-or-later,
including modifications and build files. Keep copyright and license notices.

## 0.2 expansion

Photon adds standard-YouTube channel/public-playlist/comment extraction, local
subscriptions and playlists, a vertical Shorts browser, service-owned watch
progress/resume, local related-video recommendation ranking, and independently
wired LAN video casting. `GoogleCastLink` metadata now distinguishes video from
its unchanged music metadata path. The Photon manifest declares LAN/multicast
permissions and allows HTTP for dynamically addressed UPnP TVs; casting targets
are explicitly validated as LAN addresses. No authenticated Google account client
or live recorder is claimed. Device testing remains separate from unit/build checks.
