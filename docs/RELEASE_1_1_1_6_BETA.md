# Original Photon 1.1.1.6 Beta — TV regression corrections

Version code 61; install over original Photon without uninstalling/clearing data.
Same package and permanent signer. Separate supplied app unchanged.

The previous TV beta was reported to trap Shorts, switch outside Subscriptions,
and show controls without a video picture. This release addresses those code
paths; no physical-TV verification or confirmed decoder/first-frame fix claimed.

- TV card selection now routes a Short to the portrait Shorts player, instead of
  unconditionally sending every TV video to the landscape cinema player.
- Portrait video itself is centered, with muted gray background and a compact
  lower-right title/creator/icon-action rail following the supplied reference.
  No fabricated related-title, avatar or action is added.
- Remote Back and visible Back leave Shorts; exit pauses playback, clears the
  presentation and invalidates pending service navigation to prevent reopening.
- Up/Down changes Shorts only while the portrait video has focus. Navigating
  action buttons no longer changes the video. A missing current item does not
  default to another video's list index. Composing the screen never starts a feed.
- Subscriptions selections freeze that feed's actual order as a bounded transport
  source; no related-video fallback. Shorts and Subscriptions no longer auto-advance
  at end. Explicit Next remains bounded by the selected source.
- Related transport obeys the existing recommendation preference, including in the
  background service. Persist that policy with the queue; hide recommendation
  shelf in Subscriptions or when recommendations are off.
- Use explicit TextureView output for flat TV playback under Compose overlays,
  rather than default SurfaceView. Focus settings are applied after PlayerView's
  constructor resets XML attributes. This changes the output path but is NOT a
  verified physical-TV black-picture fix.
- No further Home/phone redesign is mixed into these regression corrections.

## Validation
Pending final versioned CI. New tests exercise production Shorts frame remote Back,
action navigation versus video stepping, bounded source/Shorts policies,
recommendation-policy persistence, actual TV video-view inflation and runtime focus
configuration. These tests cannot prove hardware decoding or first-frame rendering.

Test on TV: open a Short from Subscriptions, move among action controls, press Back;
confirm return to Subscriptions without a new video. Reopen and wait to end: no
unrelated autoplay. Then check a normal video's actual picture as well as audio.
If black persists, report TV model/Android version and whether the same video works
on the phone; the device-specific surface/decoder failure still needs diagnosis.
