# Google-account library and restored Prism Home (0.8 alpha)

**0.9 supersedes the read-only/local-library split described below:** account actions now send real Google mutations and the parallel local-library UI is removed. See [ACCOUNT_WRITES.md](ACCOUNT_WRITES.md) for current behavior, verification and remaining limits. The rest of this document records the 0.8 reader implementation.

The user confirmed account Home works in 0.7.1, but its bare list changed the interface substantially and History/Playlists/Channels/liked videos still showed only device data. 0.8 restores Prism's branded header, spectrum divider, accent-coloured shortcut tiles and section typography while keeping authentic account recommendation order. Explicit Explore videos/music searches are separate actions, not replacement account recommendations.

## Where items appear

- Home → **History / Playlists / Liked videos / Channels**.
- Library → **YouTube account**, then the corresponding section.
- Library → **On device** retains the existing local saved videos, local watch history/resume positions, downloads, locally followed channels and local playlists. Download shortcuts select this source automatically.
- Channels means **subscribed YouTube channels**; opening a channel uses the existing native channel screen. It is not an enumeration of all channels owned by a Google identity.

No uninstall or clearing app data is needed. Account lists do not replace or erase local lists. Absence in the old local UI did not establish deletion from Google. Existing account Home login is reused; no extra account login is required just to read the library.

## Source and limits

Read-only authenticated requests go to fixed `www.youtube.com` routes, using the same account-index/revision-bound credentials as Home:

| Section | Website route |
|---|---|
| Previously watched videos | `/feed/history` |
| Account playlists | `/feed/playlists` |
| Actual YouTube liked videos | `/playlist?list=LL` |
| Subscribed channels | `/feed/channels` |
| Open a returned playlist | `/playlist?list=<validated-id>` |

These are unofficial website interfaces, not official Data API history access. Parsing recognizes legacy video/playlist/channel renderers and supported modern lockups in primary selected content. Sidebar/other tabs/menus are excluded; invalid IDs cannot become arbitrary requests. More items use server continuations with authenticated web context. Pages retain server order, deduplicate IDs, reject repeated tokens and bound pagination. An explicit Load more action reads further pages. Reading an account library never uploads old local history or changes Google likes/subscriptions/playlists.

A signed-out response is rejected. Errors/empty/unsupported layouts are labelled rather than replaced with local items or described as proof that the Google account is empty. Google may expose only a subset of past activity; this is not a full Google Takeout/My Activity export or an exact account-wide watched-duration ledger. Paused history can still show previously saved entries. Opening an authenticated playlist supports reading its rows, but playback still uses the existing extractor; private/restricted video playback is not promised.

The lists are read-only. The app's local Save/bookmark and local subscription/playlist controls are not relabelled as Google mutations. Account playlist editing, adding/removing YouTube likes or subscriptions, owned-channel management, deleted/private video playback and complete library synchronization are not implemented by this change.

Account results and continuation/context data stay in process memory, separate from the persistent device library. Account review/switch/disconnect cancels work and clears account rows. Responses from an old revision cannot populate a new account's library. Cookie host scope, no redirects/replays and response-size bounds remain in effect. No new telemetry/backend or credential logging is introduced.

## Acceptance

- Compare each section with official YouTube for the **same account**: previously watched videos, liked videos, public/private playlists, subscribed channels.
- Open a playlist, return to playlists, open a channel, play a supported public video, load additional pages and refresh.
- Verify empty/history-paused accounts, missing/changed layouts, unavailable/private videos and network failure are honest and not backfilled locally.
- Switch accounts with a request in flight; ensure old rows disappear and never repopulate the new identity. Sign out and inspect On device for retained local data.
- Check Home's Prism colours/header/shortcut tiles in light/dark/accent modes, music/video exploration, Shorts, download shortcut and account library navigation.
- Confirm independent Google history pause/reporting behavior, comments, captions, seek, casting/downloads and permanent-key upgrade remain intact.

[Run 40884491780](https://github.com/HayMayia/Photon/actions/runs/40884491780), source `a2d2717c06788a9885626c71eb9a860422607b93`, passed APK assembly, **122 focused Photon tests** and permanent signing-certificate verification in 6m47s. [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40884491780/artifacts/12901454220): 31,188,399 bytes, version code 10. Six local Python signing tests and 92 main XML/SVG parses passed. Real account-library/device behavior remains unverified. New tests use synthetic renderers and intercepted responses only; no live Google library or credentials are used. Real-account/device acceptance remains required.
