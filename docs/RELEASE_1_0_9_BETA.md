# v1.0.9 Beta — actual full videos, actual Shorts

Removes v1.0.8's forced portrait two-column fallback for unknown videos. Full and unknown-format videos use full-width landscape cards; only positive Shorts evidence gives a paired portrait card. No duration, thumbnail appearance or metadata failure is treated as proof of Shorts.

Library data remains independent of optional format HTTP. Returned rows appear without waiting for classification. As visible videos resolve, actual Shorts can be promoted once scrolling is idle, rather than being permanently stuck in the initial frame or repacking during a gesture. Newly identified Shorts can therefore change layout once at rest. Dataset-retained positive proof prevents cache eviction/remounts from turning them back into normal cards; canonical identity/order/deduplication remain.

Unknown Shorts can initially look like normal cards until YouTube supplies usable evidence. This is deliberately preferable to making full videos look like Shorts or blanking the library. Refresh and playback still use actual available metadata. No account data is fabricated or deleted.

Regression coverage includes unavailable metadata preserving normal frames, a mixed trailer/two-Shorts sequence committing spans [2,1,1], retained proof under stale metadata/remount and the existing UI/scroll/header tests. Physical/live-account verification remains pending.

After publication use **Home → Update to v1.0.9 Beta**. If cached, force-stop/reopen then Home. No uninstall, storage clearing or manual APK delivery.


## Verified publication

App source `a1d0d8389a5ba962f330ae7d5f438e3169b96680`, code 26. [Release run 41226639238](https://github.com/HayMayia/Photon/actions/runs/41226639238) passed in 8m14s: debug/release assembly, 195-method focused suite, permanent certificates and publication. Tag targets that source; asset 25,303,368 bytes.

An additional test-only commit `42d1d3f` passed [run 41226678098](https://github.com/HayMayia/Photon/actions/runs/41226678098) in 5m15s with 196 methods. Its actual Compose mixed-format regression uses the production idle-format effect and asserts full-width trailer plus side-by-side proven Shorts, preserved through duplicate/remount admission. It changes no app code and does not republish the tag. Physical/live-account verification remains pending.
