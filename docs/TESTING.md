# Photon acceptance checklist

A successful compile is necessary, but not sufficient. Do not mark these as
passing without running them. Baseline: Android 8+; test Android 13+ notifications
and current Android foreground-service restrictions as well.

- [ ] Install APK beside Prism; separate Photon icon, name and app data.
- [ ] Launch; no account or invasive microphone/camera/storage permissions.
- [ ] Search a normal phrase; play a non-music video with audible sound.
- [ ] Paste watch, youtu.be and Shorts links; share a YouTube link to Photon.
- [ ] Load another results page; no duplicate key crash.
- [ ] Rotate while browsing/searching/playing; no reset or unintended playback.
- [ ] Switch 360p/720p/1080p where available; position and pause state survive.
- [ ] Seek, pause, background/resume, lock-screen controls, headset unplug.
- [ ] Minimize/expand player without resetting playback.
- [ ] Enter/exit landscape and PiP; correct surface, audio and back behavior.
- [ ] Open another video; previous audio must stop even if resolution fails.
- [ ] Save/unsave; restart app; saved/history/settings persist.
- [ ] Download combined MP4 and adaptive 1080p; progress and cancel work.
- [ ] Offline/airplane mode: play downloaded video WITH sound, seek to end.
- [ ] Unmetered-only download waits on metered data; disabling allows cellular.
- [ ] Cancel mid-download; no visible incomplete files; repeat download.
- [ ] Network error/restricted/unavailable video: useful failure, no crash.
- [ ] Low storage: clear error, no completed-library entry for partial output.
- [ ] Force stop during download, restart; no corrupt completed entry.
- [ ] Delete a download, confirm removed; decline deletion, file stays.
- [ ] Clear history; saved videos/downloads remain.
- [ ] Dark/light screens and controls have legible contrast and TalkBack labels.

## Build verification

- **PASS**: Android debug APK assembly, revision `3a7501f`.
- **PASS**: focused `com.prism.music.photon.*` unit suite (link parsing, library
  persistence/deduplication, history cap, preference persistence, duration labels).
- **PASS**: XML/SVG parse validation (214 files) and `git diff --check`.
- [Verified GitHub Actions run](https://github.com/HayMayia/Photon/actions/runs/40852929196)
- [APK artifact](https://github.com/HayMayia/Photon/actions/runs/40852929196/artifacts/12888497023)

## Still unverified

All device checks above remain unverified. Local Android compilation cannot
run in the coding sandbox (no JDK/SDK; Maven/Google/Gradle hosts restricted).
GitHub Actions supplied the successful external build check above. A successful
build is not a claim that streaming or download muxing passed on a real phone.

## 0.2 expanded-feature device cases (not yet device-verified)

Use **Settings → Share device test report**, fill in the case ID and steps, then
send the report with a screenshot if helpful. Review before sharing. The report
contains app version, device model/API and bounded local playback-state/error-code
events, not cookies, stream URLs, SSID or unique device identifiers.

| ID | Action | Expected result |
|---|---|---|
| CH-01 | Open a video → Channel; switch Videos/Shorts; load more | Real uploads and tab-specific errors, not a crash |
| SUB-01 | Subscribe locally, restart, open Library → Channels | Subscription persists; refresh fetches uploads |
| SUB-02 | Unsubscribe and refresh | Channel removed locally, Google account unchanged |
| PL-01 | Create playlist; add the same video twice | One entry; playlist survives app restart |
| PL-02 | Paste a public YouTube playlist URL in Search; load more | Public list pages, no sign-in-required content bypass |
| PL-03 | Play all, let first video finish | Next loaded video resolves/plays while UI/controller is active |
| CM-01 | Open comments, load more, view replies, back to comments | Paginated real text; disabled/unavailable errors visible |
| SH-01 | Home → Shorts; swipe up/down | Selected Short plays; previous audio stops; no parallel playback |
| WP-01 | Play 30 s, pause, force-stop, relaunch and reopen | Resume near saved position, within ~5 s |
| WP-02 | Seek forward 5 min after watching 10 s | History does not claim 5 min watched |
| WP-03 | Play to end, reopen | Restarts at beginning rather than sticking at the end |
| WP-04 | Lock screen, play 30 s, return | Service records background playing time and position |
| REC-01 | Actually watch several videos, tap Home → For you | Related candidates ranked locally, watched seeds excluded |
| CAST-01 | Same Wi-Fi, tap Cast and select Google Cast/DLNA TV | Combined MP4 or downloaded merged file plays with sound |
| CAST-02 | Cast an adaptive-only video without a combined source | Explains that downloading/merging first is required |
| CAST-03 | Pause/play/disconnect; dismiss picker | Remote controls work; discovery stops on dismissal |
| PRIV-01 | Clear watch history | History and resume positions clear; saves/downloads/playlists remain |

**Do not mark implemented:** native Google account sync/YouTube likes, native
comment posting, signed-in Shorts personalization, live recording or live casting.
The official YouTube browser link is not evidence of native account integration.

## Latest 0.2 build verification

- **PASS:** debug APK assembly and the 27 focused Photon tests at `6fb5e2e`.
- **PASS:** XML/SVG parse validation, 215 files; `git diff --check`.
- [Successful 0.2 build](https://github.com/HayMayia/Photon/actions/runs/40856646519)
- [0.2 APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40856646519/artifacts/12890570366)
- Physical-device checks remain unverified until a tester reports them.

## 0.3 manual acceptance cases

- **UI-03:** Home has Prism-style shortcut tiles and distinct Quick picks,
  Continue watching (if unfinished history exists), Ready offline (if downloads
  exist), Made for you, Music and general-video shelves. Check light/dark and
  Settings accent changes. Categories must not restrict general YouTube searches.
- **AUD-01:** Open a video with both original and higher-bitrate dubbed tracks.
  Compare against YouTube's explicit Original audio option, not its auto-dub default.
  Photon should use original audio and display its selection label. Repeat after
  changing video resolution and after downloading/playing offline.
- **AUD-02:** A source with no soundtrack labels displays unverified language.
  Explicit dub-only sources must not silently change the spoken language.
  Live HLS language selection is a separate, unverified source behavior.
- **ART-01:** Download while online, enable airplane mode, force-stop/reopen:
  downloaded thumbnails must come from the saved files, with audible offline video.
- **ART-02:** On an old download, choose Settings → Save offline thumbnails while
  on an allowed network. Then repeat ART-01. Clearing only Android's app **cache**
  must not remove saved thumbnail files. **Do not clear app storage/data**.
- **ART-03:** Delete a download: its private thumbnail file is deleted too. Failed
  image retrieval must not mark a successfully saved video as failed. Missing
  artwork should be retriable without downloading the entire video again.
- **COMM-03:** Open video → View Channel / Comments; use Home → Shorts. Comments
  must come from YouTube extraction. Posting remains a browser action until native
  authenticated account integration exists; never report a local-only comment as posted.

The 0.1 user report confirms basic playback on one device, not all new 0.3 cases.

### 0.3 build verification

Debug APK assembly and all **37 focused tests passed** at `601c02a`. The new tests
cover original-over-dubbed audio selection, no explicit dub/descriptive fallback,
mixed music/video shelf definitions, offline-artwork data persistence and migration.
They do not prove live stream metadata accuracy or network image retrieval.

- [Successful build](https://github.com/HayMayia/Photon/actions/runs/40858409686)
- [0.3 APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40858409686/artifacts/12890507624)
- XML/SVG parse check: 215 files valid; git diff whitespace check passed.
- New physical-device cases above remain unverified pending tester feedback.


## 0.4 native authorization/comment acceptance (unverified)

See [Google Cloud registration and signing prerequisites](GOOGLE_SIGN_IN.md).

- [ ] Correct registered Android package and current installed signing SHA-1; API enabled and test user allowed.
- [ ] Missing/outdated Google Play services and wrong OAuth registration produce useful errors, not crashes.
- [ ] Google consent cancelled/denied: no connected session or automatic post.
- [ ] Verified channel name shown; reconnect/switch account and check confirmation names the selected channel/video.
- [ ] Rotate/background during initial Task, Google screen and channel lookup; no stuck busy state or stale callback reconnect.
- [ ] Draft survives rotation and sign-in failure; whitespace/overlength input cannot post.
- [ ] Public posting requires its own confirmation, never triggered by sign-in.
- [ ] Successful real post returns a server comment ID; check official YouTube (allow moderation delays).
- [ ] Literal markup/plain text and Unicode are submitted correctly.
- [ ] Disabled comments, permission/quota errors and 401 retain draft; 401 requires reconnect.
- [ ] Ambiguous network outcome warns to check YouTube; no automatic duplicate submission.
- [ ] Disconnect and process death clear Photon session; revoke underlying grant separately in Google account settings.
- [ ] Existing read-only replies, signed-out playback and local library remain working.

### 0.4 build result

- **PASS**: APK assembly and all 48 focused Photon test methods at `07affd0` ([run 40861060192](https://github.com/HayMayia/Photon/actions/runs/40861060192), 8m54s).
- [0.4 APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40861060192/artifacts/12891223551), 31,048,708 bytes; contains `app-debug.apk`.
- Eleven new account/API tests use synthetic interceptor responses, not a registered Cloud app or live Google comments.
- **NOT VERIFIED**: real-device Google authorization, actual public posting, OAuth registration and signing stability. All manual checkboxes remain intentionally unchecked.


## 0.5 subtitles, gestures and startup (build passed; device verification pending)

- [ ] Captions available: CC menu lists languages/auto-generated labels; select and turn off. Confirm visible captions and synchronization in normal/fullscreen/Shorts player.
- [ ] Separate-original-audio playback retains subtitle text tracks; audio language unchanged.
- [ ] No-caption video: clear availability text, no invented captions. No automatic translation, subtitle downloads or offline caption persistence are claimed.
- [ ] Switch quality, minimize/expand, rotate; CC remains usable; player is not restarted by a simple control interaction.
- [ ] Double-tap left/right video area seeks -/+10s, clamps at start/end; paused playback stays paused.
- [ ] Single taps, play/pause, scrubber, CC menu and other native buttons still work; PiP has no seeking gesture; unseekable live streams do not seek.
- [ ] Cold video: measure click to first frame over Wi-Fi/mobile, then repeat within two minutes; compare same URLs/network/quality with 0.3. No measured latency improvement is claimed before this test.
- [ ] Retry after playback error invalidates the cached signed URLs. Cache expires after two minutes and holds at most eight videos, only in memory.
- [ ] Slower network: lower startup threshold does not produce unacceptable rebuffering; HD preference/original audio remain intact.
- [ ] Settings → Share device test report contains extraction/cache/first-frame timing, not URLs or credentials. First-frame timing after quality switches includes time since original open, so use the initial first-frame event for startup comparisons.

Implementation reduces startup buffer from Media3's default to 750 ms (rebuffer 1.5 s), retains a 15–50 s forward buffer, and caches/deduplicates stream-detail extraction. It does not bypass YouTube's required extraction, CDN connection setup or network throughput. Native gestures and real subtitle rendering remain device checks; policy unit tests do not establish these outcomes.

### 0.5 build result

- **PASS:** Android APK assembly and all 56 focused Photon test methods, revision `3ee28ea`, [Actions run 40864401451](https://github.com/HayMayia/Photon/actions/runs/40864401451).
- [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40864401451/artifacts/12893367353): 31,061,411 bytes; extract `app-debug.apk`.
- Eight new pure policy tests cover ±10-second math, boundary/unknown-duration handling, cache TTL, LRU eviction, invalidation and video separation. They do not simulate native touch routing, caption rendering or real network startup.
- `git diff --check` and parse validation of 92 main-source XML/SVG files passed.
- **NOT VERIFIED:** actual speedup from the reported ~10-second startup, subtitle availability/rendering, gestures on a phone, live Google authorization or posting.


## Stable signing setup (0.5.1 source; provisioning/build pending)

Owner must save PHOTON_SIGNING_PASSWORD directly as a GitHub Actions repository secret; this session cannot manage Actions secrets (403). See signing/README.md. Bootstrap must refuse an existing identity, publish only encrypted backup/public certificate, dispatch a trusted APK build, and restore the same identity in later trusted builds. Final APK certificate must match ANDROID_OAUTH.txt. PR jobs must never decrypt the private key. Local Python safety-gate and workflow syntax checks are not proof of a completed key generation, Android signing build or successful Google OAuth registration.

### Permanent identity provisioned

- **PASS:** setup run 40867368566, six signing safety tests, key generation/encrypted backup, public certificate publication and explicit APK build dispatch. Provisioning commit `de4e5db`.
- **PENDING:** stable-signed APK build 40867400991 and final APK-certificate match; Google Android OAuth client registration and live-device authorization/comment posting.
- Public registration identity is `signing/ANDROID_OAUTH.txt`; do not register an older temporary-debug APK's SHA-1 for this key.

### 0.5.1 stable-signed APK verification

- **PASS:** revision `de4e5db`, [run 40867400991](https://github.com/HayMayia/Photon/actions/runs/40867400991), 5m18s: permanent-key restore, APK assembly, all 56 Photon Android test methods, final APK certificate match, artifact upload and plaintext signing-file cleanup.
- [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40867400991/artifacts/12895050509): 31,062,109 bytes; includes `app-debug.apk` and public `ANDROID_OAUTH.txt`.
- Owner reports Google Android OAuth client creation complete with the permanent package/SHA-1. This is not an independently verified Console result or a device sign-in test.
- **PENDING:** actual Google authorization, real comment posting, subtitle/gesture device checks and measured startup improvement. Public distribution verification/policy review remains separate from testing-mode client creation.


## 0.6 account Home and local pause (build passed; device checks pending)

See docs/YOUTUBE_HOME.md for the complete session/account/privacy limits and device checklist.

- [ ] Connect website session after disclosure; verify account selection and supported Home cards against YouTube. Native comment OAuth must remain separate.
- [ ] Google embedded-browser rejection is not bypassed or hidden; no cookie-pasting option. Clear web session must work after canceled/failed setup too.
- [ ] YouTube history-off nudge/empty page is preserved without generated recommendations or discovery shelves. Unknown empty feed must not be labelled paused.
- [ ] Explicit local pause prevents history/watch-time/resume writes, preserves old data, survives restart, and does not count paused elapsed time on unpause.
- [ ] Cancel/clear/switch/rotation: no stale or cross-account feed; match web account index; test brand channels separately (not assumed to match native OAuth channel).
- [ ] Refresh and one-page limitation are clear; unsupported layouts fail without a fake feed.
- [ ] Search, manual Shorts discovery, local subscriptions, downloads, saved items, native playback/comments/subtitles/gestures remain accessible.
- [ ] Upgrade from stable-signed 0.5.1 without uninstalling; certificate matches permanent registered SHA-1.

New automated fixtures/protocol tests cover server order/deduplication, empty/history-off messages, signed-out/unknown-state rejection, bootstrap JSON scanning, modern card IDs, untrusted thumbnails, strict login hosts, selected web account and redirect rejection. Store tests cover paused tracking and persistence. No fixture establishes live web-session authentication, current website layout support, Google setting synchronization, native history upload or public-release policy approval.

### 0.6 verified build

- **PASS:** revision `2852d9b`, [run 40871610491](https://github.com/HayMayia/Photon/actions/runs/40871610491), 8m22s: stable-key restore, APK assembly, all 71 Photon Android test methods, final permanent-certificate match, artifact upload and signing-file cleanup.
- [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40871610491/artifacts/12896153246): 31,105,113 bytes, includes `app-debug.apk` and public `ANDROID_OAUTH.txt`.
- Fifteen new focused tests: twelve Home/parser/protocol/navigation checks and three history-pause/persistence checks. XML/SVG main-source parse (92 files) and `git diff --check` passed.
- Earlier core implementation `42e40b8` also passed run 40870748774 (7m51s). Final build adds explicit Shorts/library navigation to account Home.
- **NOT VERIFIED:** live embedded Google/YouTube sign-in, actual account Home response, same-account/brand-channel matching, physical history-pause behavior, comments, latency or public-release approval. Google history flags/sync, history uploads and feed continuations are not implemented, not merely untested.


## 0.7 account Home pagination and history reporting (automated PASS; device pending)

See docs/YOUTUBE_HOME.md for implementation limits, privacy and device acceptance.

- [ ] Scroll authenticated Home over several pages; server order, deduplication, near-end trigger, retry-on-error and repeated-token/empty-page guards work. Refresh and account changes cannot append an old response.
- [ ] Compare detected Google history enabled/paused with official YouTube. Unknown/missing/contradictory controls fail closed; stale state (>30s) blocks tracking.
- [ ] Explicitly confirmed native Pause/Resume modifies the correct account and verifies the requested state with a fresh read; failures don't pretend success or retry mutation. No history-deletion endpoint is called.
- [ ] Local tracking mirrors last verified Google pause in connected mode (not instant remote-change notification); additional local pause retains independent control and prior data.
- [ ] Reporting opt-in explains account effects and removes only extra local pause. No uploads before opt-in or when Google state is paused/unknown/stale.
- [ ] A remote video played for >=15 actual isPlaying seconds is reported once; inspect official YouTube history. Pauses, buffering, seeks, short/offline plays, quality changes and duplicate callbacks cannot inflate or duplicate reports.
- [ ] Account review/switch/disconnect revokes pending permission, disables reporting consent and invalidates old view revisions. An old playing video cannot become the new account's report.
- [ ] Redirects, 503/follow-up behavior, failed/ambiguous report, offline/background/process death: no automatic report replay or queued history backfill. Already in-flight requests cannot be recalled.
- [ ] Native reports use host-scoped cookies and verified matching player ID/tracking docid; never arbitrary URLs. No credentials/tokens/URLs in diagnostic output.
- [ ] Native comments, captions, ±10s seek, original audio, cast/downloads and stable-key update remain intact.

New source adds 28 focused test methods: 19 continuation/pause-policy/playback-gate/URL/session checks and nine synthetic account-mutation/read-back/report/scoped-cookie/concurrency protocol checks. All use synthetic data/local interceptors; no live account writes occur in tests. All 99 focused tests, APK assembly and permanent certificate verification passed in run [40877422966](https://github.com/HayMayia/Photon/actions/runs/40877422966), source `a6bb93d`, 7m31s. Locally, all six signing Python tests and parsing of 92 main XML/SVG files passed. These checks do not replace the unticked live-device checks above.


## 0.7.1 login-to-Home correction

- [ ] From Settings, the primary **Sign in with Google** opens Google's account chooser for Home, not native comment OAuth.
- [ ] Returning to an authenticated YouTube page completes automatically, without “Use this YouTube session”. A signed-out/missing/invalid account proof cannot complete.
- [ ] Return to Home: account cards or an explicit account-loading/error state replaces the previous local feed. No silent fallback after a connection attempt.
- [ ] Comment permissions remain optional, explicitly labelled, and do not falsely claim to connect Home. Existing native comment identity/grant remains intact.
- [ ] Cancel, unsupported Google login, failed TLS, navigation away and delayed JavaScript callbacks cannot complete login or switch accounts incorrectly.

Seven new synthetic completion-proof tests cover default/non-default account index, signed-out null, missing configuration, malformed/unsupported values and result type. APK assembly, all **106 focused tests** and permanent signing-certificate verification passed in [run 40881293556](https://github.com/HayMayia/Photon/actions/runs/40881293556), source `09f81c1`, 8m1s. All six local Python signing tests and 92 main XML/SVG parses passed again. Live-device checks above are not claimed as complete.


## 0.8 account library and Prism layout

The user reports 0.7.1 Google sign-in/account Home now works. This is a user device observation, not an assistant device test. The new 0.8 library/layout checks below remain pending:

- [ ] Home restores Prism brand/header, spectrum accents, shortcut tile layout and light/dark/accent styling; authentic recommendation order remains unchanged.
- [ ] History, Playlists, Liked videos and Channels shortcuts open account lists, not device lists. Explore videos/music remain explicit search actions, never fallback account recommendations.
- [ ] Compare each account list against official YouTube for the same Google account; history includes previously watched videos where returned, Liked videos uses LL, Channels lists subscribed channels.
- [ ] Open private/public account playlists and further pages; open a subscribed channel and a public playable video. Unavailable/private playback remains an honest extractor limitation.
- [ ] Library source selector retains On device saved/history/progress/downloads/channels/playlists unchanged; download shortcuts choose On device.
- [ ] Empty, signed-out, unknown-layout and network-error cases do not falsely show local rows as Google data.
- [ ] Account switch/disconnect with a request in flight clears old rows and rejects stale responses. No history backfill or Google library mutations occur.

Sixteen new synthetic parser/intercepted-HTTP tests cover account history/LL/playlist requests, legacy and modern collections, channels, IDs, order, deduplication, primary scope, continuation tokens and auth failure. [Run 40884491780](https://github.com/HayMayia/Photon/actions/runs/40884491780), source `a2d2717c06788a9885626c71eb9a860422607b93`, passed APK assembly, **122 focused Photon tests** and permanent signing-certificate verification in 6m47s. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40884491780/artifacts/12901454220): 31,188,399 bytes, version code 10. Six local Python signing tests and 92 main XML/SVG parses passed. Real account-library/device behavior remains unverified. See [account library behavior/limits](ACCOUNT_LIBRARY.md).
