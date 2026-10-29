# YouTube account Home (0.6 alpha)

## What is implemented

An optional native YouTube Home screen reads the signed-in YouTube website's returned Home cards, preserving their server order. It does **not** call Photon's on-device recommendation ranker or replace an empty/error result with topic searches. Server-provided empty-feed messages (including a history-off nudge when YouTube supplies one) are displayed as text. Unsupported layouts fail closed rather than being labelled personal recommendations.

The current implementation loads one Home page. Refresh fetches a new response. Continuation pagination, account Shorts/subscription synchronization and a fully faithful rendering of all YouTube shelves/cards are not implemented. Unsupported card types can be omitted; modern lockups may have limited channel/duration metadata. It is a native rendering of supported server cards, not a pixel-identical YouTube website or a guarantee that the account receives the same experiment/layout on every client.

## Two different account connections

- **Sign in with Google:** official Play services authorization for verified channel identity and explicitly confirmed comment posting. This does not expose the personalized Home feed.
- **Connect YouTube Home:** a separate, explicitly disclosed YouTube website session, similar in principle to Prism's session-based feed integration. Open Home or Settings → YouTube account, read the disclosure, sign in/review the account on Google's/YouTube's pages, return to YouTube and choose **Use this YouTube session**. The selected web account index is read from YouTube's own page configuration, not assumed to match the comment channel.

Google can reject sign-in in an embedded WebView. Photon preserves the real/default login browser user-agent, does not bypass that rejection, and does not provide cookie-pasting or password-field inspection. If Google blocks the browser or the website's format changes, native Home may be unavailable. Do not claim this has been device-tested merely because the APK builds. Native comment authorization can still work independently.

## History and recommendation pause — precise limits

- YouTube determines the returned feed using its account settings. If it returns no supported cards or a history-off message, Photon shows that outcome, without filling Home with generated alternatives.
- **Photon cannot read or synchronize Google's history-pause switch through the official API used here.** An empty feed alone is not proof of a paused setting; neither the UI nor parser invents such a flag.
- A separate **Pause local history & resume tracking** switch stops Photon's local history/progress writes and cancels generated local discovery/recommendation requests. It retains previously saved history; clearing old data remains an explicit action.
- Connecting account Home turns the local pause on conservatively by default, regardless of the unknown Google switch. You can explicitly unpause it. This is not advertised as automatic Google-setting synchronization.
- Native playback does **not** upload watch history to YouTube. Watching in Photon will not train the account's YouTube recommendations through a watch-history integration in this version. Website interactions during the web connection screen are handled by YouTube itself and may affect the account in the ordinary way.
- Search, manual discovery, saved items, local subscriptions, Shorts discovery and downloads remain explicit separate features; a user opening one is not an automatic Home fallback.

## Session/privacy safeguards

The embedded website stores session cookies in Android WebView's app-private storage, not in Photon's library/preferences, diagnostic reports, APK, repository or backend. App backup is disabled. These are sensitive authentication credentials even though they are not a password. This is distinct from the native comment OAuth access token, which remains memory-only.

Home HTTP reads temporarily obtain only the YouTube cookie header and send it to the fixed HTTPS `www.youtube.com` Home endpoint with the selected web-account index. Redirects and connection retries are disabled; responses are size-limited. No arbitrary feed URL can be supplied. The parser accepts only valid video IDs and trusted video-thumbnail hosts. There are no JavaScript interfaces or password/cookie-reading scripts on login pages; the only evaluation reads YouTube's non-secret account index after validating the current YouTube origin.

Login top-level navigation is restricted to explicit HTTPS Google/YouTube hosts, file/content access and mixed content are disabled, TLS errors are rejected, and untrusted navigation is blocked. **Clear web session** is available even after a canceled/failed connection and removes this app's WebView cookies/site storage, not Chrome's login, Android's Google account or the native comment authorization grant. It also cancels pending feed loads and clears the native feed.

This is an unofficial website integration. Google production verification, YouTube policy compliance, secure release builds and real-device acceptance are separate prerequisites for public distribution; switching the Cloud project from Testing to Production does not establish them.

## Required device checks

1. Connect a permitted website session, review the chosen account, compare supported cards/order with that account's YouTube Home. Test a non-default web account too.
2. History off / reduced personalization: check actual server response/message, no local/search fallback. Do not infer a flag from an unexplained empty result.
3. Unknown/expired session, consent interstitial, blocked embedded browser, missing WebView provider, network/TLS failure and changed layout: useful failure, no fake recommendations.
4. Pause local history, play/seek/background, restart: no new local position/watch-time/history writes. Unpause explicitly and verify tracking resumes without counting paused elapsed time.
5. Connect Home, clear session, cancel during loading, switch accounts and recreate UI: no stale feed resurrection or cross-account results.
6. Verify existing native comment authorization, subtitles, ±10s gestures, original audio, downloads/casting and saved library remain intact. Upgrade from the stable-signed 0.5.1 APK without uninstalling.
7. Review disclosure, clear-web-session accessibility, TalkBack and narrow-screen layout. No credentials or private session data in the shared device report.

Automated fixtures/protocol tests cannot verify a live Google sign-in, account recommendation response or website DOM behavior on a physical phone.

## Build verification

Source `2852d9b` passed APK assembly, all 71 focused Photon test methods and final permanent-certificate matching in [run 40871610491](https://github.com/HayMayia/Photon/actions/runs/40871610491) (8m22s). [APK ZIP](https://github.com/HayMayia/Photon/actions/runs/40871610491/artifacts/12896153246), 31,105,113 bytes. This establishes build/protocol-fixture correctness, **not** a successful real web-session login/feed or Google history-state synchronization.
