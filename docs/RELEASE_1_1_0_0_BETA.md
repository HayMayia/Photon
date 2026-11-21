# v1.1.0.0 Beta

- Faster, bounded thumbnail requests: dedicated public-image connections, explicit memory/disk cache, bounded decoding and smaller available standard previews. Signed and portrait artwork and saved offline images are preserved; a failed resized preview can retry its original source.
- Recognize genuine Shorts routes, reel commands and badges directly in playlist/feed rows. Support modern video lockups instead of silently omitting them. Normal videos are never classified by duration or portrait dimensions.
- Start a small amount of optional format work after admitting playlist rows; retain proven formats in open collections. Prefer one authenticated format read instead of serially checking both public and account pages for every normal video. Already-proven Shorts skip format networking. Failed lookups never hide or block playlist data.
- Correct four-component update ordering and select the highest eligible release rather than assuming API list order.
- Fix playback reopening losing the selected row's proven Shorts format, read-only replies showing a nonfunctional posting box, and headset mode remaining enabled after closing playback. Headset rendering waits for the real controller and keeps the screen awake.

Same application identity and permanent signing key; update in place through Home. Existing Google-account authority, TV/mobile behavior, playback controls, offline files and original-channel audio selection remain in place.

## Verification

Verified source `fec2d7be5c0ad516811edceb12ae516b3e5c14ee`, code 29: [run 41418756115](https://github.com/HayMayia/Photon/actions/runs/41418756115) **PASS in 10m4s**. Debug/release builds, the 213-method focused suite, permanent-signature checks and publication succeeded. Public tag targets that exact source; `Photon.apk` is 25,336,136 bytes. Six local Python signing tests also pass. Physical-device thumbnail timings, scrolling, touch, VR and surround output have not been measured in this environment. This is a focused source audit, not a claim that every possible app/device error has been eliminated. Unknown formats can still be identified later if YouTube omits format proof from the playlist response; the list is never held hostage to that lookup.
