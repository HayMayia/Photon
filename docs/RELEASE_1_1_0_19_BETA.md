# Photon 1.1.0.19 Beta

- Simplify Home with a pill-shaped search bar instead of another slogan/banner. Place Liked Music and Liked videos first, highlight them using the selected accent and reduce shortcut height to bring the real feed higher. All nine shortcuts remain visible, with no horizontal scrolling.
- Accept bounded same-host HTTPS redirects for bootstrap GETs, preserving the selected authuser and host-specific cookies. Previously every redirect was treated as an opaque loading failure. Cross-host redirects are rejected before sending credentials, and POST/mutation requests are never replayed.
- Read native Liked Songs embedded in the LM bootstrap when available instead of throwing away a usable private list and always issuing another browse request. Only the native shelf is accepted; recommendations still cannot become liked songs.
- Preserve safe HTTP status details for Music failures (authentication refusal, rate limit, server failure), and distinguish account-confirmation redirects and unsupported shelves/continuations. No cookies, URLs, account IDs or response bodies are displayed. The previous generic catch concealed every one of these different failures.

Hosted validation pending. The screenshot does not expose the underlying HTTP response; these are reproducible request-handling repairs, not a verified diagnosis of the user's private account. Live-account and physical-device testing remain unperformed. No new login/session or inferred music fallback is added.
