# Native Photon for Windows — 0.1.0 Beta

This is a real WPF/.NET application with an embedded LibVLC player, **not an Android APK conversion, emulator or YouTube website wrapper**. It is an early port, not Android feature parity.

## First release

- Windows x64, per-user installation with no administrator elevation. Self-contained .NET runtime; no separate runtime install or individual Google API setup.
- Native search, public YouTube links, public playlist/channel first-page lists, playback, available combined-MP4 quality choices, 10-second seek, volume, 0.25–2× speed, full screen and user-requested MP4 saving.
- Bundled pinned yt-dlp + Node runtime perform extraction locally; no Photon backend or remote extraction service. No cookies/account tokens supplied to the extractor. Cancel terminates its process tree.
- Account Home, Google sign-in/subscriptions/history/writes, cast, captions, native Shorts browsing, 360/VR, separate-track high-resolution/multichannel playback, playlist pagination and in-app updating are **not ported**. Search is not personalized Home. No fake saved/subscribed state is displayed.
- Only genuinely combined audio/video MP4 sources are offered. Many videos may top out at 360p/720p; restrictions or YouTube changes can prevent playback. Do not interpret this as the Android quality/audio implementation.

Install the official `Photon-Windows-x64-Setup.exe`. The app installs to your local Programs folder and appears in Start. Updates currently require the next official installer. Do not disable Defender/SmartScreen. See SECURITY.md for integrity checks and warning review.

## Source build / verification

.NET 10 SDK, Windows, pinned NuGet LibVLCSharp.WPF 3.10.2 and VideoLAN.LibVLC.Windows 3.0.23. Run `dotnet run --project desktop/windows/Photon.Tests -c Release`, then publish the WPF project for win-x64. The Windows workflow downloads the pinned official yt-dlp 2026.10.26 binary, bundles Node 24.22.2, records SHA256 checks and builds an Inno Setup installer. Tools are included under `tools`; do not fetch executables automatically at app startup.

Windows CI validates core input/parser policies and native initialization/XAML construction. It does **not** prove real YouTube playback, hardware decoding, live searches or every Windows device's antivirus verdict. The release is unsigned unless separately stated; SHA256 is integrity evidence, not Authenticode publisher trust or proof of harmlessness.
