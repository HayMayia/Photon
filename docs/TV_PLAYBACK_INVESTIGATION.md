# TV playback investigation after the 1.1.1.6 report

## Confirmed before the new adapter
Real Android API 30 emulator, bundled local AVC/AAC sample, PhotonPlaybackService,
MediaController, production TV video view inside Compose. No YouTube/network
extraction was involved.

Run 46174734538 (source 8092943) failed first-frame verification. Controller:
`state=1 ready=false playing=false position=0 tracks=0 surface=true`.
The service diagnostics at the same time recorded `Playback state=2`, then
`Playback state=3`, then `Playing=true`.

This is not evidence that YouTube itself was slow or that a decoder error was
confirmed. It establishes a broken service/controller state/event path while the
output texture was available. A view-inflation test had not caught it.

## Correction
The queue adapter's Kotlin `Player.Listener by listener` wrapper is removed.
`ForwardingSimpleBasePlayer` now owns coherent state and listener events. An outer
`ForwardingPlayer` intercepts all four Next/Previous commands without losing the
source-bound transport behavior. Queue command invalidation uses Media3's state
invalidation, not hand-emitted command-only listener batches.

The new callback test verifies that timeline and play-intent events actually reach
listeners; the real-render test requires a first-frame callback AND nonblack
TextureView pixel data, then tests remote Back before a native view hierarchy.
Passing this does not claim all TV codecs/manufacturers are tested.

## Other scoped changes
- Activity-level remote Back dispatch pauses/closes the player in one press, before
  a native child can consume it. No shelf-hiding/fullscreen-toggle loop on this path.
- Canceled selections cancel their synchronous NewPipe HTTP calls instead of
  merely discarding their results. Lookup has a 30-second total extraction limit
  with an explicit retry error, not an indefinite spinner. No measured public
  YouTube network speed improvement is claimed.
- New adaptive/monochrome icon, separate background and matching TV launcher banner.
  The TV sidebar displays the installed version so future screenshots identify
  the build actually in use.
- The debug render activity and local video fixture are test-only; not shipped
  in the release APK. No new account backend or unrelated phone redesign.

## Release gate
Do not publish while real-frame verification is failing. Record the successful
run and final versioned build results before describing the corrected pipeline as
verified. Physical TV behavior and full app-end-to-end navigation remain separate
validation requirements.

## Corrected pipeline evidence
The identical real-frame test **passed** in run 46175212800, source c17d4f4,
after the adapter replacement: first-frame callback, nonblack texture pixels
under Compose, and Activity-level remote Back dispatch. The earlier run
46174734538 failed on the same local fixture with service playing/controller idle.
This is a failing-before/passing-after emulator result, not physical-TV validation.
