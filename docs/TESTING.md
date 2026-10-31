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


## 0.9 Google-authoritative actions (device pending)

- [ ] Like/unlike from player/search/Shorts/related cards affects the exact same account in official YouTube; pre-existing likes don't accidentally toggle off because of a cache miss.
- [ ] Subscribe/unsubscribe on native channels/player persists on Google; unknown/modern controls fail honestly with no local substitute. Google subscriptions feed reads actual account videos, and account switch clears it.
- [ ] Create a private playlist, create-and-add, add an existing video, remove using setVideoId, delete exactly the confirmed playlist; test editable vs saved/others' playlists and special LL/WL safety.
- [ ] No legacy local save/subscribe/playlist path is invoked. Downloads and cached playback/device preferences remain. Old local records aren't destroyed or silently bulk-uploaded.
- [ ] Current-account revision/consent checked before confirmation and transmission. Switch, disconnect, rotate, background/kill and ambiguous errors never replay a write or populate another account.
- [ ] Explicit short-play consent records genuine >=1-second clips/Shorts; old default consent stays fifteen seconds. Pausing, buffering, seeks, quality changes and repeated callbacks don't fabricate/double-count.
- [ ] Downloaded video played while online can report against its opening revision; offline/unverified/Google-paused cases don't queue/backfill.
- [ ] Verify report appearance on official YouTube; HTTP acceptance isn't persistence proof. Deletion acceptance isn't presented as verified deletion.
- [ ] Validate ordinary public playback, captions, seeks, original audio, downloads/muxing, background/PiP/casting, account readers and stable-key upgrade.

[Run 40889863444](https://github.com/HayMayia/Photon/actions/runs/40889863444), source `0621c01213f3567e2d55a376c45dfb72be6e92ce`, passed APK assembly, **140 focused Photon tests** and permanent signing-certificate verification in 5m31s. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40889863444/artifacts/12904148038): 31,201,269 bytes, version code 11. Six local Python signing tests and 92 main XML/SVG parses passed. These are not live Google mutation/device tests. New synthetic fixtures test account mutations/permissions/read-back/no replay, short-play threshold consent and authenticated subscriptions source; no live Google changes occur in tests. See docs/ACCOUNT_WRITES.md for what remains incomplete before claiming all-activity synchronization or publication readiness.


## 0.9.1 thumbnail/comment-composer touch-ups

Source `8e9325df6a7f4d3a8b5b60a1112f5860d9fa62b8`, version 0.9.1-alpha/code 12: [build 40892791049](https://github.com/HayMayia/Photon/actions/runs/40892791049) **PASS**, 4m55s, APK assembly, 147 focused test methods and permanent signing-certificate check. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40892791049/artifacts/12904963365): 31,212,171 bytes. Six Python signing tests and 92 XML/SVG parses also pass. Install over the existing app; do not uninstall. The delivered 0.9 artifact remains unchanged and available above.

- Thumbnail cards/rows follow explicit Shorts metadata: portrait 9:16 for Shorts, landscape 16:9 for regular videos, never duration guesses. Prefer actual returned portrait image sources when their dimensions are known. Recognize legacy reel/Shorts-overlay and modern Shorts-lockup metadata in authenticated feeds. Preserve known Shorts artwork/classification when opening and in new downloads. Old downloads with no Shorts classification are not automatically reclassified or deleted. Player viewport geometry is unchanged.
- A valid draft enables authorization/review even without native comment permission. Missing permission opens a consent explanation and Google's existing official authorization flow, not a fake public post or a Home-session conversion. Authorization never posts automatically; return to review and confirm. Real publication still requires the authorized channel and API success. Final public confirmation is a filled primary button, guarded by authorization, draft validity and disabled/busy states.
- Explanations identify empty/over-limit text, pending authorization/posting and comments disabled by YouTube. Count/validation use the same trimmed text as the posting API. Failed authorization/posting retains the draft.
- Seven new synthetic tests cover shapes, real portrait preference, boundary/whitespace validation, busy/disabled reasons and explicit Shorts metadata. No live account or device tests were run; the original hidden missing-permission gate is a code-level finding, not a confirmed diagnosis of the user's phone.

Device acceptance pending:
- [ ] Check portrait Shorts vs landscape regular videos across search, Home, channels, account library and new offline downloads; short ordinary videos must remain landscape.
- [ ] From Home-only sign-in, type a valid comment: authorization/review is enabled. Cancel Google permission and keep the draft. Approve permission, review the displayed channel and confirm exactly once; verify the actual comment on YouTube.
- [ ] Test empty, whitespace-only, exactly 10,000 and over-limit drafts; disabled comments, authorization failure, rotation and expired credentials show truthful state without automatic publication.
- [ ] Recheck 0.9 Google account writes/history and existing playback/download features. This patch does not complete missing search-history/offline transfer or establish public readiness.


## 0.10 Back/fullscreen and optional native YouTube-style presentation

Source `bec48bcc0080b204b3d06c67d9da5a25223cac6a`, version 0.10.0-alpha/code 13: [build 40896915371](https://github.com/HayMayia/Photon/actions/runs/40896915371) **PASS**, 4m47s. APK assembly, 153 focused test methods, permanent signing-certificate check and artifact upload passed. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40896915371/artifacts/12908630044): 31,291,479 bytes; contains `app-debug.apk`. Install over the existing app without uninstalling. Six local signing tests, 92 main XML/SVG parses and eight new icon-path syntax checks passed.

- Back navigation now retains a bounded tab history, ignores duplicate tab reselections, returns restored non-Home tabs to Home, handles the account playlist subpage and a channel opened from the player, and asks before leaving the root. This is not a complete browser-like stack of every search/filter/nested collection.
- Fullscreen uses explicit Media3 FIT, immersive bars with restoration and safe drawing insets. This is a code-level fix for the likely viewport/inset cause; no claim of reproducing or resolving the phone's clipping without device testing.
- A persisted `youtubeLayout=false` default keeps existing installations on Prism until they explicitly switch. Separate native Home/search/Shorts shelves/navigation, monochrome theme/standard typography and neutral app-owned text avoid using a website player or a second backend. Native You/profile exposes account library and Settings; center Create uses the real private-playlist workflow. Header/native feed controls call existing functionality, not dummy upload/inbox buttons.
- The root/media-controller/theme content remains at stable call sites. The switch updates only the device presentation flag, not downloads, accounts, history consent or playback requests. Busy completion no longer automatically re-expands a minimized player.
- Four new navigation policy tests and two serialization/default/round-trip tests pass. These do not instrument Android Back, rendering, orientation, captions or continuous playback across mode switches.
- **The user's strict EXACT-YouTube visual requirement remains unmet.** This is a native YouTube-style presentation, not Google's actual UI; some controls remain shared. System app identity, Google permissions, notifications and external legal/source links are not rebranded. Obtain screenshots of the desired YouTube version before claiming visual parity.

See [INTERFACE_MODES.md](INTERFACE_MODES.md) for the pending Back, fullscreen, mode-switch, visual-comparison and live-account acceptance cases. All previous 0.9 account/history/public-readiness limitations still apply; a new interface does not resolve them.


## 0.11 playlist Shorts, hold boost, screen awake, hearts and channel artwork

Source `8bc8b9d8c55ea03236d93407b9d4e13ae3d49be6`, version 0.11.0-alpha/code 14: [run 40904639198](https://github.com/HayMayia/Photon/actions/runs/40904639198) **PASS**, 4m16s; APK assembly, **163 focused test methods**, permanent signing-certificate check and upload. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40904639198/artifacts/12911391018): 31,320,756 bytes. Six local Python signing tests and 92 main XML/SVG parses pass.

Added metadata/read-only/state-isolation fixtures are synthetic. They cover ten new cases, not live gesture/speed/haptic/screen-timeout or Google account/device acceptance. See [PLAYBACK_TOUCHUPS.md](PLAYBACK_TOUCHUPS.md) for the implementation scope, metadata-dependent unknown cases, like-index limits, private/account-switch checks and required phone test matrix. No unknown Shorts classification is guessed from duration, failed like reads do not invent false state, no Google mutation happens without explicit confirmation, and no fake local fallback or history backfill is added. Exact YouTube visual parity remains unverified; native refinements do not imply every official screen/feature is replicated.


## 0.12 auto-rotate and simplified interface follow-up

Source `6bed3dfdf7bb8e005b9d599d16c3a24f03830a6c`, version 0.12.0-alpha/code 15: [run 40908141737](https://github.com/HayMayia/Photon/actions/runs/40908141737) **PASS**, 5m4s. APK assembly, focused Photon tests (170 test methods in source), permanent certificate verification and upload succeeded. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40908141737/artifacts/12913061133): 31,328,143 bytes. Local Python signing tests: six pass; main XML/SVG parses: 92 pass.

The user's 0.11 failures supersede earlier implementation expectations. Seven new synthetic tests cover rotation/layout policy and metadata/avatar normalization, not live YouTube or phone rendering. See [0.12 scope](PLAYBACK_TOUCHUPS.md#012-follow-up-auto-rotate-metadata-pictures-and-simpler-ui) for actual changes and outstanding physical checks. No additional device-information request is a prerequisite for this code work. No exact YouTube parity, complete activity sync or public-readiness certification is made.


## 0.13 Android TV and shared player settings

Version 0.13.0-alpha/code 16, source `887f36827ba0a49d3875c2e936310a4c47e2e0d0`: [run 41216748013](https://github.com/HayMayia/Photon/actions/runs/41216748013) **PASS in 4m56s**. APK assembly, focused Photon unit tests (174 methods in source), permanent signing-certificate match and upload passed. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/41216748013/artifacts/13048367111): **31,417,451 bytes**. Same package/key; install over the existing app, without uninstalling, on TV and phone. Six local signing tests and 92 main XML/SVG parses pass; the TV banner is a 320×180 PNG.

Four new synthetic tests check speed presets/clamping, caption-size presets/fine values, the TV landscape/fullscreen policy and defaults/preference serialization. They do not test DPAD focus/key dispatch, slider keyboard events, physical Back behavior, subtitle rendering, casting/authentication on TVs or 10× hardware decoding. [Detailed scope and pending phone/TV checks](TV_AND_PLAYER_SETTINGS.md). No universal-TV, official YouTube TV parity or live Google approval claim is made.


## v1.0.0 Beta — Home updater and foreground-only TV playback

Version name `1.0.0 Beta`, code 17; source `5b03bc63f52717793b3beefb87b99a5a03e28fc8`. [Run 41217937477](https://github.com/HayMayia/Photon/actions/runs/41217937477) **PASS in 6m59s**: debug assembly, focused Photon tests (176 methods in source), permanent debug certificate check, release assembly, permanent release certificate check and GitHub Beta publication. [Public release](https://github.com/HayMayia/Photon/releases/tag/v1.0.0-beta); `Photon.apk` is **25,303,364 bytes**, signed with the existing permanent key. No uninstall required. Local signing tests: six pass; main XML parses: 92 pass (93 XML/SVG combined).

Two new synthetic tests cover Beta/stable/version comparisons and allowed Photon release APK URL origins, including rejection of Prism, HTTP, lookalike hosts and user-info URLs. They do not exercise native package archive/certificate verification, actual download/install permission flows or TV lifecycle behavior. The updater additionally checks installed package ID, strictly increasing version code and exact signing-certificate set before Android independently validates/asks to install. Downloads are bounded to 160 MiB and metadata to 2 MB, with private cache-only URI sharing. No wrong-signature uninstall fallback is provided.

TV playback is paused both when returning to browsing and on loss of the Photon activity's resumed state; service listeners reject background resume, task removal pauses and TV PiP entry is disabled. Mobile background/PiP behavior is unchanged. Validate Home/Recents/power/background media buttons on a real TV, Back to browsing/details/fullscreen, installer foreground transitions and mobile background playback. The official Prism entry is an external direct APK link, not a bundled APK or guaranteed browser/installer on every TV.

First migration from 0.13 necessarily uses one manual install: 0.13 has no updater code to discover this release. Subsequent higher-version signed releases can use Home updates. Test fresh/upgrade defaults, offline/rate-limit/errors, rejected corrupt/wrong-package/wrong-signature/non-increasing APKs, permission denial/grant/retry, installer cancellation, completed upgrade and preservation of downloaded content. Direct distribution is not a Google Play update path or policy approval.


## v1.0.1 Beta — two-column account Shorts and update-only banners

Version `1.0.1 Beta`, code 18, source `ad24a63dc8b54902c6a5e7e71c4772df3496edb1`. [Run 41219254742](https://github.com/HayMayia/Photon/actions/runs/41219254742) **PASS in 9m23s**: debug assembly, focused Photon tests (179 methods in source), permanent debug certificate, release assembly, permanent release certificate and publication. Public Beta tag `v1.0.1-beta` targets that exact source; release asset is 25,303,368 bytes. Existing v1.0.0 Beta can discover/install it in-app; no manual APK/ZIP delivery is required. Six local signing tests and 93 XML/SVG parses passed.

Three new synthetic tests cover two-column/full-span classification, late Shorts metadata preserving video identity and readable Beta banner version/equal-version behavior. They do not render the grid or drive a real device/installer. Physical checks remain: mixed videos/Shorts and odd Short counts, two Shorts aligned per row, narrow screens and large fonts, wrapping/ellipsis and correct video/like/remove targets, late metadata reflow, scroll restoration/TV focus, hidden banner when checking/current/offline with no offer, visible Prism styling only on a real newer release, dismiss/new-version behavior, Android permission denial/grant return and install-over-existing data preservation. The installed v1.0.0 Beta still has its old Check button until upgraded; the update cannot retroactively change an already installed binary.


## v1.0.2 Beta — library header overlap regression

The user supplied a screenshot showing the v1.0.1 account-library tabs, title, Refresh and returned empty-list message stacked at one origin. This was an actual UI regression: unlike a lazy list, a grid item's separately emitted layout roots are placed at the same item origin. The previous pure span tests could not detect it.

Source `7e98bc6d7faef5332e3e706630b425c68a98669c`, version `1.0.2 Beta`/code 19. [Run 41220324972](https://github.com/HayMayia/Photon/actions/runs/41220324972) **PASS in 9m17s**: debug/release assembly, focused Photon tests (181 methods), permanent certificate checks and Beta publication. Public tag `v1.0.2-beta` targets that source; release asset 25,303,368 bytes. Six local signing tests also pass.

Header now emits one full-width Column, with shelf controls/title/Refresh/statuses measured sequentially; footer also has one layout root. Two Shorts per row and actual account data remain unchanged. Two new Robolectric Compose tests instantiate the real header in a grid and assert vertical non-overlap, content below the header and two adjacent equal-top cells, at 100% and 180% font scale. These are actual measured layout tests, not just metadata/span fixtures, but are not physical-device or full-screen live-account certification.

Deliver through the in-app update banner, without a manual APK/ZIP step. The installed updater throttles checks for six hours in memory; force-stop/reopen then Home permits a fresh check without clearing data. Confirm in-place installation, actual device fonts, shelf switching, messages, portrait pairs, scrolling and TV remote focus on hardware. An actual Google empty-list response remains an empty-list response, not locally invented videos.


## v1.0.3 Beta — library scroll stability

User reported Shorts shifting and repeating as they scrolled. Previous grid spans/thumbnail heights depended on mutable resolved metadata, including a bounded global LRU. New data snapshots commit geometry at admission; per-dataset positive presentation survives LRU eviction. Canonical video identity deduplicates overlapping pages, continuation merge uses current enriched rows rather than an old captured copy, and dataset generations prevent stale loads overwriting new requests.

Source `d0a5021cde895095b0475dbc960f19c42dbcfab3`, version `1.0.3 Beta`/code 20. [Run 41221594855](https://github.com/HayMayia/Photon/actions/runs/41221594855) **PASS in 9m36s**: debug/release assembly, focused tests (185 methods), permanent signing checks and Beta publication. Tag `v1.0.3-beta` targets that source; release asset 25,303,368 bytes. Six local signing tests also passed.

Three pure tests verify immutable spans/frame ratios after metadata, page overlap/renderer aliases with retained order, retained positive proof after global cache eviction and deliberate refresh repacking. One actual Robolectric Compose test scrolls a mixed 60-entry grid away/back, changes all format evidence, appends an overlapping continuation, and asserts unchanged visible keys, offsets, scroll anchor and one rendered card per visible identity. It uses production row/span/frame helpers; it does not run a real Google account or full image/network stack. Existing rendered header/font tests also pass.

Unknown entries deliberately retain their admitted footprint after late proof; portrait artwork fits that space without shifting later rows, and playback receives actual resolved metadata. Explicit Refresh may adopt newly known classifications and repack. Do not restore live metadata lookups in the span/frame callbacks. Physical checks remain: fast fling/backtrack, mixed/odd Shorts, long datasets crossing the global LRU capacity, profile/player return, repeated/error continuations, account/shelf switches, known Shorts pairs, newly identified unknowns, click/like/remove targets, TV focus and native in-app installation. No physical-phone fix verification or live duplicate-page reproduction is claimed.


## v1.0.4 Beta — direct actions and Shorts overlay

Source `77a0a31232447dd2b28841d54f8b342b2306dc5c`, version `1.0.4 Beta`/code 21. [Build 41222693382](https://github.com/HayMayia/Photon/actions/runs/41222693382) **PASS in 9m3s**: debug/release assembly, focused suite (186 methods), permanent certificate checks and publication. Release tag targets that source, asset 25,286,984 bytes. Six local signing tests passed.

Google mutation request immediately enters the existing revision-bound/in-flight-guarded execution path. Successful writes clear pending state without modal results; failure messages are nonblocking notices. Removed confirmation dialogs for unlike/unsubscribe/playlist changes, public Post, app login/authorization pre-disclosures, history switches/pause, root exit, offline deletion and device cache clearing. Naming forms/troubleshooting and real Google/Android permissions remain. No history default, backfill, pause override, account fallback or automatic draft posting was introduced.

Shorts uses a full-page video and an overlay driven by Media3 controller visibility: initially hidden, shown by native single tap, hidden by another tap/timeout. Existing native seek/hold input routing remains untouched. Comments use the existing real comments/replies/pagination and composer; details use returned metadata, save uses the existing real playlist picker, and available channel subscription/share actions are exposed. Rendered Compose regression checks hidden/shown/hidden semantics and one direct callback each for unlike, comments, details, save and unsubscribe. This tests the production overlay, not native player touch dispatch or live Google writes. Existing scrolling/header regressions pass.

Physical/live-account follow-up remains: tap overlay reveal/hide and timeout, repeated page swipes, metadata/description loading, comments/replies/continuations, authorized Post and OAuth return without auto-post, failed/ambiguous mutations, same-account results in official YouTube, account switches mid-request, duplicate taps while busy, native double-tap/hold controls, TV/back navigation, in-place update and data preservation. Source/build/render tests are not device certification.


## v1.0.5 Beta — classify before library admission

The v1.0.3 freeze prevented reflow but left late-confirmed Shorts full-width; the user supplied another screenshot showing consecutive portrait Shorts stacked. v1.0.5 resolves presentation before publishing ordered six-entry batches. It reuses valid revision-bound metadata or positive Shorts proof, otherwise public/authenticated presentation reads under the existing two-request semaphore. Only classified rows receive immutable spans. Existing canonical identity/order/metadata retention, generation checks and page deduplication remain. Partial successful batches remain visible on errors; unknown batches are not guessed as landscape. Continuation tokens are consumed only after a page completes, allowing duplicate-safe explicit retry. Loading can take longer; Shorts separated by a normal full-width item remain separate to preserve Google order.

Source `bd7867fec313d57a2cbd59d58811078121cdcad3`, version `1.0.5 Beta`/code 22. [Run 41223617263](https://github.com/HayMayia/Photon/actions/runs/41223617263) **PASS in 7m37s**: debug/release assembly, 188-method focused suite, permanent certificates and publication. Public Beta tag targets that source; asset 25,303,368 bytes. Six local signing tests passed.

Two new coroutine tests exercise the production admission helper: initially unknown videos resolve out of order, stay unadmitted until the batch is complete, preserve order, and commit spans [1,1,2] with portrait/landscape ratios; unavailable formats throw rather than produce fallback cards. Prior actual Compose scroll/remount/append/header/overlay regressions also pass. The new helper tests do not run live Google classification or a physical device. Follow-up: cold-cache liked/history/playlists, consecutive/odd/batch-boundary Shorts, mixed normal videos, load errors/explicit retry, slow networks, cache eviction, overlapping continuation pages, account changes during classification, fast backtracking, TV focus and native signed update. No device certification claimed.


## v1.0.7 Beta — actual subscription state and native channel strip

Source `ab98c61a3d6a7c0d4b9c0e1e80d9edd72252031c`, `1.0.7 Beta`/code 24. [Build 41224699573](https://github.com/HayMayia/Photon/actions/runs/41224699573) **PASS in 6m8s**: debug/release assembly, 192-method focused suite, permanent certs and publication. Tag targets that source; asset 25,303,368 bytes. Six local signing tests pass. The earlier v1.0.6 source run was superseded and did not publish successfully; do not rerun its obsolete publisher.

New intercepted-HTTP regressions verify channel-bound modern entity state (not variant booleans), rejection of variant-only guesses, authenticated-directory evidence for an existing subscription without a write, and directory read-back after one unsubscribe write. Existing legacy mutation, account binding, library scroll/header, admission and Shorts overlay tests remain passing. Canonical extractor channel IDs replace handle URLs for channel-page writes. State is seeded from authenticated channel lists, resolved on channel/player details, and guarded against pre-mutation snapshots. Unknown Check subscription performs only a read.

Library's Subscriptions entry opens the existing real account feed; a separate authenticated directory state drives the mobile/TV channel/avatar strip, pagination and channel navigation. It does not replace data with archived local channels. This is not a native screenshot/pixel-parity test; grouped Shorts shelves and video-feed continuation are not implemented here. Device follow-up: canonical/handle channel opening, existing subscriptions, unknown/unsupported controls, direct subscribe/unsubscribe, loaded directory races, account switch mid-read, channel pagination/failure, TV focus/back, library shortcut and actual account results in official YouTube. No live mutation/device verification is claimed.

Prism refresh investigation is documented in PRISM_PLAYLIST_REFRESH.md, with inspected upstream revision/source links. Photon cannot patch the separate installed app's mirror/cache; no Prism fix is claimed.


## v1.0.8 Beta — optional metadata cannot hide account data

User supplied an empty Liked videos screen with the format-classification error. The v1.0.5 admission policy had mistakenly treated every optional format lookup as a prerequisite: one failed row canceled the batch, and a first-batch failure left no rows. Previous tests explicitly required that blocking behavior, so their passing did not establish correct product behavior.

Changed production admission to isolate per-row failures, retain original Google models/IDs/order, and propagate cancellation. The ViewModel admission classifier now reads **only cached presentation**: it makes no format HTTP requests before showing account rows. Existing visible-card metadata work runs independently. Fallback allocation is a stable half-width portrait preview frame, not a changed Short/normal flag. Append respects that committed allocation. Mismatched landscape artwork fits rather than crops. Unknown normal videos can temporarily have narrow previews; explicit Refresh may repack from subsequently known format evidence. This is an intentional availability/stability tradeoff, not a claim that every format can be known without YouTube.

Source `3ce8d57a4f307599519d41d6072449a21fcb0c39`, `1.0.8 Beta`/code 25. [Build 41225445602](https://github.com/HayMayia/Photon/actions/runs/41225445602) **PASS in 8m55s**: debug/release assembly, 194-method focused suite, permanent certificate checks and publication. Public Beta tag targets that source; asset 25,303,368 bytes. Six local signing tests pass.

Regressions now assert that all metadata unavailable still returns every row with unchanged actual format/model; mixed successful/failed metadata preserves rows, spans and overlapping continuation deduplication; late normal/Shorts proof cannot change admitted geometry; account cancellation still propagates. Previous actual Compose header/scroll/overlay and protocol tests pass. These are production-helper/model-render checks, not a physical/live-account repro. Follow-up: cold account cache, first/middle/private/unavailable entries, denied/slow format endpoints, all metadata unavailable, scroll/backtrack and append, Refresh with newly known normal metadata, account switching and in-place update. Do not restore format-request gating or silently mark unknown videos as Shorts.


## v1.0.9 Beta — correct format, not forced fallback

User screenshot showed a normal trailer in a narrow portrait slot beside a Short. v1.0.8 preserved source flags but forced unknown geometry to portrait; that availability workaround was visually wrong. Removed forced portrait allocation: failed/missing format metadata retains the ordinary full-width frame. Account data still loads without format HTTP. Positive Shorts evidence enriches dataset rows without immediate geometry change; production AccountLibraryFormatEffect observes scrolling/rows and explicitly commits actual-format spans only when idle. Positive source proof is retained across LRU eviction, stale negative metadata, duplicates and remounts. Newly identified Shorts can correct layout once at rest; unknown Shorts may initially appear normal. This is not a claim of instant format knowledge or zero initial correction.

Released app source `a1d0d8389a5ba962f330ae7d5f438e3169b96680`, `1.0.9 Beta`/code 26. [Run 41226639238](https://github.com/HayMayia/Photon/actions/runs/41226639238) **PASS in 8m14s**: debug/release, 195-method focused suite, permanent certs and publication. Public Beta targets that source; asset 25,303,368 bytes. Six local signing tests passed.

Test-only commit `42d1d3fa7d281243229999f5073eb9d0fd21c583` adds an actual mixed-format Compose regression using the production idle-format effect. [Run 41226678098](https://github.com/HayMayia/Photon/actions/runs/41226678098) **PASS in 5m15s**, debug assembly/196-method suite/debug signing. No app source changed and publication was skipped, so the release/tag was not duplicated. Rendered checks assert the trailer retains full width, only the two positively identified Shorts halve their widths, Shorts share one row, and duplicate/remount admission keeps the result. Pure checks cover [2,2,2] when unknown and [2,1,1] only after actual positive proof is committed. Existing no-blocking, cancellation, scroll, header and protocol regressions remain.

Physical/live-account follow-up: cold metadata cache, normal trailer mixed with Shorts, late/failed/denied format reads, active fling then idle correction, anchor behavior, remount/LRU/backtrack, page overlap, TV focus and update. The native mixed-format test does not simulate a real Google account or certify a physical fling/idle interaction. No physical-device fix verification is claimed.
