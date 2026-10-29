# Photon

**Your world, refracted.** A YouTube-video Android client built on
[Yigder's Prism](https://github.com/Yigder/Prism), retaining its real Compose theme,
fonts, spectrum accents and hand-drawn icons, adapted to widescreen video.

> **0.5.1 alpha — stable signing for native Google authorization.**
> **[Download 0.5.1 APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40867400991/artifacts/12895050509)**
> — GitHub sign-in required; extract and install `app-debug.apk`.
> APK assembly, all 56 focused Photon tests and final APK certificate matching
> passed at revision `de4e5db` ([verified build](https://github.com/HayMayia/Photon/actions/runs/40867400991)).
> Owner reports Android OAuth client registration complete. Actual device sign-in
> and comment posting are not yet verified. Ordinary users do not create Cloud projects.
> Includes 0.5 captions, double-tap seeking and startup changes; their device checks
> remain pending. See [account setup](docs/GOOGLE_SIGN_IN.md) and [signing/migration](signing/README.md).


## Feature status

| Feature | Current implementation |
|---|---|
| Home design | Prism section headers, typography, shape tokens and spectrum colours; shortcut tiles, Quick picks, Continue watching, Ready offline, Made for you and separate music/video discovery shelves; violet default and accent selection |
| Videos | Standard YouTube search, topic discovery, result pagination, video/channel/public-playlist links and Android Share → Photon |
| Channel pages | Native channel details, Videos and Shorts tabs, paginated uploads |
| Subscriptions | **Device-local** subscribe/unsubscribe and refresh uploads from up to 20 subscribed channels; not Google subscriptions |
| Playlists | Local create/add/remove/delete, play loaded videos as a queue; browse paginated public YouTube playlists by link |
| Comments | Read comments/replies with pagination; native top-level composer with public confirmation and official YouTube API posting, requiring Google setup/authorization; replies remain read-only |
| Shorts | Swipeable vertical video feed using extracted Shorts and channel Shorts tabs; discovery is not YouTube's signed-in Shorts algorithm |
| Recommendations | On-device ranking of related videos from engaged watch-history seeds, saved-video weighting, seen-video exclusion, subscription boosting and channel diversity; cold-start topic discovery remains search-based |
| History/resume | Recorded by the playback service, including background playback; periodic positions, playing-time accumulation (not seek distance), and resume across app restarts; recently finished videos restart from the beginning |
| Casting | LAN discovery/control for Google Cast and DLNA using Prism's protocols; proxies a combined MP4 or serves your downloaded merged MP4; **keep Photon open**; no live/adaptive-stream casting |
| Audio language | Original AAC audio takes priority over bitrate. Explicit dubs/descriptive/secondary tracks are not selected automatically. Missing labels are shown as unverified; source availability still limits playback |
| Downloads | Available MP4/AVC + AAC qualities; foreground progress/cancel; separate audio/video muxed on-device with MediaMuxer |
| Offline | Play/delete downloaded MP4 files including sound; thumbnail images are real app-private files rather than only an evictable image cache; old-download artwork is repaired when an allowed network is available |
| Player | Native CC subtitle language/off selector for extracted caption tracks, ±10-second side double-taps (except buttons/unseekable streams), background session, lock-screen/headset controls, fullscreen, PiP and mini-player |
| Startup | 750 ms initial / 1.5 s rebuffer thresholds, bounded two-minute stream-detail cache and same-video extraction deduplication; retry refreshes signed URLs. Device latency reduction not yet measured |
| Testing tools | Settings → **Share device test report**; bounded local playback events without credentials/stream URLs; [device checklist](docs/TESTING.md) and issue template |
| Google authorization | Official native Google Play services account/consent flow, memory-only session and verified YouTube channel; **requires registered Android OAuth package/certificate and enabled YouTube Data API v3** ([setup](docs/GOOGLE_SIGN_IN.md)); live device flow unverified |
| YouTube likes/account sync | Not implemented; authorization for commenting does not sync local subscriptions/playlists or turn bookmarks into likes |
| Live | Extracted live HLS playback when available. **Live recording is not implemented**; the MP4 downloader is not misrepresented as a live recorder |

Local saves are **bookmarks, not YouTube likes**. Local playlists/subscriptions do
not modify your Google account. No account or Photon server is required for signed-out browsing/playback. Optional native commenting needs Google authorization and an app-owner Google Cloud registration. No passwords, cookies or client secrets should be pasted into issues or chat. Native authorization uses the broad YouTube API scope disclosed before Google's consent screen; it does not implement full account synchronization.

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
