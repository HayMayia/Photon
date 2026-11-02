# Photon for Windows 0.1.0 Beta

A **native Windows x64 preview**, not the Android app converted or a website wrapper. Download `Photon-Windows-x64-Setup.exe`; self-contained, per-user installation, no admin required.

Native public search/link browsing, first-page playlists/channels, embedded LibVLC playback, combined MP4 quality, speed/volume/10-second seeking/full screen and user-selected MP4 saving. Pinned local yt-dlp + Node extraction; no Photon server or cookie import.

**Not Android parity:** Google account connection/personalized Home, private content, account mutations/history/sync, cast, subtitles, native Shorts browsing, 360/VR, separate-track HD/multichannel, pagination and updater are not ported. Many combined streams are 360p/720p; some content may not play. Missing features are not simulated.

**Unsigned preview:** Windows may show SmartScreen/unknown-publisher warnings. SHA256SUMS accompanies the installer. Do not disable antivirus or install a custom trust certificate. Security/false-positive review and a conditional free OSS signing route are documented in `desktop/windows/SECURITY.md`; neither guarantees warning-free distribution.

CI builds and startup/core checks are not real-device/live-YouTube playback certification. Android releases and their updater are unchanged.

## Verification

Source `a9ca38a1888b1d9e3c45d77a276e28062ac2227e`; [Windows build 41431338162](https://github.com/HayMayia/Photon/actions/runs/41431338162) PASS in 3m55s, 25 core checks and native initialization. Separate [published-installer verification 41431997310](https://github.com/HayMayia/Photon/actions/runs/41431997310) PASS in 48s: fixed digest, per-user installation, installed native startup and uninstallation. This is not a live-playback, virus-scan or SmartScreen approval.

Installer: 147,906,611 bytes. SHA256:
```
15ee9b2c359ee4cde35b1c53689f7ffb7eab201fae0ab1857f7b410e160d44aa
```
