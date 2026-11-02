# Photon native iOS foundation — not an installable release

SwiftUI + AVPlayer, not a website wrapper. First source milestone: validate a public YouTube video URL, use pinned YouTubeKit local extraction, play a combined stream with native Apple controls, cancel stale lookups and report unsupported content honestly. It makes no Google account connection or history writes and has **no remote extractor fallback**.

This is much earlier than Android/Windows: no search/playlist browser, Google login/sync/mutations, downloads, casting, separate-track HD/multichannel, native Shorts, 360/VR, subtitles management, updater or feature parity. No guarantee that current YouTube extraction works in every region or on a real iPhone. Source compilation is not live playback certification.

## Build and distribution

- XcodeGen generates `Photon.xcodeproj` from project.yml. Xcode 16+ with iOS 17+ SDK support builds the SwiftUI app. YouTubeKit is pinned to commit `89854c93207ad0d2b3d78d6f1aea24595ca66552` (0.4.10), MIT: https://github.com/alexeichhorn/YouTubeKit . Photon remains GPL-3.0; review redistribution/Apple terms before distribution.
- CI runs host Swift policy tests and compiles an **unsigned iOS archive** with signing disabled. This archive is developer material, **not an installable IPA**, and cannot be installed merely by downloading it on an iPhone. No personal Apple certificate is available in this repository.
- Native installation needs legitimate Apple signing/provisioning. A free Personal Team development route uses Xcode on a Mac and short-lived provisioning; it does not provide unrestricted, durable distribution to everyone.
- TestFlight/App Store distribution requires an eligible Apple Developer Program membership (normally paid; approved organization fee waivers may apply), signing, account-owner authorization and applicable reviews/content rights. See https://developer.apple.com/support/compare-memberships/ and https://developer.apple.com/support/fee-waiver/ . Do not assume App Review approval for a third-party video client.
- With **no payment and no computer**, we cannot provide a universally installable native iPhone app from an unsigned archive. A home-screen web app would be a different product and is not silently substituted for this native port.

## Safety checklist

Pin/review dependency source; keep `.local` extraction enforced; do not import browser cookies, enable remote fallback, collect credentials or relax App Transport Security. Review privacy declarations and content-platform rules before release. Use only legitimate Apple signing; never ask users to install unknown enterprise/MDM profiles or trust a custom root certificate. Android Play Protect and Windows SmartScreen reviews do not approve an iOS app.

No camera, microphone, contacts, photo-library or tracking permission is requested. Direct YouTube/CDN connections still disclose network metadata and requested video identifiers to their providers. Playback/network/headset behavior is unverified; unsigned CI builds are not Apple security approval.
