# Native interface modes, Back and fullscreen

## What the switch does

Settings → **Switch to YouTube layout** enables an optional, persisted native YouTube-style presentation. In that mode, open **You → Settings → Switch to Photon** to return. The original Prism presentation is still the default for existing installations; its saved accent and theme preferences are retained.

This is a native interface, **not the official YouTube application, not the actual YouTube website/settings, and not a pixel-perfect reproduction**. Home, search, Shorts shelves, bottom navigation and the black/white appearance have separate native presentation. Some player, channel, account-library, comment and settings controls are shared with the existing client. The center Create button creates a private playlist; it does not upload videos. A full YouTube notification inbox, creator/upload tools and every official app feature are not implemented. Exact visual acceptance requires comparison with the user's particular YouTube version/screenshots and remains open.

App-owned in-app branding is suppressed in this mode, apart from the requested Switch to Photon command. User video/comment/channel text is never rewritten. Android's launcher, notifications, package identity, Google-owned permission screens, diagnostics and external source/license links retain the real application identity; this switch does not impersonate the official app or alter the OAuth registration. The About section still discloses that the client is unofficial and retains access to source/licensing information.

## Same functions, not a second account/session

Both layouts use the same retained view model, media controller/service, extractor/native playback path, subtitle and seek handling, downloads/offline artwork, casting, Google account reads/writes, comment authorization and privacy/history settings. The interface switch changes only `youtubeLayout` in device preferences. It does not create a website player, clear cookies, change account/revision, enable reporting consent, restart playback deliberately, delete downloads, or manufacture a new synced library. Theme/content call sites are kept stable across the switch so the playback-binding effect is not intentionally recreated.

The existing native extraction/playback approach, rather than a YouTube WebView player with a new ad pipeline, remains in use. This is not a guarantee against every possible server-side ad or future extraction change. Existing limitations in [ACCOUNT_WRITES.md](ACCOUNT_WRITES.md), [ACCOUNT_LIBRARY.md](ACCOUNT_LIBRARY.md) and [TESTING.md](TESTING.md) still apply. The new skin does not establish public-release readiness or complete offline/search-history transfer.

Authenticated Home still uses returned Google-account cards and guarded continuations; unknown/failed states do not become search-based personalized recommendations. Native topic chips are explicit searches. The subscriptions tab uses the authenticated subscriptions reader (currently first page), not local subscribed-channel aggregation. Likes/playlist actions use the existing confirmation and read-back safety checks. The optional comment account remains separate and explicitly labelled.

## Back navigation

Back first dismisses a dialog/sheet, leaves fullscreen, minimizes the player, exits Shorts, or closes a collection as applicable. A channel opened from the player returns to that player. The account playlist subpage returns to the playlist list. Ordinary tab navigation has a saved, bounded previous-tab stack; reselecting the same tab does not add duplicate steps. A restored non-Home tab with no stack falls back to Home. At the root, Back asks before moving the app to the background; it does not immediately finish the activity. This tab stack is unrelated to Google watch history. It is not a complete browser-like history of every filter/search query or nested collection.

Finishing a video details request no longer re-expands a player that the user just minimized; expansion follows a new video selection rather than every busy/controller change.

## Fullscreen sizing

Fullscreen requests immersive landscape with transient system bars, restoring prior orientation/bar visibility on leaving it. The viewport respects safe drawing/cutout/navigation insets and explicitly uses Media3 FIT rather than zoom/crop. This is intended to keep the whole video and subtitles inside the visible viewport. It does not alter subtitle tracks or claim device-verified results. Gesture navigation, three-button navigation, notches, embedded captions and translated tracks must still be checked on real devices.

## Device acceptance still required

- Search → Settings → Back returns to Search; Library/You → playlist → Back returns to playlists, then the prior tab. No immediate exit at Home; root confirmation can be cancelled.
- Video → fullscreen → Back leaves fullscreen; next Back minimizes; channel navigation and modal sheets unwind before tab history. Check 10-second gestures, captions, quality switching and PiP.
- Landscape playback on gesture/three-button phones and notched displays: entire video, bottom captions, controls and transient bars remain visible. Compare screenshot with the original clipping report.
- Switch both ways while a video is minimized/playing, then restart the app. Verify saved mode, position/quality/account/cookies/downloads and consent settings survive without a duplicate history report or playback restart.
- In native YouTube-style mode, exercise authenticated Home continuation, search, selected Shorts, subscriptions, You/account library, native download/playback/casting and comments. Verify actual Google writes on the intended account; unit tests do not prove live account effects.
- Compare Home/player/channel/comments/Settings to screenshots of the requested YouTube version. A strict EXACT-YouTube acceptance criterion is **not yet met**.


## Build verification

Version 0.10.0-alpha/code 13, source `bec48bcc0080b204b3d06c67d9da5a25223cac6a`: [run 40896915371](https://github.com/HayMayia/Photon/actions/runs/40896915371) passed in 4m47s with assembly, the 153-method focused unit suite and permanent signing-certificate check. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40896915371/artifacts/12908630044), 31,291,479 bytes. Device/live-account/strict visual acceptance remains pending.
