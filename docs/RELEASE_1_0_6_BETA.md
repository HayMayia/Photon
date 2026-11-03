# v1.0.6 Beta — superseded source notes

This source was superseded before successful publication. Use v1.0.7 Beta; do not rerun the obsolete v1.0.6 publisher.

## Subscriptions navigation, channels and account state

Library now has a Subscriptions shortcut. The subscription feed includes a horizontal subscribed-channel/avatar strip with channel navigation and explicit channel pagination, on mobile and TV. Channels come from the connected Google account, not a local substitute. Feed video pagination/YouTube pixel parity and a grouped Shorts shelf are not completed by this update.

Channel pages use resolved canonical channel IDs instead of handles for subscription actions. Existing authenticated channel-directory entries seed Subscribed state. Added modern channel-bound subscription entity parsing; the two subscribe/unsubscribe button variants alone never establish current state. If channel controls are unavailable, authenticated directory reads can establish membership; fresh read-back still guards writes. Unknown state is not silently reported as subscribed. Subscribed buttons act directly to unsubscribe, with no added warning.

Prism is the separately installed upstream app, not code packaged inside Photon. Its current upstream code keeps a library/offline mirror, loads playlist pages on opening, and can fall back to a saved copy on network failure. It is not a live push stream of playlist edits from YouTube Music. Photon cannot patch an already-installed Prism app or its private cache. Use the same Google/YouTube Music channel in Prism, Library's Sync action, then leave and reopen the playlist online. A local/imported/downloaded copy is not a live account playlist; do not uninstall or clear storage. This release does not claim to fix Prism itself.

Known library pairing/stability, direct Google writes, Shorts player overlays and signing identity remain. Includes modern-entity and authenticated-directory intercepted-HTTP regressions; real-account/device checks remain pending.

After publication: **Home → Update to v1.0.6 Beta**. If a check is cached, force-stop/reopen then Home. No manual APK delivery or data clearing.
