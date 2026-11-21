# Windows source-channel test — 0.2.0 Beta

Use **5.1 test** or **7.1 test** before trying a YouTube source. Each speaker has a
1.5-second low-volume tone followed by half a second of silence. The label names
that source channel, not a measured output speaker. Start with a low receiver volume.

- 5.1: front left, front right, center, LFE (80 Hz), back left, back right.
- 7.1: front left, front right, center, LFE (80 Hz), back left, back right,
  side left, side right.

These are actual 48 kHz multichannel PCM sources, not stereo upmixes. Open
**Windows Sound**, choose the actual HDMI/USB output, and configure its supported
speaker layout. Stereo-only Bluetooth/headphone outputs cannot reproduce discrete
surround. Receiver bass management can intentionally redistribute low frequencies.

For YouTube, Photon chooses an explicitly original, non-dubbed soundtrack first,
then prefers the most source channels within that language preference. Unknown
language/channel metadata is labeled unverified. Another track can be explicitly
selected. If YouTube only supplies stereo, this app does not invent more channels.
There is no app-level 6/8-channel cap; codec, decoder and output support still apply.
No claim of Dolby Atmos/object audio passthrough is made.

HD playback attaches the selected real audio to the separate video track in
LibVLC. MKV downloads copy the selected video and audio codecs without transcoding,
channel conversion or upmix. The audio and caption selectors restart playback at
the previous position. Captions require an available source VTT track.

The build checks parsing/selection, actual generated source channel counts, and a
six-channel lossless remux. It does NOT certify live YouTube compatibility,
receiver negotiation, every codec, or physical speaker output. If a source refuses
to open, do not interpret the displayed source metadata as proof that it played.

## Remaining port gaps

Google-account sign-in/sync, personalized account feeds and mutations, casting,
headset VR and complete Android/iOS parity are not included. iOS remains the
previous unsigned development foundation; this Windows update does not alter it.
