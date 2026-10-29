# Photon acceptance checklist

A successful compile is necessary, but not sufficient. Do not mark these as
passing without running them. Baseline: Android 8+; test Android 13+ notifications
and current Android foreground-service restrictions as well.

- [ ] Install APK beside Prism; separate Photon icon, name and app data.
- [ ] Launch; no account or invasive microphone/camera/storage permissions.
- [ ] Search a normal phrase; play a non-music video with audible sound.
- [ ] Paste watch, youtu.be and Shorts links; share a YouTube link to Photon.
- [ ] Load another results page; no duplicate key crash.
- [ ] Rotate while browsing/searching/playing; no reset or unintended playback.
- [ ] Switch 360p/720p/1080p where available; position and pause state survive.
- [ ] Seek, pause, background/resume, lock-screen controls, headset unplug.
- [ ] Minimize/expand player without resetting playback.
- [ ] Enter/exit landscape and PiP; correct surface, audio and back behavior.
- [ ] Open another video; previous audio must stop even if resolution fails.
- [ ] Save/unsave; restart app; saved/history/settings persist.
- [ ] Download combined MP4 and adaptive 1080p; progress and cancel work.
- [ ] Offline/airplane mode: play downloaded video WITH sound, seek to end.
- [ ] Unmetered-only download waits on metered data; disabling allows cellular.
- [ ] Cancel mid-download; no visible incomplete files; repeat download.
- [ ] Network error/restricted/unavailable video: useful failure, no crash.
- [ ] Low storage: clear error, no completed-library entry for partial output.
- [ ] Force stop during download, restart; no corrupt completed entry.
- [ ] Delete a download, confirm removed; decline deletion, file stays.
- [ ] Clear history; saved videos/downloads remain.
- [ ] Dark/light screens and controls have legible contrast and TalkBack labels.

## Still unverified

All device checks above are initially unverified. Local Android compilation cannot
run in the coding sandbox (no JDK/SDK; Maven/Google/Gradle hosts restricted).
GitHub Actions is the external build check; see its actual run result. A failed
run is not an APK delivery.
