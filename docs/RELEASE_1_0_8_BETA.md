# v1.0.8 Beta — account rows must not depend on optional format lookups

Fixes a regression where a failed Shorts/normal-format request discarded the first six-entry batch and left Liked videos blank. Real account rows now load without waiting for any format HTTP request. Cached format proof is used when available; visible cards can resolve metadata afterward without changing geometry. Individual metadata failures cannot hide rows or prevent the remaining page/continuation from loading. Cancellation and account-generation protections still propagate.

When format is unknown, a fixed half-width portrait preview frame keeps adjacent Shorts side-by-side without guessing their actual video type. Landscape artwork fits inside that frame rather than being cropped. This can temporarily give an unknown normal video a narrow frame too; Refresh can adopt subsequently cached normal/Shorts proof. Known normal videos remain full-width. Playback uses actual metadata, not the preview-frame allocation. Existing keys, order and duplicate filtering remain stable.

Regression coverage includes all metadata unavailable, one failed row among healthy rows, overlapping continuations, unchanged source/playback flags, late metadata geometry stability and cancellation. Previous UI/header/scroll/subscription tests remain. Physical/live-account verification is still pending.

After publication use **Home → Update to v1.0.8 Beta**. Force-stop/reopen then Home if an old check is cached. No uninstall or storage clearing; account rows were not deleted from Google by this rendering error.


## Published build record

Source `3ce8d57a4f307599519d41d6072449a21fcb0c39`, code 25. [Build 41225445602](https://github.com/HayMayia/Photon/actions/runs/41225445602) passed in 8m55s: debug/release, 194-method focused suite, permanent signing checks and publication. Tag targets that source; asset 25,303,368 bytes. Physical/live-account checks remain pending.
