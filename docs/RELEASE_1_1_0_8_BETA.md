# Photon 1.1.0.8 Beta

- Home now requests the signed-in account's authenticated Home browse feed after bootstrapping the existing Google connection, rather than using bootstrap HTML recommendations. No search-generated replacement or additional session.
- Native YouTube Home cards use the parent row's committed thumbnail frame, so late Shorts metadata cannot narrow just one card before row regrouping.
- Selection filtering is now restricted to navigation tabs, so selection flags on other containers cannot discard playlist entries. Unselected navigation tabs remain excluded.
- Shared Music, shortened, and sentence-embedded playlist links open the playlist instead of just its first video. Connected-account playlist URLs use the account loader, including private playlists.
- Selecting a playlist entry binds an explicit playlist queue. Phone and TV Up next follow that queue in source order, and Next loads continuation pages when needed. Selecting another entry preserves the queue. Playlist ends and loading failures never substitute unrelated recommendations.
- Portrait playlist playback leaves room for the details/Up next panel outside fullscreen; fullscreen stays portrait.
- Playlist continuation can also be loaded from Up next. Account changes invalidate private queues. Normal non-playlist playback retains its own related-video behavior.

Validation: hosted Android run **41454902703 PASS (6m44s)**, including debug/release builds, unit tests, permanent certificate verification, and publication. PR run **41454911976 PASS**. Regression coverage includes selected/unselected playlist rows, shared playlist links, queue order/end/continuation merge, and signed Home browse vs bootstrap cards. Existing Shorts geometry tests also pass. These are fixture/policy/build checks, not a live-account or physical phone/TV test.

Published source: `531a066698ece9105df2ce1ef1be71bed514f8e3`, version code 37.
Verified release asset: Photon.apk ID 701906199, 25,353,004 bytes;
SHA256 `7d5423e02a0ad52355a5e08f27bf8bd96c345451db344a3b1a21031b4af371fb`.

Live personalized feed and physical phone/TV checks have not been performed in this environment. No Google history setting was changed to force different recommendations.
