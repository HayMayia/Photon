# Separate Prism playlist refresh scope

Photon's Music Photon, “Prism” entry opens upstream's separately installed APK. Photon neither bundles that application nor has access to its private database/session/cache. Updating Photon cannot change an installed Prism build.

Inspected upstream `Yigder/Prism` main at `13bd911e4fd2d7195c0a47c1ab179aae23db07a7` on 31 October 2026:

- [LibraryRepository.kt](https://github.com/Yigder/Prism/blob/13bd911e4fd2d7195c0a47c1ab179aae23db07a7/app/src/main/java/com/prism/music/data/LibraryRepository.kt): local mirror restored at launch; sync fetches account library metadata/likes and schedules bounded daily offline prefetch. This is not a push subscription to individual playlist edits.
- [LibraryScreen.kt](https://github.com/Yigder/Prism/blob/13bd911e4fd2d7195c0a47c1ab179aae23db07a7/app/src/main/java/com/prism/music/ui/screens/LibraryScreen.kt): once-per-session loading plus explicit Sync with YouTube Music action.
- [CollectionScreen.kt](https://github.com/Yigder/Prism/blob/13bd911e4fd2d7195c0a47c1ab179aae23db07a7/app/src/main/java/com/prism/music/ui/screens/CollectionScreen.kt): playlist page loads via a remembered load when opened; `orSaved` uses saved copies offline or after load failures. An already-open page is not a live external-change stream.

Practical steps: verify Prism uses the same YouTube Music account/channel, use Library's sync icon, then leave/reopen the playlist while online. Local/imported copies and offline snapshots are not live playlist views. Do not uninstall or clear storage. These are source-backed troubleshooting steps, not confirmation of the cause on the user's device or a claim that Prism was fixed. Further changes to upstream Prism require work in that app's own source/release, not a Photon-only update.
