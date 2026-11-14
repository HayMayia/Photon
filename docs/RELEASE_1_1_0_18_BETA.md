# Photon 1.1.0.18 Beta

- Replace the oversized promotional Home hero with a compact accent-tinted search panel and an independent title/settings toolbar. Use only selected theme colours, not fixed blue or contrasting tertiary washes in the hero.
- Show all nine collection shortcuts in a three-column grid instead of hiding Liked Music and Downloads beyond a sideways scroller. Keep Google recommendation order unchanged.
- Account shelf filters wrap into two rows, with larger touch/remote targets. Account errors have a readable themed status card rather than a bare wall of red text. Home source notices get a matching card; Google's paused-history notice remains truthful and pause is respected.
- Liked Songs bootstraps the actual LM page using the same pinned Google session. Do not reject a Music bootstrap just because it lacks the optional WEB LOGGED_IN flag; the authenticated private VLLM response remains authoritative and rejected responses are still rejected.
- Missing/private/deleted song rows no longer abort all readable songs. Count skipped rows, publish readable songs, and report the skipped count. Parse Music endpoint-action continuations and playback-overlay IDs as well as existing shapes.
- Distinguish timeouts, missing Music context and missing session credentials in safe retry text; only say cached songs are retained when actual songs exist. No second login, guessed music, or account mutation is introduced.

Hosted validation pending. Physical phone/TV UI, live account responses and all network/authentication failure modes have not been verified. These repairs target reproduced protocol/control-flow failures; no blanket claim that every YouTube error is eliminated.
