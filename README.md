# Photon

**Your world, refracted.** A YouTube-video Android client built on
[Yigder's Prism](https://github.com/Yigder/Prism), retaining its real Compose theme,
fonts, spectrum accents and hand-drawn icons, adapted to widescreen video.

> **v1.0.1 Beta — paired Library Shorts and quiet Prism-style update banners.**
> [Release notes](docs/RELEASE_1_0_1_BETA.md). Installed **v1.0.0 Beta** can get this through **Home → Check for updates → Update**, with Android installation approval. No separate manual APK/ZIP step is needed for that migration.
> Account-library Shorts now occupy up to two cells per row, with portrait thumbnails and titles wrapping beneath each image. Ordinary videos, collections and headers remain full-width, with Google ordering preserved. TV Short thumbnails have a height cap.
> In **v1.0.1**, the permanent version/check card is gone: automatic checks stay quiet unless an actual newer release is available. A glass/spectrum Prism-style banner then reads **Update to v[version] Beta**; it supports tap-to-update, dismiss and install-permission return. It disappears when current.
> [Build 41219254742](https://github.com/HayMayia/Photon/actions/runs/41219254742) **PASS in 9m23s**, source `ad24a63`: debug/release assembly, focused Photon tests (179 methods in source), permanent certificate checks and Beta publication. Code 18, same package/signing identity.
> Existing TV foreground-only playback, mobile background playback, Prism download entry and shared player settings remain unchanged. Physical layout/TV/native-install checks remain pending; no universal-TV or Google approval certification is claimed.
> Older 0.13 has no updater and still requires its one-time updater-enabled migration; updater-enabled versions do not require a manual file download for subsequent releases.
> Back retraces tabs/pages/player before asking to leave at the root; fullscreen uses FIT and safe insets.
> **Settings → Switch to YouTube layout** changes native presentation; **You → Settings → Switch to Photon** returns. Same player/accounts/downloads, no website-player replacement.
> This is **not pixel-identical or the official YouTube interface**; some controls remain shared, and Android/Google permission screens keep the real app identity. [Scope and device checks](docs/INTERFACE_MODES.md).
> Shorts cards use portrait 9:16 artwork; regular video cards use landscape 16:9. Valid comment drafts can authorize/review instead of silently requiring a missing comment grant; publishing still needs explicit authorization and confirmation.
> Like/unlike, subscribe/unsubscribe and playlist actions now send confirmed same-account YouTube requests, not local saves.
> Google is the default library; only downloads/device caches stay local. [Account writes and remaining scope](docs/ACCOUNT_WRITES.md).
> Authenticated Home retains the Prism header and spectrum accents with compact shortcut chips.
> **Library → YouTube account** reads History, Playlists, Liked videos and subscribed Channels;
> **Downloads** retains necessary offline files; legacy device lists are no longer the account library. [Account library scope/testing](docs/ACCOUNT_LIBRARY.md).
> The primary **Sign in with Google** now connects account Home automatically after Google's login.
> Optional native comment permission is separately labelled **Authorize comments**; it does not connect Home.
> No extra “Use this YouTube session” step; failed Home loads show errors rather than silently keeping the local feed.
> Home follows authenticated YouTube continuations; native pause/resume requires confirmation and read-back.
> Optional history registration keeps its 15-second default, with separate explicit 1-second short-play consent; fresh enabled Google history and the original account/session are required. Downloaded videos played while online can qualify; no offline backfill.
> **User confirmed 0.7.1 login/account Home works. Account lists/writes, Google pause changes and saved history reports still need device verification.**
> HTTP acceptance is not proof of stored activity. Unknown/stale/paused state blocks tracking in connected mode;
> [behavior, privacy and device checklist](docs/YOUTUBE_HOME.md).
> Native comment authorization is separate; embedded sign-in may be blocked. Keep Cloud testing while validating;
> public Google verification is separate. Stable-signed 0.5.1/0.6 should update without uninstalling.


## Feature status

| Feature | Current implementation |
|---|---|
| Home design | Prism section headers, typography, shape tokens and spectrum colours; shortcut tiles, Quick picks, Continue watching, Ready offline, Made for you and separate music/video discovery shelves; violet default and accent selection |
| Videos | Standard YouTube search, topic discovery, result pagination, video/channel/public-playlist links and Android Share → Photon |
| Channel pages | Native channel details, Videos and Shorts tabs, paginated uploads |
| Subscriptions | Actual Google subscribe/unsubscribe with structured state checks/read-back; account Channels lists subscriptions. Video feed reads Google's authenticated first page, not local channel aggregation |
| Playlists | Account list/rows, private creation/initial video, Google add/remove with entry IDs and read-back; per-playlist confirmed deletion reports request acceptance, not independently verified deletion. No local fallback; multi-page editing/rename/privacy controls remain incomplete |
| Comments | Read comments/replies with pagination; native top-level composer with public confirmation and official YouTube API posting, requiring Google setup/authorization; replies remain read-only |
| Shorts | Swipeable vertical video feed using extracted Shorts and channel Shorts tabs; discovery is not YouTube's signed-in Shorts algorithm |
| Recommendations | Optional native **YouTube account Home** through Prism-style Google-page sign-in, in server order; no local/search fallback in that mode. Auto continuation loading with deduplication/loop guards; unofficial; 0.7.1 login/Home user-confirmed, continuation/device cases remain pending. Original on-device related-video ranking remains a distinct local mode |
| History pause | Reads explicit server watch-history controls; confirmed account pause/resume with read-back; connected-mode local tracking is blocked when Google state is paused/unknown/stale. Additional local pause stays independent. Device verification pending |
| Account watch history | Separately opted-in single real-play report after 15 actual playing seconds (explicit short-play consent: 1 second); fresh Google enabled-state check and account-pinned item required. No offline backfill, fake watchtime stream or automatic report retry. HTTP acceptance is not proof of saved Google activity |
| History/resume | Recorded by the playback service, including background playback; periodic positions, playing-time accumulation (not seek distance), and resume across app restarts; recently finished videos restart from the beginning |
| Casting | LAN discovery/control for Google Cast and DLNA using Prism's protocols; proxies a combined MP4 or serves your downloaded merged MP4; **keep Photon open**; no live/adaptive-stream casting |
| Audio language | Original AAC audio takes priority over bitrate. Explicit dubs/descriptive/secondary tracks are not selected automatically. Missing labels are shown as unverified; source availability still limits playback |
| Downloads | Available MP4/AVC + AAC qualities; foreground progress/cancel; separate audio/video muxed on-device with MediaMuxer |
| Offline | Play/delete downloaded MP4 files including sound; thumbnail images are real app-private files rather than only an evictable image cache; old-download artwork is repaired when an allowed network is available |
| Player | Native CC subtitle language/off selector for extracted caption tracks, ±10-second side double-taps (except buttons/unseekable streams), background session, lock-screen/headset controls, fullscreen, PiP and mini-player |
| Startup | 750 ms initial / 1.5 s rebuffer thresholds, bounded two-minute stream-detail cache and same-video extraction deduplication; retry refreshes signed URLs. Device latency reduction not yet measured |
| Testing tools | Settings → **Share device test report**; bounded local playback events without credentials/stream URLs; [device checklist](docs/TESTING.md) and issue template |
| Google authorization | Official native authorization for comments (memory-only token); separate optional app-private WebView session for account Home. These are not interchangeable. [Comment setup](docs/GOOGLE_SIGN_IN.md) · [Home/session/privacy limits](docs/YOUTUBE_HOME.md) |
| YouTube account library | Actual History, Playlists, Liked videos (LL) and subscribed Channels with load-more. Google-bound likes/subscriptions/playlist actions replace local-only controls. No historical backfill, complete activity export or owned-channel management; not yet all-activity/search-history/offline synchronization |
| Live | Extracted live HLS playback when available. **Live recording is not implemented**; the MP4 downloader is not misrepresented as a live recorder |

Local saves are **bookmarks, not YouTube likes**. Local playlists/subscriptions do
not modify your Google account. No account or Photon server is required for signed-out browsing/playback. Optional native commenting needs Google authorization and an app-owner Google Cloud registration. No passwords, cookies or client secrets should be pasted into issues or chat. Native authorization uses the broad YouTube API scope disclosed before Google's consent screen; it does not implement full account synchronization. Optional account Home separately uses sensitive website-session cookies kept by Android WebView; see the in-app disclosure and [Home privacy/limitations](docs/YOUTUBE_HOME.md).

## Get an APK

1. Open **[Actions → Build Photon APK](https://github.com/HayMayia/Photon/actions)**.
2. Open the latest **successful** run for branch `arena/c217a41b-photon` and check
   that its revision contains the features you want.
3. Under **Artifacts**, download **Photon-Android-APK** (GitHub sign-in required).
4. Extract the ZIP, transfer `app-debug.apk` to your Android 8+ phone, and open it.
5. Allow installation from that browser/file manager if Android asks.

Artifacts expire after 90 days. Keep a local APK/source copy. If a workflow is
red, there is no verified APK for that revision. Earlier successful runs remain
available but do not include later changes. The original 0.1 build passed at
[revision 3a7501f](https://github.com/HayMayia/Photon/actions/runs/40852929196).

**Upgrade warning:** 0.5.1 is the first verified APK using the permanent Photon
certificate. Older CI debug APKs used unrelated temporary keys and may not accept
an in-place update. **Do not uninstall a populated app casually:** uninstalling
deletes its private library/downloads. Future trusted CI builds reuse the permanent
key; PR-test APKs remain temporary-debug-signed. See [signing and recovery](signing/README.md).
Never post the signing password or private key.

## Build and test

Verified toolchain baseline: JDK 21, explicit Android SDK platform **37.2**, and
internet access to Google Maven, Maven Central, Gradle and JitPack. Gradle source
is pinned to Prism's upstream dependency versions. CI resolves the minor SDK
version explicitly rather than requesting the nonexistent `android-37` package.

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest --tests 'com.prism.music.photon.*'
```

The focused suite exercises link validation, library persistence and migration,
subscriptions, playlist mutation/deduplication, progress accumulation/resume,
bounded history retention, recommendation ranking, OAuth/API protocol safeguards,
seek clamping and bounded cache expiry/eviction. It does not call YouTube.
Inherited Prism tests remain, but many exercise live music/lyric services and are
not a Photon acceptance suite. Local compilation is unavailable in the coding
sandbox (no JDK/SDK and restricted Maven/Google/Gradle hosts); GitHub Actions is
the external compilation/test gate.

## Important remaining limitations

- No native Google login, Google library import/sync, YouTube likes, posting
  comments, or editing your account's online playlists. Official YouTube in a
  browser can perform account actions, but those are **not Photon-native features**.
- Live recording is not implemented. Live manifests can have rotating windows,
  separate tracks, discontinuities and encryption; ordinary MP4 downloads cannot
  safely record them.
- Only compatible available progressive MP4/AVC + AAC streams are offered. No
  guaranteed 1080p/4K; device codecs, IP/region restrictions and source availability vary.
- The original-audio policy can pair a separate original track even with a combined
  MP4. For such sources, download/mux first and cast the merged copy; the live
  proxy does not merge tracks on the fly.
- Cast quality uses a combined MP4 source rather than silently sending video-only
  adaptive media. If unavailable, download/mux first and cast the offline copy.
  Keep Photon open while casting; background cast lifecycle and TV compatibility
  remain device-test items. No live casting.
- Downloads restart rather than resume partial files. Android may restrict long
  background work. Downloads and thumbnail files are app-private and **removed on
  uninstall**. Public export is not implemented. A missing/failed thumbnail does
  not fail the video download; Settings → Save offline thumbnails retries older
  or missing images, respecting Wi-Fi-only. Artwork unavailable from YouTube cannot
  be guaranteed offline.
- Original soundtracks are selected when labelled and available in compatible AAC.
  Unlabelled audio remains unverified; if only explicitly dubbed alternatives are
  available, playback fails rather than silently changing language. Existing
  downloads must be downloaded again to replace a previously selected dub.
- Live HLS retains its source-defined audio; the new progressive-audio policy does
  not promise original-language selection for live manifests.
- Discovery shelves use explicit public YouTube queries and are labelled as such;
  they are not signed-in YouTube Music album charts or a clone of every Prism
  feature. All standard YouTube video links/searches remain supported.
- Local history/progress covers 100 recent videos. Progress is periodically saved
  (up to about five seconds may be lost on abrupt process death). Seeking does not
  add the seek distance to watched time. Queue continuation requires the active
  Photon UI/controller; it is not a persisted background playlist service.
- Subscriptions and playlist collections are local; remote playlist playback queues
  only the videos loaded so far. Subscription refresh currently covers 20 channels.
- YouTube may change extraction or refuse a request. Network errors and disabled
  comments/absent channel tabs are reported rather than replaced with fake data.

Download/record only content you own or have permission to save, following
applicable terms and laws. No DRM, paid-access or age/sign-in restriction bypass.

## Code map

Active application: `app/src/main/java/com/prism/music/photon/`.

- `VideoRepository.kt`: public YouTube extraction, channels, playlists, comments,
  safe links and local recommendation ranking.
- `PhotonActivity.kt`, `CommunityScreens.kt`: Prism-themed video/community UI.
- `PhotonPlaybackService.kt`: Media3 session, merged playback and service-owned progress.
- `VideoDownloadWorker.kt`: foreground downloads, cancellation, MP4 muxing.
- `PhotonStore.kt`, `PhotonViewModel.kt`: local data, collections, retained state and queues.
- `PhotonCast.kt`: independently wired Prism LAN discovery/control and media server.
- `PhotonDiagnostics.kt`: bounded, local, URL-free playback test report.

Original Prism sources remain for design reuse, but the manifest starts **Photon**,
not Prism's music container/jobs. Music-only features are not advertised as Photon
features. Future cleanup can remove unused inherited dependencies.

## License and credits

**GPL-3.0-or-later**. Original Prism copyright (C) 2026 Yigder.
See [LICENSE](LICENSE), [UPSTREAM.md](UPSTREAM.md), and the
[original README](docs/upstream/README.md). Independent project, not an official
Prism product and not affiliated with Google/YouTube.
