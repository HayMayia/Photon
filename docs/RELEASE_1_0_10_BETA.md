# v1.0.10 Beta — Shorts pairs in Subscriptions

The phone Subscriptions screen still used one LazyColumn item per video, so fixing Library could never pair Shorts there. Replaced that feed with a two-column spanning grid: consecutive confirmed Shorts share a row, full videos occupy both columns, and titles wrap underneath. Channel strip, header, Refresh and All channels occupy full-width header space.

Subscription data retains positive format evidence across remount/global cache eviction. New evidence is staged and committed only when idle using the same format effect as Library. Original Google order, canonical deduplication, normal/unknown full-width behavior and nonblocking account loading remain. A normal video between two Shorts still separates them; order is not rearranged to manufacture a pair. Existing TV adaptive feed is unchanged.

Added an actual production Subscriptions grid/card regression covering full-width normal video, late-proven Shorts on one row with a gutter, and retained format/order under stale source data and duplicates. Physical/live-account checks remain pending. This is not a claim of full YouTube pixel parity or new video-feed pagination.

After publication: **Home → Update to v1.0.10 Beta**. If cached, force-stop/reopen then Home. No uninstall, data clearing or manual APK delivery.
