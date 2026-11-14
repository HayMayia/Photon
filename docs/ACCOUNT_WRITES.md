# YouTube-authoritative account actions (0.9 alpha)

User requirement: future content watched/used in Photon should affect the real YouTube account, not only an independent local library. This means replacing local-only actions, **not erasing the existing Google library** or blindly uploading historical device records.

## What changes in 0.9

- Player/search/related/Shorts heart actions request **Like / Unlike on YouTube**.
- Native channel/player subscription controls request **Subscribe / Unsubscribe on YouTube**.
- Playlist picker reads actual account playlists. **Create on YouTube and add** creates a new **private** playlist with the selected video; existing-list selection sends a real playlist addition.
- Account library adds create/delete controls and removal of a returned video from an opened playlist.
- The default library is the Google account library; the previous parallel On device saved/history/channels/playlists UI is removed. **Downloads** remains because offline files necessarily live on the device.
- No active ViewModel action falls back to `PhotonStore.toggleSaved`, `subscribe`, `createPlaylist` or `playlistVideo`. Legacy fields/functions remain for schema compatibility and old data is not silently destroyed or mass-uploaded.
- The subscriptions video feed now reads Google's authenticated `/feed/subscriptions` first page, not archived local channels. It currently has no continuation UI; account Channels remains the paginated subscribed-channel list. Shorts discovery no longer mines archived local subscriptions/history as sources.
- Device history/resume data is a playback cache, not presented as Google history. Clear-cache settings explicitly do not delete Google activity. Preferences/downloads/artwork remain device-local necessities.

## Direct actions, account binding and verification (v1.0.4 Beta)

Each account action submits directly on tap to the **same Google website account as Home**, not the separate native comment identity. v1.0.4 removes extra confirmation/result dialogs, including unlike, unsubscribe and playlist deletion. Playlist naming still uses an input form; failures remain visible. No delete-all or bulk overwrite exists. Google edit permissions still apply; saved playlists owned by others cannot be deleted as if owned.

The operation is bound to the session revision when requested. Execution checks that revision and readiness again; the network replay guard checks permission at transmission. Account review/change/disconnect clears pending actions and in-memory verified-state caches. Requests already in flight cannot be recalled, but never receive a new account's replacement credentials.

For likes/subscriptions, Photon reads structured current state, sends a desired state, and checks the resulting state with up to three read-only refreshes. It does not retry the mutation. Explicit desired-like/subscribe requests are idempotent: a cache miss must not accidentally unlike/unsubscribe an item Google already considers liked/subscribed. Unknown/conflicting/unrecognized state fails closed rather than guessing from local lists or button text. Modern like entities and legacy like/subscription controls are supported; modern channel-bound subscription entities are also supported, with authenticated directory membership/read-back when channel controls are unavailable. Variant booleans alone never imply current state; unsupported or empty/unverified directories can still remain unknown.

Playlist creation requires a returned playlist ID and authenticated read-back; create-and-add also checks the selected video row. Playlist additions/removals check the returned page. Removing requires the server's playlist-entry/setVideoId, not just an invented video ID. If a row/token is not on the freshly returned page or Google uses an unsupported renderer, the edit may be refused or verification may fail; this is not complete multi-page editing support. Duplicate occurrences and eventual consistency need device testing. Special LL/WL lists are not exposed to generic playlist deletion/editing.

Deletion accepts a successful request response and refreshes the account list; **independent deletion verification is not implemented**. No success-result popup is shown. The user must check official YouTube before retrying. For any ambiguous failure, the server may have applied a change: no local substitute or automatic replay follows. Only verified results update the in-memory like/subscription UI cache.

All endpoints are fixed allowlisted HTTPS YouTube paths: `like/like`, `like/removelike`, `subscription/subscribe`, `subscription/unsubscribe`, `playlist/create`, `browse/edit_playlist`, `playlist/delete`. IDs/names are validated. Requests use the existing host-scoped app-private Google website session and SAPISID authorization, not cookies pasted by a user or a Photon backend. Existing no-redirect/replay/size limits remain.

## Watched activity and what is NOT yet all-activity synchronization

The actual-playback reporter sends one registration per view, only with explicit consent and freshly enabled Google watch history. Existing consent keeps the **15-second** minimum. A new explicit **Include short actual plays** consent lowers the minimum to **one actual playing second**, so short videos/Shorts can be registered without changing old privacy defaults. The explicit Include short plays settings switch enables this mode independently; account changes reset reporting consent. Enabling removes the extra local pause. Genuine downloaded videos played **while online and verified** now bind to their opening account and can be reported too. Pauses, buffering and seek distance do not add watched time. Global Google pause/unknown/stale state remains authoritative and blocks reports. Comments still use native official authorization; the explicit Post tap submits directly without an extra review dialog.

**Do not call this complete transfer of everything the app does.** Search-history synchronization, offline/deferred play transfer, plays below the selected minimum, a full historical-duration ledger, automatic legacy migration, playlist rename/privacy editing, complete multi-page playlist editing, owned-channel management and Google settings with no mapped app action are not implemented. HTTP playback acceptance still is not proof Google stored a history item. A complete requested all-activity experience remains unfinished; these are genuine account writes replacing local-only controls, not a claim of comprehensive sync or public readiness.

## Verification and acceptance

[Run 40889863444](https://github.com/HayMayia/Photon/actions/runs/40889863444), source `0621c01213f3567e2d55a376c45dfb72be6e92ce`, passed APK assembly, **140 focused Photon tests** and permanent signing-certificate verification in 5m31s. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40889863444/artifacts/12904148038): 31,201,269 bytes, version code 11. Six local Python signing tests and 92 main XML/SVG parses passed. These are not live Google mutation/device tests. Synthetic intercepted-HTTP tests cover real mutation endpoints/payloads, private create, initial video, add/remove entry IDs, desired-state idempotence, wrong/conflicting controls, sign-out/revocation, read-back failure and no automatic mutation retry. They do not mutate a live account.

Before publishing:

- Compare each action on the exact same account in official YouTube. Verify pre-existing likes/subscriptions, unlike/unsubscribe, private create/add/remove/delete and server permission failures.
- Test modern/legacy layouts, changed controls, pagination, duplicate playlist entries and server update delays.
- Switch/disconnect/cancel during pending and in-flight requests; no cross-account results or local fallback.
- Verify explicit playback consent, paused/unknown state, short/offline cases and official history appearance; do not claim unimplemented cases work.
- Test comments, captions, ±10s seek, downloads, background playback, data retention and stable-key upgrade.
- Complete Google/public policy and release hardening review separately. A passing APK/fixture suite is not live account acceptance or Google approval.

Protocol reference: YouTube.js endpoint builders/managers for like, subscription, playlist create/edit/delete were inspected to validate request shapes. Implementations/tests are newly written; no account credentials or real history fixtures are committed.
