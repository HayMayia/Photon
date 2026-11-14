# Photon 1.1.0.16 Beta

- Visibly redesigned Prism Home: large colour-gradient hero/search, two-row accent shortcut tiles, rounded inset recommendation cards and richer page gradients. Actual Google recommendation order and two-column portrait Shorts remain intact; no sample content replaces account data. YouTube layout remains separate.
- Keep stable positive/negative music classifications for 30 days instead of expiring them with every six-hour account reconciliation. Account revision boundaries and conservative source evidence remain enforced. This avoids repeatedly fetching watch metadata for all likes.
- Full Liked Music scans paginate source-proven/cached decisions before checking unknown watch metadata, so older confirmed songs are no longer blocked behind each page's unknown videos. Unknown checks remain bounded at four concurrent requests, with persistent progress.
- Freshly discovered songs appear before retained older cache while a scan is incomplete, rather than waiting behind it until every restricted row resolves.
- Recent-like refresh now follows up to 20 pages until reaching an already-classified page, rather than checking only the first page. Known rows are rechecked against current proof decisions and rejected songs are filtered from the resulting snapshot.

Hosted validation pending. Initial unknown metadata and restricted videos can still be slow/unresolved. Music classification uses source metadata, not audio recognition; a source's incorrect Music category is not independently detectable. No live private-account or physical-device validation has been performed.
