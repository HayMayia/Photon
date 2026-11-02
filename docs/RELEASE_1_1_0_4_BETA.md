# v1.1.0.4 Beta

- **Liked Music** in Library and Prism Home shortcuts. Uses a real playlist
  on the signed-in Google account (new playlists are private), not a local substitute. Searches returned account
  playlist pages for an existing exact-name playlist before creating one; lookup
  errors or incomplete pagination never trigger blind creation.
- **Save → Save to Liked Music** creates/finds that playlist and adds the selected
  video through the existing Google mutation/read-back path. It does not import
  Prism's private cache, automatically relabel YouTube likes, or pretend to be the
  separate YouTube Music built-in liked collection.
- **Music in background: on by default. Regular videos in background: off by default.**
  Independent persistent settings let you enable/disable either class.
- Music classification comes from YouTube's returned Music category, not titles,
  channel names, aspect ratio, source channel count or playlist membership. Unknown
  classification follows the regular-video setting. Older offline downloads without
  category proof therefore default to regular-video behavior.
- Background rules apply to app invisibility/screen off, remote/session play requests,
  media transitions and settings changes. Visible PiP/configuration changes do not
  count as leaving the visible app. Enabling a switch does not automatically resume.
- TV retains foreground-only playback; mobile background switches do not override it.

Permanent Android signing identity retained. Windows/iOS and the separate private
music preview are unchanged. Automated checks do not certify live Google writes or
physical-device lockscreen/PiP behavior; no such testing is claimed here.


## Published verification

[Run 41447282769](https://github.com/HayMayia/Photon/actions/runs/41447282769)
PASS in 6m17s: debug/release builds, focused unit suite including nine new tests,
permanent signing identity checks and publication. Source `31bb450`, code 33.
Live account changes and physical phone lifecycle behavior remain unverified.
