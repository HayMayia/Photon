# 0.11 playlist Shorts, hold gestures, account hearts and artwork

## Playlist Shorts

Playlist renderers often omit Shorts badges. Visible cards now resolve presentation separately from stream playback:

- Explicit Shorts renderer metadata is still accepted. Missing classification is checked against the exact video's YouTube canonical Shorts/watch route; never inferred from duration or image dimensions.
- Anonymous metadata requests do not carry account cookies or follow redirects. Connected-account fallback uses the existing pinned, allowlisted YouTube session, not a new login/session. Both request paths are read-only. There are two concurrent metadata slots, bounded caches and a response-size limit.
- Unsupported/consent/failed responses are not cached as proof of an ordinary video. Negative account-scoped lookups can be retried after account revision changes. Stale account responses are discarded; account invalidation clears presentation state.
- Resolved classification reaches both the thumbnail geometry and the open callback, including playlist rows whose original `Video.short` was false. Stream extraction uses the latest metadata rather than an earlier snapshot. Late metadata can update the viewport without changing the playback request/restarting it.
- Known Shorts get 9:16 thumbnail/inline-player bounds. Directly opening a known Short uses the Shorts viewer in either skin; queue/video-format Shorts can retain the ordinary player but with portrait bounds. Both the Shorts viewer and the video viewport have an explicit fullscreen action. Shorts fullscreen is portrait; regular videos remain landscape. Media3 FIT and safe insets remain in use, without stretching/cropping playback.
- New downloads carry the flag/artwork. Successfully resolved existing downloaded Shorts retain the classification in download metadata for later offline use; existing thumbnail files are not deleted. Unknown old offline downloads cannot be accurately classified without a successful online lookup.

This is metadata-dependent, not a promise that every current YouTube renderer/response is supported. If a particular playlist Short still remains landscape, capture its public URL/renderer case for a regression fixture. No live Google/device classification test is claimed by synthetic canonical fixtures.

## Hold for 2×

The existing native touch observer handles a long press over playing, non-live video, excluding interactive player controls. It requests 2× speed, shows a persistent pill, performs Android's standard long-press haptic, and restores the exact previous speed/pitch on release/cancel, focus loss, detach, player replacement, pause or media-item transition. It temporarily prevents the Shorts pager from taking the held gesture and cancels the pending native click, without placing a gesture-stealing overlay over controls. Two-finger interaction ends the boost. Double-tap 10-second seeking remains separate.

Haptics obey device/system feedback settings and hardware support; no forced vibration permission or live-stream acceleration is added. Actual Android gesture dispatch, haptic intensity and speed restoration need physical testing; metadata unit tests do not prove these behaviors.

## Screen timeout

The activity's keep-screen-on flag follows actual playback or buffering with play intent while the activity is visible (STARTED or RESUMED, including visible PiP/split-screen). Pause/end/stop and fully backgrounded playback clear it. There is no background wake lock and the power button still works normally. Check gesture/three-button phones, buffering, pause while buffering, PiP dismissal and returning Home on a device.

## Existing likes, not only actions made here

The connected Home account's available Liked-videos pages are indexed in memory, with account/revision checks, continuation-loop detection and a 200-page safety limit. Visible/opened videos also have bounded read-only like-control lookups. Opening the Liked shelf seeds confirmed IDs. Known likes are red filled hearts across the shared/player/playlist/Shorts/native feed surfaces. Pressing one uses the existing explicit Google unlike confirmation and explains removal from Liked videos; nothing changes on the tap alone.

Unknown/failed reads do not invent an unliked state or fake a successful Google write. A modern inline/keyed like entity must be linked from the current primary-video controls or bound to the target video; unrelated entities are not accepted. Matching opaque entity keys are resolved to their framework payloads for both read-only state and mutation read-back. Late control reads do not overwrite a newly confirmed state. The list index avoids overwriting explicit known-false state with a potentially older page; list/index results are snapshots, not real-time push synchronization. Google may cap returned likes, and unsupported states/errors can leave some hearts unresolved. Account-list Refresh can request the index again. Existing account mutation/read-back and Google history-consent rules are unchanged; this feature does not claim complete all-activity synchronization.

## Channel artwork and interface refinements

Legacy and modern channel/playlist collection artwork is retained, validated to known HTTPS artwork hosts and rendered in lists (round channel avatars; playlist covers). Available returned uploader avatars are rendered on native feed cards instead of initials; unsupported/missing artwork has an honest icon/initial fallback. The native mode also has filled red hearts, a standard-style settings gear, direct fullscreen and a 2× pill. App identity, licensing and the previous unofficial-client disclosure remain unchanged.

These refinements are not a pixel-by-pixel certification of every YouTube screen. The exact app version, device size/font scaling and reference screenshots are needed for that visual comparison; some shared controls remain different.

## Manual acceptance checklist

- [ ] Public and connected/private playlist Shorts: portrait rows, actual correct video on tap, portrait viewport and direct fullscreen. Test early tap while metadata/stream extraction are both loading, queue advance, offline after an online classification, and account change during lookup.
- [ ] Hold center/sides of video and Shorts: one haptic (when enabled), persistent 2× badge, actual speed change; release/cancel/swipe/background/rotation/next item restore prior 0.75×/1×/1.5× speed and pitch. Controls, subtitles, seeking and pager scrolling still work. Live/paused/PiP controls do not start boost.
- [ ] Screen stays awake beyond the phone's timeout during visible playback/buffering/PiP. Pause (including while buffering), stop/end, PiP dismissal and fully backgrounded audio allow normal timeout.
- [ ] Existing Google likes fill hearts, including account playlists and Shorts. Press a filled heart, cancel the warning (no mutation), then explicitly confirm removal and verify on official YouTube. Test reconnect, account switch, slow reads, partial/capped lists and unavailable controls without fabricated state or cross-account results.
- [ ] Channel avatars and playlist covers appear for legacy and modern lists; no wrong creator image is substituted. Test unavailable artwork/offline fallback.
- [ ] Compare both skins, playback/download/cast/comment paths and caption/fullscreen visibility on a real phone. Supply reference YouTube screenshots for a closer visual pass.


## Build verification

Version **0.11.0-alpha / code 14**, source `8bc8b9d8c55ea03236d93407b9d4e13ae3d49be6`: [run 40904639198](https://github.com/HayMayia/Photon/actions/runs/40904639198) **PASS in 4m16s**. APK assembly, 163 focused test methods, permanent signing-certificate matching and artifact upload passed. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40904639198/artifacts/12911391018): **31,320,756 bytes**, contains `app-debug.apk`. Install over the existing app, without uninstalling. Six Python signing tests and 92 main XML/SVG parses passed locally.

Ten added synthetic tests cover canonical Shorts/unknown/unsafe responses, resolved playlist opening, legacy/modern collection artwork, pinned authenticated Shorts reads, read-only likes, linked modern entity isolation and modern mutation/read-back. They do not exercise a real account, Android gesture dispatch/haptics, screen timeout, live YouTube canonical responses, phone rendering or pixel-level visual matching. The checklist above remains pending; no public-readiness or complete all-activity-sync claim is made.


## 0.12 follow-up: auto-rotate, metadata, pictures and simpler UI

The user reported that 0.11 still lacked channel pictures and playlist Shorts presentation, and clarified that clipping happens when physically rotating, not pressing the fullscreen button. Its synthetic tests were not evidence of those device outcomes.

- **Rotation:** configuration landscape and manual fullscreen now share an immersive layout policy. An expanded physically rotated player hides the details column and takes the available height with Media3 FIT and safe drawing insets. Orientation requests remain separate from system-bar effects; PiP, minimized playback and the Shorts feed are excluded. Returning portrait restores the regular view; Back/close in automatic landscape minimizes rather than trapping the player in fullscreen.
- **Shorts:** matching reel-video commands, relative/absolute Shorts navigation commands and target-bound explicit player flags take precedence over `/watch` canonical metadata. Recommendations for other IDs cannot classify the opened video. No duration/aspect-ratio/eligibility guess is introduced. These fixes still depend on YouTube returning supported metadata; blocked/unknown results are not fabricated.
- **Channel pictures:** missing or failed account/channel-header avatars can fetch public NewPipe channel artwork. Two lookup slots, 256 successful cache entries with a 30-minute TTL and a bounded UI image map limit work. Known protocol-relative artwork becomes HTTPS; unknown image hosts remain rejected. Circular initials stay visible during loading/error/offline conditions; they are fallback artwork, not claimed creator photos.
- **Less everyday text:** removed slogans and routine player/library explanations, compacted authenticated Home shortcuts, shortened action labels, collapsed descriptions to one preview line, and moved advanced history, optional comment authorization and About/help behind explicit controls. Consent dialogs, destructive account confirmations, errors, history safeguards and accessible legal/source information remain.
- **Icon:** adaptive-safe faceted play/light mark, with the same package and permanent signing identity.

**Verification:** 0.12.0-alpha/code 15, source `6bed3dfdf7bb8e005b9d599d16c3a24f03830a6c`; [run 40908141737](https://github.com/HayMayia/Photon/actions/runs/40908141737) passed in **5m4s**: APK assembly, focused Photon unit tests (170 test methods in source), registered permanent signing-certificate check and artifact upload. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40908141737/artifacts/12913061133): **31,328,143 bytes**. Six local signing tests and all 92 main XML/SVG parses pass.

Seven new synthetic tests cover automatic versus button fullscreen policy, excluded surfaces, reel proof over watch canonical, different-video isolation, relative/absolute command URLs, bound explicit flags and trusted protocol-relative artwork. They do not run Android orientation/rendering, a live Google account, public channel extraction or actual YouTube Shorts pages. No phone-tested or all-issues-proven-resolved claim is made.

Pending physical checks: rotate an expanded video without pressing fullscreen (captions/controls entirely visible), rotate back, minimize/Back, then verify playlist Shorts thumbnails/opening, avatar success/error/offline states and both layouts. Existing consent, likes, hold boost, downloads, PiP and playback checks still apply.
