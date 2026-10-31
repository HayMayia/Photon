# v1.0.1 Beta

- Account-library Shorts now use two columns, with up to two Shorts per row. Portrait artwork stays inside each cell and titles wrap beneath it, with three-line ellipsis rather than stretching across the screen. Ordinary videos, headers, channels and playlist collections stay full width; mixed Google ordering is preserved. Late authoritative Shorts metadata updates the column span. TV account thumbnails have a height cap.
- Removed the permanent version card and Check for updates button. Automatic checks are quiet unless a newer release is known. The update-only banner uses Prism's glass/spectrum treatment and reads “Update to v[version] Beta,” with tap-to-update and dismiss. Permission, download and installer status appear only inside an actual update offer. Returning from install-source permission resumes the user-requested install when allowed.
- Existing app identity, permanent signing key, downloads and account settings are preserved. TV foreground-only playback and mobile background playback are unchanged.

Install this through the v1.0.0 Beta Home updater. Android still requires installation approval. The updater downloads an APK internally, as all direct-distribution Android updates do; there is no separate manual APK/ZIP step for an installed updater-enabled version. Older 0.13 installations without the updater still need their one-time migration.

This is a beta. Build/unit checks do not certify phone rendering, TV focus, real Google content layouts or end-to-end native installation on a physical device.


## Build verification

Source `ad24a63dc8b54902c6a5e7e71c4772df3496edb1`, version code 18. Build 41219254742 passed in 9m23s, including debug/release assembly, focused Photon tests (179 test methods in source), permanent certificate checks and public Beta publication. Native device rendering and end-to-end installation remain unverified. Same package/key; preserve data by approving an in-place update, not uninstalling.
