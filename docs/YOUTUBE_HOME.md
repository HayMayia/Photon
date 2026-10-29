# Account Home, Google history controls and real-play reporting (0.7 alpha)

These are unofficial YouTube website integrations, adapted for ordinary videos rather than Prism's music-only endpoints. They are separate from the supported native Google authorization used for comments. They can fail if Google blocks embedded sign-in or changes website responses. A successful build/fixture test does not establish a working live account integration.

## Connect and use

1. Home or Settings → YouTube account → **Connect YouTube Home**.
2. Read the website-session disclosure, finish Google's/YouTube's own login, review the account, return to YouTube and choose **Use this YouTube session**.
3. Home displays supported YouTube recommendation cards in server order. Scrolling near the end requests the next continuation automatically; **Load more** also supports explicit retries.
4. In account settings use **Check Google history** to read the current watch-history control. **Pause on YouTube / Resume on YouTube** each require confirmation because they change the connected account's setting, not just Photon.
5. To have real native plays reported, explicitly enable **Report real playback to YouTube history** and accept its separate disclosure. Enabling removes the additional local pause, but never overrides a paused/unknown Google setting.

Google may reject embedded WebView sign-in. Photon does not disguise the login browser or ask for pasted cookies. The official comment OAuth token cannot be converted into a YouTube website session. Switching a Cloud project to Production does not remove an embedded-browser rejection or complete Google verification.

## Home continuation behavior

The initial response supplies the authenticated web-client context and continuation token. More pages use a fixed authenticated `youtubei/v1/browse` request, not a search query or local ranker. Supported cards are appended in server order and deduplicated by video ID. In-flight loads are serialized, repeated/consumed tokens stop loops, and the account/session revision is checked before appending. Errors preserve prior cards and require an explicit retry; pages containing no new supported cards stop automatic chaining. Refresh starts a new feed. A 200-page-per-refresh guard bounds memory/network use; this implements practical infinite scrolling, not unbounded storage.

Empty/history-off messages returned by YouTube remain visible. The app does not fabricate an empty state to mean history is paused or replace an empty/error Home with generated alternatives. Some card types/layouts may be unsupported; native rendering is not a pixel-identical copy of all YouTube shelves. A missing or unrecognized client context prevents pagination rather than inventing credentials/version data.

## Reading and changing Google's watch-history pause

Photon reads the signed-in English desktop `/feed/history` page. It inspects **structured watch-history commands in the history sidebar controls**, not English video titles or an empty Home:

- An offered `LOCAL_WATCH_HISTORY_COMMAND_TYPE_PAUSE` action means the returned control currently allows pausing: **enabled**.
- An offered `..._RESUME` action means the returned control allows resuming: **paused**.
- Missing, contradictory, signed-out or unrecognized controls mean **unknown**.

This is an inference from an explicit server-provided actionable control, not an official Google settings API or a push notification. State is account-revision-bound, stays in memory, and becomes unsafe for tracking after **30 seconds**. While playing, checks are scheduled at approximately that interval. Home refresh/settings checks also refresh it. Changes made elsewhere are therefore not mirrored instantaneously; Google remains authoritative.

Confirmed pause/resume uses only the action token offered by that freshly read watch-history control and a fixed `youtubei/v1/feedback` endpoint. It does not replay history-deletion tokens. The resulting history page must show the requested state before the app presents it as verified. A failed/ambiguous change becomes unknown and is not automatically resent. This action does not delete previous history.

In connected mode, Google's paused/unknown/stale state blocks local history/progress writes as well as account reports. **Additional local history pause** can block tracking even when Google history is enabled; it never changes Google's setting. Previously saved local history remains until explicitly cleared.

## Reporting actual native playback

Reporting is **off by default**, independently of Home connection and local history. Once explicitly enabled:

- At least **15 seconds of actual `isPlaying` wall time** are required. Pauses/buffering/unknown-state intervals do not count; seeks do not add distance, and long sampling gaps are conservatively capped. Plays shorter than the threshold are not reported.
- One report is attempted per native view. Quality changes keep the view identity; a new explicit video open is a new view. Offline/downloaded playback, missing view identities, old local history and unplayed metadata previews are not uploaded.
- The viewing item is bound to the web account/session revision at opening. Reviewing/switching accounts immediately invalidates that revision, disables reporting consent and blocks account work until reconnection. An old playing item cannot silently become a new account's report.
- Immediately before a report the current Google history control is read again. Only freshly enabled history plus current consent, no additional local pause and an unchanged session permit proceeding.
- A signed-in watch-page response must identify the actual playing video and provide its playback-tracking URL and web context. Only HTTPS `www.youtube.com` or `s.youtube.com` at the exact `/api/stats/playback` path, with a matching `docid`, are allowed.
- A single playback-registration request is sent with a per-view random nonce and the actual measured played time. This is not a fabricated watchtime stream, view-count booster, recommendation ranker or complete YouTube watch-duration ledger. No offline queue, history backfill or automatic report retry is implemented.
- HTTP acceptance is labelled **report accepted**, not proof that Google stored an activity item or immediately changed recommendations. Verify on official YouTube; account policy, server behavior and processing delay remain authoritative.

A change/disconnect can cancel not-yet-sent work, but an already in-flight request cannot be undone. Credentials remain pinned to the captured account, not substituted with a new account's cookies. A failed/ambiguous report is not automatically sent again for that view.

## Privacy and request safeguards

WebView stores sensitive session cookies in app-private storage; Android backup is disabled. Cookies are not placed in Photon's library/preferences, repository, APK, diagnostic report or a Photon backend. Only the non-secret selected web-account index is read via evaluated JavaScript on a validated YouTube origin. No password-field reader, JavaScript interface or cookie-paste flow is provided.

HTTP credentials are captured on the main thread and used only for allowlisted YouTube hosts. `www.youtube.com` and `s.youtube.com` receive their own CookieManager cookie headers, not a blindly forwarded host-only cookie. Internal web requests use the session's SAPISID authorization scheme. Cookies, opaque feedback/continuation tokens and client contexts remain memory-only outside WebView; server-derived context/tokens are not logged. Redirects and connection retries are disabled, and a per-call network guard prevents a second transmission through automatic follow-up behavior. Responses are size-limited. Bootstrap parsing/network work runs off the UI thread and does not delay playback preparation.

Login navigation allows explicit HTTPS Google/YouTube hosts only, rejects TLS errors and disables file/content access and mixed content. Login uses WebView's real/default user-agent; the separate read-only website parser requests desktop layout, not a disguised Google login. **Clear web session** also works after failed/canceled setup and removes this app's web cookies/site storage, clears the feed, invalidates pending work and disables reporting. It does not sign out Chrome, Android's Google account or revoke native comment OAuth.

## Device acceptance before public distribution

- Real website sign-in; verify default/non-default Google account and brand-channel behavior separately. Do not assume the comment channel matches the website identity.
- Compare Home order, scroll several continuation pages, duplicate handling, refresh, network failure and explicit retry; ensure no search/local fallback when paused/empty.
- Compare detected pause with official YouTube. Pause/resume in Photon and externally; verify read-back and periodic refresh, unknown-state blocking and retention of old local data.
- Enable reporting knowingly, play a remote video for >15 real seconds, and inspect official YouTube history. Test paused/buffering/short/offline plays, quality changes, account switching, consent disabling and cancellation during requests.
- Confirm no duplicate automatic reports, history backfill, inaccurate watched-time claims or cross-account records. Review the shared device report for absence of credentials/URLs.
- Upgrade over stable-signed 0.5.1/0.6 without uninstalling; retain captions, ±10s gestures, original audio, downloads, casting and library navigation.

Keep OAuth testing while validating accounts; add another email as a test user instead of assuming Production status fixes access. Google production verification, privacy/policy review, release-build hardening and real-device acceptance remain separate work. Approval and reliable unofficial website behavior are not guaranteed.

## Implementation references and verification

Prism's `YouTubeMusic.home/continueBrowse`, `playerExtras/registerPlayback` informed the architecture; ordinary-video requests and safeguards are implemented separately. Structured history-control shape was checked against public TeamPiped/rustypipe and yewtubes history fixtures; tests use newly constructed synthetic data, not copied account history/identifiers/action tokens.

0.7 Android build and live-device acceptance are pending verification. The focused test source contains 98 methods, including account continuation, structured pause parsing, policy freshness, real-play gating, global-setting read-back, account changes, scoped cookies and ambiguous-report failure. No test sends live account mutations.
