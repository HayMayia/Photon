# v1.0.5 Beta — paired Shorts without scrolling reflow

The previous stability fix reserved full-width frames for unknown videos, so later-confirmed Shorts stayed stacked. This release resolves the actual format before committing library cards to the grid. Consecutive Shorts now use paired portrait cells; normal videos and collections remain full-width and titles wrap underneath.

New pages arrive in small, ordered, fully classified batches. Existing rows retain their committed geometry, canonical identities and order; overlapping pages remain deduplicated. Unknown/unavailable formats are not silently admitted as landscape: already loaded cards remain visible and an explicit retry is offered. Loading may take longer while format information is retrieved. A Short separated from another by a normal video stays alone to preserve Google's order.

Direct Google actions, tap-to-show Shorts playback overlays, wrapped card titles, header bounds and permanent signing remain unchanged. Regression tests cover initially unclassified Shorts, out-of-order metadata completion, unavailable-format failure and stable geometry. Physical/live-account validation remains pending.

After publication use **Home → Update to v1.0.5 Beta**. If cached, force-stop/reopen and visit Home. No uninstall, storage clearing or manual APK delivery.
