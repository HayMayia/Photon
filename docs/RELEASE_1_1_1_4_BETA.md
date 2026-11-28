# Original Photon 1.1.1.4 Beta — playback notification and platform transport

Only original Photon changes. Version code 59; same package and signing identity.

- Preserve genuine native Liked Music classification when publishing the initial
  playback item; a missing extractor category must not make the service pause a
  verified song when the screen closes. Keep allowed paused music sessions when
  the app task is swiped away, rather than stopping solely because they are paused.
- Configure the playback notification explicitly with a proper status-bar icon,
  and retain controls for paused/restored queues using the same idle-notification
  setting as Prism. Register the service's session immediately on creation.
- Allow Android system media controllers to bind the playback service. Keep the
  Media3 media-button receiver and dispatch actual Next/Previous media key events
  to the same service queue; leave Play/Pause, seeking and volume to Media3.
- Emit both command-change callbacks and their Player.Events batch when queue
  navigation availability changes. This completes the Player listener contract
  used by platform/notification controllers.
- Request notification permission once at first playback on Android 13+, not only
  through the Downloads flow. Android/device settings can still block a channel;
  the app does not override a user's notification or background preferences.
- Add an actual service/notification-manager test without an Activity, and a
  Media3 controller-to-session binder transport regression (not only adapter
  methods). A separate Android instrumentation test covers the framework legacy
  transport used by AVRCP; it is compiled but not executed in the hosted build.
  Robolectric's framework MediaController shadow only returns manually injected
  playback state, so it cannot validate that OS-level bridge.

Validation: [CI run 46122045081](https://github.com/HayMayia/Photon/actions/runs/46122045081)
passed: debug build, Photon unit tests (including actual service notification and
Media3 controller/session transport), Android instrumentation-test compilation,
permanent signing-certificate verification, release build and publication.
The OS-level framework transport instrumentation test was **not executed**.
No physical speaker, Bluetooth firmware or notification-shade validation claimed.

Published source: `25f7032c3466f3be6ed5554ca0a6af1de09ff9d1`.
[Download Photon.apk](https://github.com/HayMayia/Photon/releases/download/v1.1.1.4-beta/Photon.apk)
(24,567,521 bytes; non-draft release and uploaded asset verified).
Install over original Photon without uninstalling/clearing data. Open Liked Music
and start a song once, then test notification controls and speaker Next/Previous
with the phone locked. Allow notifications if prompted; keep background music
allowed. If still failing, collect phone/Android/speaker models and whether audio
continues while the notification is absent.
 Android force-stop/routing limitations remain. No UI redesign,
Firebase changes or unrelated features.
