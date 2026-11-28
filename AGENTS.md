# Photon release notification rule

The user explicitly requests an email notification for each newly published Photon
update. After verifying publication and that the intended downloadable asset is
uploaded (not merely a successful compile or a draft release), compose and SEND
an email using the connected Gmail app:

- To: nimit3956@gmail.com
- Subject: Updated Photon to [version name]
  Example: Updated Photon to 1.1.1.3 Beta
- Body: version and app variant, concise changelog, actual HTTPS download and
  release-notes links, installation/migration instructions, validation results,
  and important known limitations. Never claim physical-device testing unless
  actually performed. Never include credentials, private keys or account tokens.
- Send promptly after verified publication, before ending the release task; do
  not leave only a draft. The user has authorized these release notifications.
- Consult the notification log below before sending to avoid duplicates. If a
  send result is uncertain, check Gmail Sent before retrying. Record successful
  sends here with their returned message IDs.
- If Gmail is unavailable or sending fails, tell the user; do not claim delivery.
- This is an agent release checklist, NOT an unattended email service. Do not
  promise exact-second delivery, background monitoring, or notifications while
  the agent is inactive without separately configured and verified automation.

## Notification log

- 1.1.1.3 Beta (original Photon): sent to the requested recipient; Gmail message
  ID `1a221051a936b0ce`. Subject: `Updated Photon to 1.1.1.3 Beta`.

- 1.1.1.4 Beta (original Photon): sent after successful CI and uploaded APK
  verification; Gmail message ID `1a2211d6e48f2c6b`.
  Subject: `Updated Photon to 1.1.1.4 Beta`.
