# Photon architecture audit and repair ledger

## Scope and evidence standard

This is an **incomplete overhaul**, not a declaration that every function is correct.
The eight categories received targeted repository searches, with deeper manual reads
of the files listed below. `docs/audit/source-inventory.json` contains the complete
per-pass search paths and matching lines; a search hit is not a verified bug.
Production under active maintenance is `old-photon/` (`io.github.haymayia.photon`).
The supplied Firebase/WebView app is `app/` (`com.aistudio.photon.qrxmzk`).
Internal `com.prism.music` symbols in the original are real APIs and database/code
names, not evidence that Android installs it as Prism. No blanket namespace rename
or destructive app-ID/data migration was attempted.

## 1. 8-Pass Bug Hunt Log

### Pass 1 — Build, Gradle, KSP, Room and R8

Inspected: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `app/build.gradle.kts`, `old-photon/build.gradle.kts`,
`baselineprofile/build.gradle.kts`, both modules' ProGuard and trial rules,
`baselineprofile/src/main/java/com/prism/music/baselineprofile/{Journeys,BaselineProfileGenerator,JankBenchmark}.kt`.

Confirmed defects:
- Excluded baseline-profile project targeted `:app`, but its `Journeys.PACKAGE`
  was `com.prism.music`, matching neither installed application. Corrected target
  to original Photon and test namespace/imports to Photon. Journeys themselves
  still reference historical music screens; module remains excluded and is **not
  a working Photon benchmark**. Added explicit README instead of enabling it.
- `app/r8-trial.pro` described unrelated Prism settings/reflection/libraries and
  suppressed all warnings. It is not selected by `app/build.gradle.kts`, so it
  was not the cause of an observed release failure. Replaced stale rules with an
  include of the app's rules; added explicit JavaScript-interface member keeps
  to `app/proguard-rules.pro`. Minification remains disabled: no R8 safety claim.

Checks, not defects:
- `app` namespace `com.example` matches its source/generated R/BuildConfig usage;
  Firebase package matches applicationId `com.aistudio.photon.qrxmzk`. A namespace
  need not equal an applicationId. Firebase is actually used.
- Root plugin versions and catalog versions differ; catalog plugin aliases are
  not the active declarations in these modules. Do not downgrade previously
  building tools based only on their numbers. Verify resolution with CI.
- The compile SDK 37 minor API 2 DSL already built on the previous release.
- Original Room uses the Room plugin and schema directory; database is version 8,
  with explicit 1→2→3→4→5→6→7→8 migrations and tracked schemas. This does not prove
  every migration with real user data. Destructive downgrade behavior remains a
  data-safety concern requiring review, not an upgrade migration fix.

### Pass 2 — Manifest, intents, permissions and lifecycle

Inspected: all three main manifests; original `PhotonActivity.kt`,
`PhotonPlaybackService.kt`; replacement `MainActivity.kt`, `PhotonApp.kt`,
`app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`.

Confirmed defects:
- Original manifest VIEW filter omitted `http` and `music.youtube.com` despite
  parser support. Both added. Added `keyboardHidden` to retained activity config.
- Replacement allowed platform backup with unconfigured sample rules, despite
  storing WebView/Firebase/account state. Disabled platform backup and explicitly
  excluded app domains from cloud backup and device transfer. Firebase-owned
  remote data is unaffected; this is not a new local backup feature.

Existing protections verified in source: original has network, wake-lock and
media-foreground-service permissions; `mediaPlayback` service; exported launcher,
Leanback and SEND entry points; optional touchscreen and Leanback features; PiP.
Replacement has a different WebView lifecycle, no MediaSessionService/TV launcher
and no equivalent background-playback contract. Adding unused service permissions
would not implement those features, so they were not fabricated.

### Pass 3 — Extraction, network and links

Inspected: original `VideoRepository.kt` (including `YouTubeLinks`),
`ExtractionRequests.kt`, `PlaybackNetworkPolicy.kt`, `data/stream/StreamResolver.kt`,
`PhotonViewModel.kt`, replacement `data/YouTubeRepository.kt` and `PhotonModels.kt`.

Confirmed defects:
- Shared-video timestamps were ignored by search/open despite valid video IDs.
  Added bounded seconds/h/m/s parsing for `t`, `start`, and `#t=`, carried the
  position through extraction, and clamped it to known media duration. Explicit
  zero overrides resume. Invalid/overflow timestamps fall back to normal resume.
  Playlist precedence remains deliberately unchanged.
- Video parser duplicated the trusted-host parser but skipped its scheme,
  credentials and port validation. Now uses the same trusted boundary.
- Replacement `parseDurationToSeconds` silently dropped malformed colon fields
  (`1:bad:30` became a valid but wrong time) and multiplied Int values unsafely.
  It now rejects malformed/negative/noncanonical minute/second fields and overflow.

Source checks: extraction uses IO; metadata has connect/read/call limits; stream
transport deliberately has no whole-call deadline because a paused media body can
outlive a metadata deadline. It still has connect/read limits. NewPipe transport
sets User-Agent and turns 429 into a challenge error. Coroutine cancellation
tracks and cancels synchronous extraction calls.

Open defect: terminal playback errors only log/display an error; manual retry
invalidates signed-stream details, but there is no tested automatic one-shot
expired-URL refresh preserving intent/position. No claim this was fixed.
Live 403/429/5xx recovery and public YouTube startup latency remain unverified.

### Pass 4 — Media3, audio focus, release and loops

Inspected: original `PhotonPlaybackService.kt`, `QueueSessionPlayer.kt`,
`PhotonActivity.kt`, `PhotonPlayerView.kt`, `PhotonSleepTimer.kt`,
`PlayerOptions.kt`, replacement `player/YouTubePlayerController.kt` and
`ui/PhotonApp.kt`; `playback/sing/Sing.kt` interruption handler.

Confirmed defects:
- Native PlayerView Back callback acted on canceled ACTION_UP. Now ignores canceled
  key-up events, matching Activity dispatcher behavior.
- Replacement WebView cleanup/evaluateJavascript swallowed Throwable, hiding
  lifecycle failures and VM errors. Now catches RuntimeException with sanitized
  diagnostics. Account-sheet cleanup receives the same treatment.
- Sing decoder swallowed InterruptedException without restoring interrupt status.
  Now restores it. This legacy path is not evidence of original Photon playback.

Existing source protections: service owns ExoPlayer, releases session/player and
cancels service scope in onDestroy; audio focus is enabled and unplug handling is
set. Composition polling is under LaunchedEffect, service polling under its scope.
Quality switching preserves position/playWhenReady. Sleep timer uses a monotonic
clock and compare-and-set expiration. These facts are not physical-device tests.

Missing: true user-facing audio-only stream switch, A–B ranges/race handling,
per-channel speed memory, codec preference/fallback policy. Not implemented here.

### Pass 5 — Compose, state and concurrency

Inspected targeted hits across Kotlin UI/state sources (full paths in inventory),
with focused reads of `PhotonActivity.kt`, `PhotonViewModel.kt`, `PhotonHome.kt`,
`PhotonTv.kt`, `SubscriptionsScreen.kt`, `AccountLibraryScreen.kt`,
`PlaylistContents.kt`, `OfflineArtwork.kt`, `PhotonStore.kt` and replacement
`PhotonApp.kt`/account WebView disposal.

Confirmed defects:
- Optional metadata/history/recommendation operations used comment-only catches
  that hid failures. Retained nonfatal fallback behavior but now records bounded,
  sanitized Photon diagnostics (no exception text/credentials/stream URLs).
- Seven syntactically empty exception handlers were found across both main Kotlin
  trees. All now have diagnostics or interrupt restoration. LAN disconnect and
  missing Settings intent fallback remain recoverable, not fatal exceptions.

Checks/open risks:
- Inspected production home/feed/TV lists use stable video/entry keys. This is
  not proof against duplicate upstream IDs in every list.
- No confirmed same-direction infinite-height crash was reproduced. TV/settings
  and feed lazy containers were read, not device-tested.
- PhotonStore JSON decode and full-library encode/preferences access still run
  synchronously on callers, including main-thread playback snapshots. Moving
  this safely needs a serialized persistence design and recovery tests; it is
  not fixed by wrapping one call in an unowned CoroutineScope.
- No claim that every launch path is exception-safe or recomposition-optimal.

### Pass 6 — Low RAM, images, icons and resources

Inspected: `PhotonImageLoader.kt`, `OfflineArtwork.kt`, `ThumbnailPolicy.kt`,
`PhotonDesignColor.kt`, `PhotonDesignType.kt`, `design/icons/README.md`, icon
builder in `old-photon/build.gradle.kts`, font existence and every SVG/XML parse.

Confirmed defects:
- All video cards forced a 640×960 decode, wasting memory for small/landscape
  cards. Removed the forced request size so Coil AsyncImage uses layout
  constraints. Portrait artwork and offline paths remain intact.
- Fixed 64MiB image memory budget and 16 concurrent artwork requests ignored
  low-RAM devices. Budget now capped at 16MiB on low-RAM/≤128MiB heap-class devices,
  48MiB otherwise, using one-eighth heap class with a 4MiB minimum; low-RAM requests 4/2
  total/per-host, normal 12/6. Disk cache remains explicitly bounded.
- Icon README was literally truncated and pointed to the wrong module's generator.
  Repaired drawing/build instructions. Upstream Prism icon API names are real.

Existing thumbnail policy downgrades unsigned standard maxres/hq720/sddefault JPGs
into hqdefault; custom/signed/portrait URLs are preserved. It is not a universal
404 fallback for every image format. All 124 SVGs parse and use supported top-level
shape tags. Static parsing is not the Gradle generator/rendering test or an OOM test.

### Pass 7 — TV/STB focus, safe zone and Back

Inspected: `PhotonTv.kt`, `TvPosterCard.kt`, `TvCinemaPlayer.kt`,
`TvShortsPlayer.kt`, `TvBackDispatcher.kt`, `TvPlaybackPolicy.kt`,
`PhotonActivity.kt`, `PhotonPlayerView.kt`.

Confirmed defects:
- Activity-level Back always paused/closed playback before cinema's BackHandler
  could dismiss a shelf/HUD. Cinema now owns the remote callback while mounted,
  uses a shared shelf→HUD→exit policy for Activity and Compose Back, and clears
  its callback only if still the owner. Parent recomposition no longer overrides
  it; root focus is restored when dismissing shelves/HUD.
- TV content horizontal margin was 40dp, below requested 48dp base. Set 48dp/27dp,
  with persisted, clamped 0–48dp extra inset and remote-operable Settings buttons.
- TV cards used only 1.025 focus scale and shared the phone's 12–20sp typography.
  Cards now scale 1.05 with existing 3dp accent border/raised tonal surface. Added
  dedicated 22–24sp title/body and 32–36sp heading TV typography and tnum timers.

Open: rail is not collapsible or replaced by requested five destinations; full
focus graph/dialog restoration/large-font grid fit and actual overscan need
physical-TV validation. Shorts/Settings-dialog Back priority is not proven by the
cinema policy test. Existing cinema HUD auto-hide remains 6 seconds while playing.

### Pass 8 — Local data, RSS and backups

Inspected: `data/db/Database.kt`, schema paths, `data/Backup.kt`,
`ui/screens/BackupUi.kt`, `PhotonStore.kt`, `PlaybackQueue.kt`,
`SubscriptionsScreen.kt`, `PhotonViewModel.kt`, `connect/Upnp.kt`.

Confirmed defects:
- Backup ZIP manifest used unbounded `readBytes`; payload copies had no expanded
  byte/entry budget. Added streaming `BackupReadLimits`: manifest ≤1MiB, ≤100,000
  entries, total unpacked bytes limited to initial free space minus 32MiB, reject
  unsafe/unsupported paths, duplicate manifests and unsupported formats. Failure
  deletes staging and propagates to the existing restore-error UI. Tests cover
  exact budgets, oversized manifests/payloads and empty-entry bombs. This is the
  retained legacy backup path, not a new PhotonRoot JSON backup UI.
- Corrupt PhotonLibrary preferences silently reset to defaults then could be
  overwritten. Original raw payload is now preserved in a recovery preference
  and a sanitized diagnostic recorded. No automatic repair/recovery UI is claimed.

RSS subscriptions and one-tap schema-versioned JSON import/export are absent from
PhotonRoot. Current subscription feed uses the actual Google account. No local RSS
feed has been substituted for it. UPnP XML rejects DOCTYPE/ENTITY before parsing;
that is not an RSS feature or RSS-special-character test.

## 2. Exact Code, Build & Feature Fixes Applied

Changed paths are recorded in Git (commits `beffd5d`, `50f8040`). All paths below
are relative to the repository root; braces expand to individual changed files.

- `app/{proguard-rules.pro,r8-trial.pro}`: rules corrected as described in pass 1.
- `app/src/main/AndroidManifest.xml`: disable account-bearing platform backup.
- `app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`: explicit exclusions.
- `app/src/main/java/com/example/data/PhotonModels.kt`: strict duration parser.
- `app/src/test/java/com/example/PhotonModelsTest.kt`: malformed/overflow regression.
- `app/src/main/java/com/example/auth/GoogleAuthHelper.kt`: observable cookie cleanup.
- `app/src/main/java/com/example/player/YouTubePlayerController.kt`: observable WebView failures.
- `app/src/main/java/com/example/ui/screens/YouTubeAccountSyncSheet.kt`: observable cleanup.
- `baselineprofile/{build.gradle.kts,README.md}` and its three Kotlin files:
  target/package correction, explicit exclusion/unsupported-journey documentation.
- `design/icons/README.md`: actual generator path and repaired instructions.
- `old-photon/src/main/AndroidManifest.xml`: links/config-change coverage.
- Under `old-photon/src/main/java/com/prism/music/`:
  - `data/{Backup.kt,BackupReadLimits.kt}`: streamed archive safety.
  - `data/LinkHandling.kt`: observable missing-system-action fallback.
  - `connect/HttpServer.kt`: observable connection failure.
  - `playback/sing/Sing.kt`: interrupt restoration.
  - `photon/PhotonActivity.kt`: cinema Back ownership and TV inset settings.
  - `photon/PhotonViewModel.kt`: timestamp handoff and nonfatal-read diagnostics.
  - `photon/VideoRepository.kt`: unified URL validation, timestamp parsing, diagnostics.
  - `photon/{OfflineArtwork,PhotonImageLoader}.kt`: adaptive artwork sizing/budgets.
  - `photon/PhotonPlayerView.kt`: canceled-key guard.
  - `photon/PhotonPlaybackService.kt`: observable nonfatal history failure.
  - `photon/PhotonStore.kt`: corrupt-payload preservation and TV inset preference.
  - `photon/PhotonTv.kt`: safe-zone margins.
  - `photon/{PhotonDesignType,YouTubeLayout}.kt`: tabular numerals and TV typography.
  - `photon/{TvCinemaPlayer,TvPlayerBackPolicy}.kt`: shared Back priority/ownership.
  - `photon/TvPosterCard.kt`: focus scale.
- Original tests under `old-photon/src/test/java/com/prism/music/photon/`:
  `YouTubeLinksTest.kt`, `TvPlayerBackPolicyTest.kt`, `BackupReadLimitsTest.kt`.
- `tools/audit_photon.py`, `docs/audit/{source-inventory.json,static-output.txt}`:
  reproducible category inventory and XML/SVG/static boundary checks.

No version bump, release publication or release email is justified by this audit
alone. The already-published APK does not contain these new source changes.

## 3. Android (Phone/Tablet) & Android TV UI Implementation

Implemented in this repair set: tabular numerals in original Photon styles;
TV-specific type scale; larger poster-card focus scale with existing accent border
and raised surface; base TV inset plus saved remote-adjustable extra inset;
cinema shelf/HUD-first Back callback shared with native Activity dispatch.
Existing AMOLED/teal tokens and Inter file were present before this work.

**Not implemented/accepted:** new three-tab Feed/Search/Library architecture,
Focus Mode/study status, requested overflow actions, swipe-dismiss mini-player,
35% brightness/volume/scrub gesture arbitration, ≥840dp 65/35 tablet panes,
collapsible five-item TV rail and complete deterministic focus graph. Existing
long-press boost/double-tap gestures are not evidence of the entire requested
nonconflicting gesture system.

Power-tool acceptance matrix:

| Requirement | Actual status |
|---|---|
| Share links + timestamps | Source and regression tests added; external-intent device flow still needs testing |
| Focus Mode + study timer | Not implemented |
| True audio-only/screen-off | MediaSessionService/sleep timer/background policies exist; requested audio-only switch not implemented |
| A–B + per-channel speed | Not implemented |
| RSS + one-tap JSON backup | Not implemented; hardened legacy ZIP restore is not equivalent |
| SponsorBlock + DeArrow | Not implemented |
| Four-digit local TV room/QR queue push | Not implemented; legacy six-digit music handoff/casting is not equivalent |
| Low-RAM + codec selection | Artwork budget improvements implemented; hardware codec preference/fallback not implemented |

## 4. Verification Commands & Real Outputs

### Local commands

`./gradlew :old-photon:compileDebugKotlin :old-photon:testDebugUnitTest :app:compileDebugKotlin :app:testDebugUnitTest`

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
Gradle exit: 1
```

`python3 tools/audit_photon.py` — full output in `docs/audit/static-output.txt`:

```text
1-build: inspected 9 files; 33 search hits (manual review required)
2-manifests: inspected 3 files; 34 search hits (manual review required)
3-network: inspected 20 files; 108 search hits (manual review required)
4-playback: inspected 47 files; 87 search hits (manual review required)
5-compose: inspected 543 files; 407 search hits (manual review required)
6-resources: inspected 131 files; 236 search hits (manual review required)
7-tv: inspected 20 files; 128 search hits (manual review required)
8-storage: inspected 18 files; 45 search hits (manual review required)
XML/SVG parse: PASS (234 files; 124 icons)
Manifest links/permissions, MainActivity and Inter existence: PASS
```

Multiline empty-catch search across both main Kotlin trees: **0 empty handlers**
after repair (before: 7). Comment-only catches in the Photon core were made
observable separately. No new TODO implementation stubs were added; removed the
sample backup TODO. `git diff --check`: exit 0, no output.

### CI (must record actual final result, not assume success)

Source `50f8040f06a0987f09058b534a275923d0c5cc0b`:
- Original compile/focused regression suite: run `46178532145` — **success**.
- Supplied app compile/unit suite: run `46178532180` — **success**.
- Real-frame emulator smoke: run `46178532153` — **success**.
The same three checks also succeeded at `c5865fc` (runs `46179156185`,
`46179156240`, `46179156178`). Full raw log download from this workspace fails
with `results-receiver.actions.githubusercontent.com ... EOF`; these success
claims are GitHub check/run conclusions, not invented stdout.

Earlier candidate `beffd5d` runs were superseded/canceled, not accepted as final
verification. Original workflow runs `:old-photon:assembleDebug`,
`:old-photon:compileDebugAndroidTestKotlin` and `:old-photon:testDebugUnitTest
--tests 'com.prism.music.photon.*'`; this is **not the entire legacy unit suite**.
Supplied workflow runs `:app:assembleDebug` and `:app:testDebugUnitTest --tests
'com.example.*'`. Emulator smoke exercises a production service/controller with a
local decoded clip; it does not test public YouTube, every new HUD interaction,
or a physical phone/tablet/TV. Full R8, migration, low-memory stress, API12–16+
foreground transitions, public-stream errors and cross-device feature acceptance
remain blockers to any complete-overhaul claim.


### Unfiltered tests and lint: FAILED release gate

Added `.github/workflows/photon-audit.yml`: runs both entire module test suites,
then lint for both modules using `--continue`, retains reports, and posts an API-
readable evidence check. `continue-on-error` is used only to collect every result;
the final gate tests the actual step **outcomes** and fails if any command failed.
GitHub's intermediate green completion icons therefore do NOT imply test success.
An earlier chat update misread those icons and was explicitly corrected.

Actual run: https://github.com/HayMayia/Photon/actions/runs/46179156290
Source: `c5865fc67b2447aafe7aa949120b43ec4fa7444b`. **Conclusion: failure.**
Full API evidence is preserved verbatim in `docs/audit/full-ci-evidence.txt`;
all 100 error/fatal lint findings with file/line/root message are extracted in
`docs/audit/lint-errors.json`. No lint baseline or blanket suppression was added.

```text
old-photon: tests=800 failures=11 errors=0 skipped=10
app: tests=6 failures=0 errors=0 skipped=0
old-photon: lint issues=507
app: lint issues=75

audit-original.log: BUILD FAILED in 10m 45s
audit-supplied.log: BUILD SUCCESSFUL in 46s
audit-lint.log: BUILD FAILED in 5m 36s
```

Original suite: 779 passed, 11 failed, 10 skipped. The 11 failures were:

| Exact test | Observed cause; not all classified as environment-only |
|---|---|
| `LiveApiTest.streamResolutionAndRangeRequests` | NewPipe SignInConfirmNotBotException / LOGIN_REQUIRED |
| `LiveApiTest.downloadThroughput` | Same explicit YouTube bot/sign-in rejection |
| `StreamChoiceLiveTest.directVideoLinkLater` | NoSuchElementException selecting a live stream format |
| `StreamChoiceLiveTest.opusIsPickedWheneverOffered` | Assertion `checked some songs` failed |
| `StreamChoiceLiveTest.videoRangesAreConsistent` | NoSuchElementException selecting a live stream format |
| `ThemeGalleryTest.badEntriesAreSkipped` | Missing `../themes/index.json` |
| `ThemeGalleryTest.everyLookInTheGalleryIsUsable` | Missing `../themes/index.json` |
| `LyricSearchLiveTest.linesFindTheirSongs` | Expected lyric search match absent |
| `QuickPicksLiveTest.quickPicksAreFoundInSpanish` | Live-result assertion failed |
| `DirectStreamLiveTest.songsResolveInOneRequestAndStreamWhole` | Expected OK, got LOGIN_REQUIRED |
| `LiveStreamLiveTest.liveStreamPlaysFromHls` | NewPipe SignInConfirmNotBotException |

New confirmed missing-reference bug (pass 1/8): retained ThemeGalleryTest required
files that had not been imported. Restored **actual upstream** `themes/README.md`,
`themes/index.json`, `themes/nothing-theme.prismlook` from the pinned Prism commit
`4a10666cfaff7114d9a53088e0adf393aebd2463`, with `themes/UPSTREAM.md` provenance.
No dummy/empty gallery was substituted. Commit `2a4bd2f` adds ThemeGalleryTest to
both original-workflow test invocations. Its new validation is tracked separately;
the earlier full-suite failure is not retroactively marked successful.

Lint blockers discovered by the compiler/lint pass (all precise paths/lines in
`lint-errors.json`):
- 68 `UnsafeOptInUsageError`: Media3 unstable APIs without required annotations.
- 13 `StateFlowValueCalledInComposition`: non-observed reads can leave UI stale,
  in retained `Components.kt`, `HomeScreen.kt`, `LyricsYourWay.kt`, `PlayActions.kt`
  and legacy `ui/player/PlayerOptions.kt`.
- 8 `UseAppTint`: widget resource lint errors; widget layout compatibility needs
  review rather than a blind namespace replacement.
- 7 `RestrictedApi`: includes Activity-level dispatchKeyEvent in production and
  debug TV host; preserving reliable remote Back while reviewing the AndroidX
  annotation contract is still required.
- 3 `NonObservableLocale`: locale reads in legacy player/settings Compose paths.
- 1 `ResourceType`: `connect/WebRemote.kt:81` raw-resource-type mismatch.

These 100 lint errors are **not fixed** in this repair set. Their presence prevents
an all-pass/release-ready claim even though production compilation and focused
regressions pass. The 9 remaining live-data failures also remain unresolved; bot
rejection is observed for four cases, not a justification to dismiss every failure.

Additional documentation changed: root `README.md` now identifies original versus
replacement modules correctly. `.github/workflows/old-photon.yml` adds the restored
gallery regression to the existing targeted gate. No new published APK exists.


### Restored-fixture verification: final source `2a4bd2f`

GitHub verified the exact source SHA
`2a4bd2f478748ec87740c83610b987e4d0a83e8f`:

- Original build + Photon regressions **and restored ThemeGalleryTest**:
  https://github.com/HayMayia/Photon/actions/runs/46180837709 — **success**.
- Production service/controller decoded-frame emulator smoke:
  https://github.com/HayMayia/Photon/actions/runs/46180837712 — **success**.
- Supplied application build and tests:
  https://github.com/HayMayia/Photon/actions/runs/46180837757 — **success**.

Thus the two missing-gallery test cases now pass in the targeted rerun. The full
800-test suite and full lint have **not** been rerun after restoring fixtures:
the recorded full audit remains failed, with 9 other live-result failures and
100 lint errors unresolved. No tests were deleted/skipped to get the focused
check green, and lint severity has not been lowered.

`docs/audit/changed-files.txt` lists every changed/added path in this turn.
