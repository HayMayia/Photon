# Photon for Windows 0.1.0 Beta

A **native Windows x64 preview**, not the Android app converted or a website wrapper. Download `Photon-Windows-x64-Setup.exe`; self-contained, per-user installation, no admin required.

Native public search/link browsing, first-page playlists/channels, embedded LibVLC playback, combined MP4 quality, speed/volume/10-second seeking/full screen and user-selected MP4 saving. Pinned local yt-dlp + Node extraction; no Photon server or cookie import.

**Not Android parity:** Google account connection/personalized Home, private content, account mutations/history/sync, cast, subtitles, native Shorts browsing, 360/VR, separate-track HD/multichannel, pagination and updater are not ported. Many combined streams are 360p/720p; some content may not play. Missing features are not simulated.

**Unsigned preview:** Windows may show SmartScreen/unknown-publisher warnings. SHA256SUMS accompanies the installer. Do not disable antivirus or install a custom trust certificate. Security/false-positive review and a conditional free OSS signing route are documented in `desktop/windows/SECURITY.md`; neither guarantees warning-free distribution.

CI builds and startup/core checks are not real-device/live-YouTube playback certification. Android releases and their updater are unchanged.
