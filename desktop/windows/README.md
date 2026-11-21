# Native Photon for Windows — 0.2.0 Beta

A native WPF/.NET application with embedded LibVLC, not a website wrapper,
Android conversion or emulator. This is still an incomplete native port.

## Available in 0.2

- Windows x64, per-user self-contained installation, no administrator elevation.
- Native public search, video/channel/playlist links and paginated public lists.
- Separate video/audio HD playback, quality and source soundtrack selection,
  source VTT captions, seek, volume, 0.25–2× speed and full screen.
- Original non-dubbed audio preferred before channel count. Source channel count
  is displayed, never presented as measured physical output. No app stereo cap,
  fake surround/upmix or promised Atmos/object passthrough.
- Real 5.1 and 7.1 PCM speaker-order test sources; Windows Sound shortcut.
- MKV saving copies selected source video/audio codecs without downmixing.
- Optional Windows-only update banner links to a newer official installer release;
  no manual “check” prompt, automatic executable replacement or Android release mixup.
- Pinned local yt-dlp, Node and shared-LGPL FFmpeg; no extraction backend,
  account cookies/tokens, remote plugins or automatic tool downloads at startup.

Google-account login/sync, subscriptions/history/account mutations, personalized
Home, casting, headset VR and full Android/iOS parity remain **unported**. These
are not secretly implemented with local substitute state. No Google state is written.

Install the official `Photon-Windows-x64-Setup.exe`; see SURROUND_TESTING.md for
channel order. Select a genuine multichannel Windows output. Stereo-only devices
cannot reproduce discrete surround, and stereo source tracks are not upmixed.
The app starts at an available source resolution up to 1080p; higher resolutions
remain selectable. Availability/decoder performance depend on the source/device.

## Source build / verification

.NET 10 SDK, Windows, pinned LibVLCSharp.WPF 3.10.2 / LibVLC.Windows 3.0.23.
Run `dotnet run --project desktop/windows/Photon.Tests -c Release` and use the
Windows workflow to build/package the complete app and tools. FFmpeg is pinned
by immutable upstream release and SHA256; licensing and build/source references
are included. Generated audio test assets and binaries are CI artifacts, not Git data.

CI checks input/parser policies, original-language/channel selection, real 6/8-channel
PCM source counts, six-channel stream-copy remux, native XAML/runtime initialization
and installer production. Independent installed-release verification checks the exact
published EXE. This does **not** certify live YouTube compatibility, physical speakers,
all codecs/decoders, antivirus verdicts or Windows publisher reputation.

The installer is unsigned. Do not disable Defender/SmartScreen. See SECURITY.md.
Checksums verify integrity, not universal trust. Use downloads only where you have
permission and comply with content rights/platform terms.

## Verified 0.2 publication

Source `2fb7c352201def062f34a691da1b3a22a68bf914`,
[build 41435801177](https://github.com/HayMayia/Photon/actions/runs/41435801177)
PASS: 40 core checks, pinned tools, real six/eight-channel PCM fixtures,
six-channel stream-copy remux, WPF/native startup, installer and publication.
Installer: 204,474,136 bytes, SHA256
`c92dda12819bac8159043cea380cdab1a5585f0239aec65a46606f1aa8a12fe8`.

[Independent verification 41436530109](https://github.com/HayMayia/Photon/actions/runs/41436530109)
PASS in 54s against that exact published installer: fixed digest, per-user install,
installed native/tool startup, installed LibVLC parsing of both 6/8-channel WAVs,
actual separate video/audio playback with an active audio track through dummy
outputs, and uninstall. This tests native track attachment/playback, not physical
speaker output or live YouTube URLs. The isolated test host exits before uninstall
so its loaded DLLs are released.

## Verified first publication

Source `a9ca38a1888b1d9e3c45d77a276e28062ac2227e`, [run 41431338162](https://github.com/HayMayia/Photon/actions/runs/41431338162) PASS in 3m55s: 25 core checks, WPF/native runtime initialization, pinned extractor verification, self-contained publication and per-user installer. Prerelease `windows-v0.1.0-beta` targets that exact source. Installer is 147,906,611 bytes, SHA256 `15ee9b2c359ee4cde35b1c53689f7ffb7eab201fae0ab1857f7b410e160d44aa`.

Independent workflow [41431997310](https://github.com/HayMayia/Photon/actions/runs/41431997310) PASS in 48s: downloaded the **published EXE**, checked its fixed digest, installed it per-user, ran installed app/tool/native initialization, and uninstalled successfully. It did not test live extraction/playback, real hardware or certify absence of malware. Initial build attempts exposed/fixed an MSBuild argument issue and missing WPF IO imports before publication; no broken Windows release was published.

Use downloads only where you have permission and comply with applicable platform terms/content rights. This project does not grant rights in third-party videos.
