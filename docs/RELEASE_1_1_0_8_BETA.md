# Photon 1.1.0.8 Beta

- Home now requests the signed-in account's authenticated Home browse feed after bootstrapping the existing Google connection, rather than using bootstrap HTML recommendations. No search-generated replacement or additional session.
- Native YouTube Home cards use the parent row's committed thumbnail frame, so late Shorts metadata cannot narrow just one card before row regrouping.
- Playlist parsing retains unselected playlist entries (the selection flag identifies the playing entry, not whether other rows should be visible). Unselected navigation tabs remain excluded.
- Shared Music, shortened, and sentence-embedded playlist links open the playlist instead of just its first video. Connected-account playlist URLs use the account loader, including private playlists.
- Selecting a playlist entry binds an explicit playlist queue. Phone and TV Up next follow that queue in source order, and Next loads continuation pages when needed. Selecting another entry preserves the queue. Playlist ends and loading failures never substitute unrelated recommendations.
- Playlist continuation can also be loaded from Up next. Account changes invalidate private queues. Normal non-playlist playback retains its own related-video behavior.

Validation: targeted regression tests added for playlist rows, shared links, queue order/end/continuation merge, and signed Home browse vs bootstrap cards. Hosted Android build/test and publication pending. Live personalized feed and physical phone/TV checks have not been performed in this environment.
