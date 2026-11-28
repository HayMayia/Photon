# Original Photon 1.1.1.5 Beta — TV cinema layout and remote controls

Version code 60. Install over original Photon; do not uninstall or clear data.
Same original app package and permanent signing identity. Phone player layout,
background music defaults, account data, and the separate supplied app are unchanged.

## TV changes
- Photo-referenced fullscreen cinema layout: title/creator upper left, a single
  Settings button upper right, slim cyan seek timeline, Up next and Chapters or
  Playlist buttons. No duplicated top-level Quality/Audio buttons.
- Expanding a shelf dims the video and moves tabs above horizontal poster cards.
  Up next contains actual related videos. Playlist contains the original ordered
  queue with load-more/error handling, never recommendations substituted for it.
- Chapters seeks explicit creator timestamps parsed from video descriptions when
  ordered and starting at 0:00. Hidden when unavailable; this does not yet claim
  complete support for YouTube's separate automatic chapter metadata.
- Portrait TV Shorts: full-height portrait video with a separate right-side title,
  creator, Like, Comments and Settings rail. Up/Down moves through Shorts; OK on
  video toggles play/pause. Phone Shorts stays unchanged.
- TV home gets an artwork spotlight and categorized horizontal video/music/Shorts
  shelves, stable card sizes, overscan-conscious padding and visible focus borders.
- Speed slider no longer captures navigation just because it is focused. OK enters
  adjustment, Left/Right changes the draft, OK applies, Back cancels, Up/Down cancels
  and moves to another option. Timeline seeking uses the same OK-to-adjust idea.
- Controls auto-hide during playback; Back closes shelves, hides controls, then
  returns to browsing. Native video surface no longer competes for Compose focus.

## Validation and limits
Pre-release CI [46168242134](https://github.com/HayMayia/Photon/actions/runs/46168242134)
passed the build, original Photon tests, device-test compilation and permanent
signing verification. Includes remote speed navigation/application regression,
chapter parsing, existing card focus and preview tests, and playback tests.
Final versioned release verification pending.

No physical TV, remote, screenshot pixel-match or Bluetooth hardware test claimed.
The photos guide layout; the copyrighted reference artwork/branding is not bundled.
Design rationale and research: [TV_DESIGN_REFERENCE.md](TV_DESIGN_REFERENCE.md).
