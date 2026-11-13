# Photon 1.1.0.14 Beta

- TV navigation has a scrollable rail with clearer selection, larger targets and focus restored to the selected section. Cards have a strong focus outline/highlight, subtle focus zoom, equal two-line title slots, correct landscape frames and contained portrait Shorts. Grid padding protects edge focus outlines.
- Add independent **Music in background on TV**, OFF by default for new and existing installations. Explicit opt-in permits music while browsing/outside Photon; TV regular videos remain foreground-only. Mobile music/video defaults remain unchanged.
- Liked Music no longer treats prior cached membership as classification proof. Generic MUSIC_VIDEO_TYPE_UGC is not music proof; exact-track/official-music markers and actual source category remain the supported evidence, not titles or channels.
- Persist positive AND negative per-video classification decisions for six hours, bound to the account revision, outside playback preferences. This avoids repeating non-music metadata lookups after restart. Old unverified cached entries are rechecked once rather than remaining permanently classified as music.
- Admit source-proven/cached results before waiting on unknown metadata, preserve source order, bound unknown lookups to four concurrent requests, and checkpoint each source page. Phone and TV share this loading path. Cached verified songs stay usable while refreshing; newly disproven entries are removed even if another entry remains unresolved.

Hosted validation pending. Initial/restricted metadata may still take time; source categorization can be imperfect and this is not content recognition. Physical TV/remote/background lifecycle and live private-account list checks have not been performed.
