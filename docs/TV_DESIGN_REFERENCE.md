# Original Photon TV design

## Reference and scope
The user's seven TV photos are the layout authority, not assets to redistribute.
Landscape playback: title/channel upper left, Settings upper right (replacing
Quality and Audio/Subtitles), blue timeline below, Up next and Chapters/Playlist
beneath, expandable horizontal card shelf over dimmed playback. No fake episode
metadata, streaming-service branding or copied artwork is included.
Portrait Shorts: centered full-height portrait video, separate right-side title,
creator and actions. Phone playback and phone Shorts remain unchanged.

## Research
Google Android TV focus-system guidance:
https://developer.android.com/design/ui/tv/guides/styles/focus-system
Focus must be visibly distinct from selection; D-pad moves, center selects,
Back returns. Apply consistent focus borders and modest scale to poster cards.
The official guidance informed focus treatment, not a replacement for the user's
player layout. Home uses image-led spotlight, left navigation and categorized
horizontal shelves with readable titles and generous spacing. No auto-play on focus.

## Data and interaction contract
- Up next is the current video's actual related videos, not the active playlist.
- Playlist shows the active queue in its original order, with continuation/error
  handling. Only selecting a playlist card prepares a queued selection.
- Without a playlist, Chapters appears for explicit ordered creator timestamps
  beginning at 0:00 in the description. This is timestamp parsing, not a claim of
  complete extractor-native automatic-chapter support. Hide when unavailable.
- Speed: navigating past does not mutate speed. OK enters draft adjustment;
  Left/Right adjust 0.1–10×; OK applies; Back cancels; Up/Down cancel and navigate.
- Timeline also requires OK before Left/Right seeks, with 10-second draft steps.
- Player controls hide after six seconds while playing, never while a shelf is open.
  First key reveals controls; Back closes shelf, hides controls, then exits player.
- Shorts Up/Down selects previous/next; OK on portrait toggles playback.

## Validation
Hosted build and regression checks are required. A passing hosted test does not
mean the photos were pixel-matched or a physical TV remote was tested. Validate on
real 720p/1080p/4K TVs for safe area, focus visibility and every D-pad/Back path.

## Regression correction after 1.1.1.5 device report
The published build was reported to trap Shorts, advance outside Subscriptions,
and display controls without a picture. Hosted tests did not establish device
playback correctness. Corrections route selected TV Shorts to one player, freeze
Subscriptions queue membership, stop automatic advance for Shorts/subscription
feeds, and gate related transport by the recommendation preference. The same
stateless Shorts frame is exercised by a remote Back/action-navigation test.
TV flat video now explicitly uses TextureView rather than the default SurfaceView
under Compose overlays; inflation is tested, but first-frame rendering on the
reporting TV remains unverified. No pixel-perfect or physical-device claim.
