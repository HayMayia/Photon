# Native Google authorization and YouTube commenting (0.4 alpha)

Photon uses Google Play services' official AuthorizationClient and YouTube Data API v3. It does not ask for a Google password, accept browser cookies, use a client secret, or fake successful comments. **Code alone does not register an app with Google. This repository does not provision a Google Cloud project or OAuth client. Until the app owner completes the setup below, native authorization may be rejected.** Physical-device sign-in and real posting remain unverified.

## One-time app-owner setup

1. In Google Cloud Console, select/create a project and enable **YouTube Data API v3**.
2. Configure Google Auth Platform / OAuth consent, including app identity, support details, appropriate audience and the scope `https://www.googleapis.com/auth/youtube.force-ssl`. This is a broad YouTube management scope required by Google's comment insertion endpoint; Photon currently uses it only for channel identity and explicitly confirmed top-level comments. Google may require verification before public distribution.
3. If the project is in testing, add the intended Google accounts as test users in the Console. Do not post their addresses or credentials in issues/chat.
4. Install the intended APK. In Photon **Settings → YouTube account → Google sign-in setup / troubleshooting**, share/copy its **public package and SHA-1**. Package must be `io.github.haymayia.photon`. Create an **Android OAuth client** with that package and the exact installed signing certificate SHA-1. This authorization-only flow does not need a web client ID or secret embedded in the APK.
5. Use a Google Play services device with a Google account that has a YouTube channel. Tap **Sign in with Google**, review the disclosure and Google's own account/permission UI. Photon then verifies the channel through the official `channels?mine=true` endpoint and displays the verified channel name.
6. Open a video's comments, write a draft, select **Review and post**, verify the channel and video, and explicitly choose **Post publicly**. Only a returned YouTube comment ID counts as confirmation. Check the official YouTube page too; moderation can delay/hide public display.

### Signing caveat — important

CI currently uses a generated Android debug signing certificate, not a securely provisioned stable release key. Its SHA-1 can change between builds. Registering one build does not necessarily authorize another; a changed certificate can also prevent installing an update over the existing app. The in-app fingerprint is for the **installed APK**, not a guessed CI value. Production distribution needs a stable privately managed signing key and registration of that key's public SHA-1 (and Play App Signing certificate if distributing through Play). Never commit a private signing key/password. Do not uninstall casually: uninstalling removes Photon's private downloads/library.

## Session/privacy behavior

- Access tokens remain in process memory only; Photon does not put them in preferences, logs, backups, bundles or diagnostic reports. Google Play services manages the system authorization grant independently.
- Reopening after process death or after the conservative 50-minute session window requires reconnecting. No refresh tokens or backend server are used.
- **Disconnect** clears Photon's session, not the device's Google account or Google's underlying consent grant. Revoke that separately through **Manage / revoke Google access** in setup help.
- Account switching/reconnecting cannot submit a draft automatically. Posting is a separate explicit public confirmation.
- Posts use a fixed official HTTPS endpoint with redirects and connection retries disabled. A network failure can still occur after YouTube accepted a comment: Photon retains the draft and warns you to check YouTube before manually retrying, rather than automatically sending a duplicate.
- Drafts survive ordinary UI recreation via Android saved state; they are not a durable draft database. A confirmed response clears the active draft. The displayed latest confirmed comment is session-only, not a fabricated replacement for YouTube's feed.
- Only top-level comments are posted in this version. Replies are read-only. Likes, subscriptions, playlists and full account-library synchronization are not implemented by this authorization step; existing saved items remain local.

## Validation

Automated protocol/session tests use an injected local HTTP interceptor, not a Google account or live comments. They cover request body/endpoint, server ID requirement, sanitized errors, missing scopes, stale callbacks, expired sessions, disconnect and unauthorized responses. They cannot establish that Google accepted a Cloud registration or that a physical device completed authorization.

Manual checks required: current/missing Play services; correctly/incorrectly registered package and certificate; consent cancellation/denial; rotation/background/process death; reconnect/channel switching; disabled comments; API/quota/network errors; preserved draft; duplicate avoidance; successful explicit posting and server ID visible on official YouTube; disconnect/revocation. Never post test comments without the channel owner's consent.
