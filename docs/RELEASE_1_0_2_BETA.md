# v1.0.2 Beta

Fixes the account-library header regression in v1.0.1. A lazy-grid item positions separately emitted roots at the same origin; the title, shelf chips, Refresh and returned messages were incorrectly emitted as siblings. They now have one full-width vertical layout root, each taking its own measured height. The footer also has one layout root.

Keeps two Shorts per row, wrapped titles, normal full-width items, authentic returned account content and the quiet update-only Prism banner. Removes the duplicate oversized library heading/strapline. Empty/error messages remain truthful and readable, without replacing Google data.

Adds Robolectric Compose layout regressions using the actual header inside a two-column grid, checking non-overlapping bounds at normal and 180% font scale and ensuring paired Shorts start below the header on the same row. Physical phone/TV and native update-install checks remain separate.

Delivered through the existing in-app updater, preserving the package, permanent signing identity and user data. Android installation approval is still required; do not uninstall.
