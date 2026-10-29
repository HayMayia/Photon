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
