# Original Photon 1.1.1.2 Beta

Original Photon is now built independently as `:old-photon` from the published
1.1.1.0 source (b905c6b). The supplied Firebase application remains unchanged in
`:app`. The old app retains io.github.haymayia.photon, permanent signer, existing
preferences/session/download data and real Google account integration. No Firebase
setup is required for this original-app update. Version code 57.

- Native player Next now delegates to Photon's queue instead of a disabled
  single-item ExoPlayer timeline. Outside playlists it opens the first related
  recommendation; inside playlists it keeps order and paginates, never falling
  through to unrelated recommendations. No successor means Next stays disabled.
- Add default-on **Turn on music override** in Playback & downloads. Description
  explains speaker/headset recovery and that other apps are not taken over.
- Register Media3's media-button receiver and implement playback resumption.
  Persist local last-item/position independently of Google watch history; refresh
  expired stream URLs on recovery, or use an existing offline download.
- Respect override opt-out and independent phone/TV background settings. Only
  genuine music recovers; regular videos do not gain background privileges.
- Force-stop, Android routing to another app, revoked permissions, network and
  vendor battery restrictions cannot be bypassed. Physical speaker/device
  recovery remains untested; there is no universal auto-launch guarantee.

Validation: Actions run 46113245324 passed debug/release assembly, configured
Photon unit/rendered tests and registered signing-certificate verification.
Command-adapter and music-recovery regression tests passed alongside existing
playlist/history/background tests. Published source ecc675f9e55d92930e0e60e261a3d18f997ef6fa.
GitHub asset 803420436 (`Photon.apk`, 24,551,133 bytes). The separate supplied-app
workflow also passed. No physical-device/speaker claims are made.
