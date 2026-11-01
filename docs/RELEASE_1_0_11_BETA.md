# v1.0.11 Beta — playback startup, gestures and simpler controls

## Startup

Playback extraction no longer starts alongside optional presentation/account probes on each tap. Actual extractor presentation is cached first and playback is published before delayed account reads. Stream-detail cache grows from 8 to 16 items, unrelated-video lock stripes from 16 to 64, and the next known Short's metadata is warmed after current startup (one item, no media download, no fake view/history). Metadata connection/read/call timeouts are bounded at 10/20/35 seconds rather than 20/35/90. This removes avoidable work/contention and long request stalls, not YouTube/network latency itself. No measured physical-device startup improvement is claimed.

## Gestures

Double-tapping a Short requests Like, never unlike or seek. Normal-video double-taps seek 10 seconds only in the inner side zones (8–38% left, 62–92% right); centre and extreme edges do nothing. Single background taps are confirmed before toggling controls so double taps do not toggle them first. Controller buttons keep their own touch routing. Hold-to-2× remains.

## Shorts and comments

Shorts overlay is compact: Like, Comments, Share and More, with details/save/fullscreen/subscription/VR in More. Comment input is reduced to Add a comment + Post, with account identity and necessary errors; technical IDs, setup/troubleshooting controls and routine explanations are removed from the composer. Native Google permission is still required when not authorized; authorization never automatically posts a draft. Replies remain read-only.

## VR viewer and audio

VR viewer is available from player settings, normal video actions and Shorts More, including offline playback. A single decoded source is drawn to two aspect-fit eye viewports. It is a flat side-by-side **2D** phone-headset viewer, not stereoscopic depth, lens correction, head tracking or a 360 conversion. Exit restores the ordinary player. Device/GL/headset validation remains pending.

Original-language audio selection now prefers the highest supplied channel count before stereo, including supported MP4/WebM/AC3/EAC3 audio sources. Within the same channel count, compatible MP4 is preferred to retain existing AAC download support. No app stereo channel ceiling is imposed, no audio is upmixed or transcoded, and dubbed/descriptive tracks do not override original language. Source and output device/decoder still determine actual surround playback; stereo-only content cannot become genuine 5.1. Non-MP4/non-AAC selections that cannot be muxed by the existing MP4 downloader are not offered as downloads rather than silently converted to stereo.

Regression tests cover gesture zones/Shorts like intent, original-language/multichannel selection and the compact overlay. Physical phone/TV, native touch, GL/VR, surround-output and timed network startup verification remain pending.

After publication use **Home → Update to v1.0.11 Beta**. If cached, force-stop/reopen then Home. Same permanent signing key, no uninstall or storage clearing, no manual APK delivery.
