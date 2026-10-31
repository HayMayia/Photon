# Photon Privacy Policy

Effective date: 31 October 2026

Photon is an independent, open-source Android YouTube client maintained through [HayMayia/Photon](https://github.com/HayMayia/Photon). It is not affiliated with or endorsed by Google or YouTube. This policy describes the current Photon implementation, including optional Google account features.

## Information Photon uses

- **Public content:** searches, video/channel identifiers and playback requests are sent to YouTube or its content-delivery providers to retrieve videos, images, subtitles, comments and metadata.
- **Connected account:** when you sign in, Photon uses an app-private Google/YouTube web session to request your account Home, available playlists, subscriptions, liked videos and history controls. This is an unofficial integration, distinct from the official authorization used for comments. Google handles the sign-in page; Photon does not read password fields. The embedded browser stores sensitive session cookies in the app's private storage; Photon uses session credentials for authenticated YouTube requests.
- **Optional comment authorization:** Google Play services obtains the YouTube permission needed to identify your channel and post comments you explicitly approve. This permission is separate from account Home. Access tokens are held in process memory, not deliberately written to application preferences or diagnostic reports; Google Play services separately manages its authorization grant.
- **Device storage:** Photon stores settings, downloaded media and artwork, and device playback/history/resume caches needed for its features. It may hold account content and state in memory while you use the app. Do not treat these caches as an independent backup of your Google account.
- **Diagnostics:** a bounded, in-memory playback-event buffer supports a report that you can review and share yourself. Reports include app version, device make/model, Android API level and playback events. They are not automatically uploaded by this reporting feature and are designed to exclude credentials and stream URLs.

## How information is used and shared

Information is used to provide the features you request. Like/unlike, subscription and supported playlist action taps, and authorized comment Post taps, send requests directly to Google/YouTube and can change your real account. v1.0.4 does not show a second app confirmation; Google/Android still require their own authorization and permissions. Public comments are subject to YouTube's visibility and moderation rules.

Optional playback-history reporting requires explicit consent and a freshly confirmed enabled Google history state. Paused, unknown or stale history state blocks reporting. Reports reflect eligible real playback, not a backfill of past or offline activity. Short-play reporting has a separate opt-in switch. The settings switches are the explicit opt-ins; no second app confirmation is shown. Turning reporting off does not erase history already stored by Google.

Casting can communicate with devices on your local network. Media and associated information may be sent to the receiver you select. Android and Google services, YouTube, media/image delivery providers, and cast receivers process requests under their own policies. Network requests necessarily expose information such as IP address and request metadata to the receiving service.

The Photon implementation does not operate a developer-run account-data backend, sell Google user data, use Google account data for advertising, or transfer it to data brokers. Information you deliberately share through another app or post in a public support issue is handled by that destination. Do not publish passwords, cookies, tokens, private signing keys or sensitive account details in support reports.

Photon's use and transfer of information received from Google APIs will adhere to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including its Limited Use requirements. This statement is a developer commitment, not a claim of Google verification or endorsement.

## Updates and optional Prism download

Home and Settings contact GitHub's public Photon release endpoints to check versions, including Beta releases. Requests disclose your IP/network metadata and a user-agent containing Photon's app version to GitHub; no Google session cookies, account tokens or library contents are sent by the updater. At your request it downloads an APK into private cache, checks package/version/signing identity and opens Android's installer for your approval. Android may require permission to install from Photon. Cached update files can remain until overwritten, cache management, clearing app storage or uninstalling. See [GitHub's Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

The optional Music Photon, "Prism" Settings entry opens the official upstream APK download through a receiving browser/app. Prism is a separate application by Yigder; its own permissions, data handling and terms apply. It is not installed silently or bundled into Photon.

## Storage, retention and deletion

Local files remain until removed by the relevant app controls, Android's clear-storage/uninstall controls, or cache management. In-memory tokens, diagnostics and caches cease to exist when the process ends. App backup is disabled, but no storage or transmission system can be guaranteed completely secure.

- **Sign out/disconnect account Home** to clear Photon's web session cookies. This does not revoke the separate native comment authorization.
- **Revoke native Google access** through your [Google account connections](https://myaccount.google.com/connections). Revocation and sign-out are separate operations.
- **Clear device playback cache** in Photon to remove the device history/resume cache. This does not delete Google activity.
- Remove downloads through Photon, or clear Android app storage/uninstall to remove app-private data. Uninstalling removes private downloads too.
- Manage or delete activity, comments, likes, subscriptions and playlists through official YouTube/Google controls. Clearing Photon data does not reverse account changes already accepted by Google.

## Permissions and choices

Internet access supports online content. Notifications support playback/download controls; local-network permissions support casting; foreground services support playback and downloads. Visible-playback screen-awake behavior prevents ordinary timeout while playing. Android permission controls and Photon settings allow you to restrict applicable optional features.

## Children, changes and contact

Photon is not specifically directed to children. Use requires compliance with applicable age requirements and Google's/YouTube's account rules. No child-specific service or parental-control system is provided.

Material changes will be reflected in this public policy and its effective date. For general privacy questions, contact the maintainer through [Photon support](https://github.com/HayMayia/Photon/issues). Issues are public: do not include personal account data. Use the support email listed on Photon's Google consent screen for private correspondence.

Also see [Google's Privacy Policy](https://policies.google.com/privacy) and [YouTube's Terms](https://www.youtube.com/t/terms).
