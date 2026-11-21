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

## 0.3 refinements

Photon Home now directly uses Prism's `SectionHeader`, typography, shape and
spectrum tokens, with shortcut tiles and distinct music/video shelves. The original
music Home is not misrepresented as a general-YouTube API. AAC soundtrack selection
prefers `ORIGINAL` over bitrate, excludes explicit dubbed/descriptive fallbacks, and
filters embedded video-source audio when merging a chosen original track. Offline
artwork is saved as bounded, validated image files beside videos; repair work can
backfill older downloads. Accent and new thumbnail fields preserve old-data defaults.


## Photon 0.4 account/comment additions

Native Google Play services AuthorizationClient requests youtube.force-ssl; official YouTube Data API calls verify the connected channel and target video's uploader channel before explicitly confirmed top-level insertion. The target snippet.channelId is not confused with the author's channel. Tokens are memory-only, POST retries/redirects disabled, and only server comment IDs confirm posting. No upstream password/cookie collection or authenticated extractor was added. Cloud registration and device tests remain required; see docs/GOOGLE_SIGN_IN.md. Prior read-only-account limitations above describe 0.2/0.3, not this new optional authorization flow. Full account sync, likes and reply posting remain unimplemented. API protocol reference: https://github.com/youtube/api-samples/blob/master/python/comment_threads.py (used as endpoint/schema reference, not copied implementation).


## Photon 0.5 playback additions

NewPipe subtitle metadata (TTML/WebVTT/SubRip) is attached through Media3 external subtitle configurations and exposed through the native CC selector. Paired-original-audio filtering now retains video **and text** tracks. A PlayerView subclass observes double taps without a Compose touch overlay, excludes native clickable controls, and seeks ±10s only for seekable media. Startup buffering is 750ms / 1.5s rebuffer with a 15–50s forward buffer. A process-only, eight-entry, two-minute LRU cache and striped same-ID locks avoid repeated stream extraction; explicit error retry invalidates cached URLs. URL-free diagnostics record extraction/cache events and first-frame elapsed time. Source `3ee28ea` passed APK assembly and 56 focused tests in run 40864401451; physical subtitle/gesture behavior and latency improvement are not yet measured. No additional speculative media prefetch, automatic quality reduction or audio-language substitution was introduced.


## Photon 0.6 optional YouTube account Home

Adds a separate explicitly opted-in WebView session for website Home, retaining server card order and empty/nudge messages instead of manufacturing a local/search fallback. This uses YouTube website bootstrap data, not YouTube Music's FEmusic_home or the official comment OAuth token. No inherited Prism cookie-paste or embedded-browser user-agent-disguise fallback is reused. WebView manages app-private cookies, backup is disabled, fixed-host HTTP reads disable redirects, and clear-session removes this app's web cookies/site data independently of native OAuth. Only the YouTube page's non-secret web account index is read by evaluated JavaScript; there are no JavaScript interfaces or password-field readers.

Adds explicit local history/resume pause, conservatively enabled on connecting Home. Google history-pause flags are not read/synced and native playback is not submitted to Google watch history. One Home page is supported, not continuation pagination/all server card types. Live sign-in/feed and public-production compliance remain unverified; details in docs/YOUTUBE_HOME.md.


## Photon 0.7 account continuation and watch-history integration

Adapts Prism's continuation and player-history-registration approach for ordinary YouTube web sessions. Adds authenticated browse continuations, scoped cookies/SAPISID headers, a per-call replay guard, epoch-bound Home loads, and structured Google watch-history control parsing. Pause/resume takes only the freshly offered watch-history feedback action and verifies state afterward; it does not copy account history or replay delete-history actions. Public structural references: https://github.com/TeamPiped/rustypipe/blob/main/testfiles/userdata/history.json and https://github.com/yewtubes/yewtubes/blob/main/src/innertube/templates/history/resume_button.json . No real account identifiers/action tokens from those fixtures were committed; tests construct synthetic controls.

Optional native history reporting is gated on 15 seconds actual playback, fresh enabled Google history, explicit app opt-in, same session revision/item identity, and verified authenticated player/tracking endpoints. Quality changes do not become extra reports; offline/short/unknown/paused plays are not backfilled. HTTP acceptance isn't claimed as verified saved history or immediate recommendation change. Google state is polled/checked, not an instant push-synchronized setting. Details/manual testing in docs/YOUTUBE_HOME.md; live behavior and public Google approval remain unverified.


### 0.7.1 login-to-Home correction

Adopts Prism's user-facing Google-login-and-auto-completion pattern: primary Google account chooser for Home, trusted-page signed-in/index proof, automatic account Home selection and no extra manual session-connect step. Native comment OAuth is separately labelled as optional comment authorization. Google rejection is not bypassed: unlike the upstream login source, Photon retains the real WebView user-agent and provides no cookie-paste flow. A failed account Home fetch is visible rather than leaving local recommendations silently unchanged. Seven synthetic completion-proof tests added; device acceptance remains required.


## Photon 0.8 account library and Prism-style Home

Restores Prism's existing Photon branded heading, spectrum line, theme/typography and coloured shortcut-grid treatment to authenticated Home, while retaining server-only recommendations. Adds read-only same-session account history, playlist/LL and subscribed-channel readers with native account library tabs and explicit On device separation. This does not merge cloud items into local persisted library, edit Google collections, or promise a complete Google activity export/private video playback. Sixteen synthetic parser/API tests added; docs/ACCOUNT_LIBRARY.md records scope and device acceptance. User confirmed 0.7.1 account Home works before this change; new library behavior is not yet device-verified.


## Photon 0.9 Google-authoritative actions

Replaces active local-only save/subscribe/create/edit/delete paths with same-session YouTube account mutations and removes the parallel local library UI. Explicit desired likes/subscriptions, structured preflight + bounded read-only verification, private playlist create/initial video, real edit entry IDs and per-playlist destructive confirmation. No mutation replay or local fallback; deletion only claims request acceptance, not verified deletion. Request permission/revision pinned at confirmation and transmission; account switch clears pending work/caches and Google subscription results.

Protocol shapes were checked against YouTube.js LikeEndpoint/SubscribeEndpoint/UnsubscribeEndpoint/CreatePlaylistServiceEndpoint/PlaylistEditEndpoint/DeletePlaylistEndpoint and PlaylistManager. Newly written code/fixtures, no account secrets copied. Adds explicit one-second short-play consent while retaining existing fifteen-second defaults; downloaded plays can report while actively online/verified, not queued offline. Subscriptions feed reads authenticated Google first page instead of local channel aggregation. Native search-history/offline backfill/comprehensive all-activity transfer remains incomplete and is not claimed. See docs/ACCOUNT_WRITES.md.
