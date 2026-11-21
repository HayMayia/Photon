## Photon for Windows 0.2.0 Beta

Native Windows player update; not a YouTube website wrapper.

- Separate-track HD playback with source soundtrack selection.
- Prefer original/non-dubbed audio before channel count; retain available source
  multichannel audio without forcing stereo. Unknown metadata stays unverified.
- Built-in real 5.1 and 7.1 PCM speaker-order tests and Windows Sound shortcut.
- Source-preserving MKV downloads with pinned shared-LGPL FFmpeg, no audio downmix.
- Available source captions, paginated public lists, optional Windows release banner.
- Starts at an available resolution no higher than 1080p; higher source resolutions
  remain selectable rather than automatically overloading slower decoders.

Read the included SURROUND_TESTING.md. CI checks source-channel counts and remux;
physical HDMI/receiver output and live YouTube playback have not been certified.
No stereo-to-surround synthesis or Dolby Atmos/object passthrough promise.

**Not full parity:** Google-account login/sync, personalized recommendations/history,
account mutations, casting, headset VR and complete Android/iOS parity remain
unported. iOS remains an unsigned development archive, not an installable IPA.

Installer is per-user x64, self-contained .NET, and unsigned. Windows reputation
warnings are possible. Never disable antivirus or SmartScreen for this app.
Checksums verify the downloaded artifact, not signing or universal trust.


### Verification

[Windows build 41435801177](https://github.com/HayMayia/Photon/actions/runs/41435801177)
PASS, 40 core checks and real multichannel fixtures/remux.
[Exact published installer verification 41436530109](https://github.com/HayMayia/Photon/actions/runs/41436530109)
PASS: install/start, installed LibVLC 6/8-channel parsing and separate-track
playback with dummy outputs, uninstall. No physical speaker/live YouTube certification.
Installer SHA256: `c92dda12819bac8159043cea380cdab1a5585f0239aec65a46606f1aa8a12fe8`.
