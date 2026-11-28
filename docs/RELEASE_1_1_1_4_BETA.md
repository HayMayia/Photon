# Original Photon 1.1.1.4 Beta — playback notification and platform transport

Only original Photon changes. Version code 59; same package and signing identity.

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

Validation pending. No physical speaker, Bluetooth firmware or notification-shade
validation claimed. Android force-stop/routing limitations remain. No UI redesign,
Firebase changes or unrelated features.
