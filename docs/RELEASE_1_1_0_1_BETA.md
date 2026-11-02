# v1.1.0.1 Beta

- Lower initial playback buffer from 750 ms to 500 ms without reducing rebuffer protection. Warm one real next video's extracted stream metadata only after the current first frame, rather than competing with startup. No speculative playback, media download or viewing-history write.
- Preserve genuine per-stream equirectangular projection and mono/top-bottom/left-right layout from the extractor's existing YouTube player response; no extra classification request and no inference from a title or aspect ratio.
- Source-confirmed equirectangular 360° streams use a spherical perspective viewer with touch panning, not a stretched flat rectangle, in normal and paired-eye modes. Unknown mesh/stereo projections are explicitly unsupported rather than falsely rendered as ordinary video. Existing saved files without projection metadata cannot be retroactively classified offline.
- Rework viewer surface ownership across controller replacement, GL recreation, pause/resume and exit. Apply the SurfaceTexture transform once instead of an additional vertical flip. Use landscape while the headset dialog is open, restore the prior orientation, and account for odd viewport widths. Preserve source projection in newly saved downloads.

This is not a promise of instant network/decoder startup or an error-free app. Touch-pan 360° and paired-eye rendering are not lens-distortion calibration, positional/gyroscope head tracking or invented 3D for ordinary 2D videos. Hosted builds/tests cannot certify actual GPU/headset appearance, eye ordering on a physical display or phone latency. Device verification remains pending.

Update in place through Home, with the same package and permanent signing identity. v1.1.0.0 is unchanged; its additional fixes are documented in its own release notes.
