# Stable Photon signing: owner-only bootstrap

Status: automation prepared; **no permanent key/fingerprint exists until the owner saves the secret and bootstrap succeeds**. This does not register Google OAuth or establish working sign-in.

## Owner action (one time)

1. Open https://github.com/HayMayia/Photon/settings/secrets/actions/new .
2. Name: `PHOTON_SIGNING_PASSWORD`.
3. Value: a **password-manager-generated random 40–128 character string**, containing only ASCII letters, digits, `_` or `-`. Keep an independent copy in your password manager. Do not use a memorable sentence, send it in chat, put it in an issue, or commit it to Git.
4. Save as a repository Actions secret. The Arena GitHub integration currently cannot manage/list Actions secrets (403), so this must be done by the owner in GitHub.
5. Tell the agent only that the secret is saved. The agent will add `signing/SETUP_REQUEST` on the session branch to trigger the one-time workflow. No password is sent to the agent.

The setup workflow generates a 4096-bit RSA/PKCS12 key on an ephemeral GitHub runner, encrypts its backup using AES-256-CBC/PBKDF2 (600,000 iterations), and commits **only the encrypted backup and public certificate/fingerprint**. PKCS12 also protects the key with an integrity MAC. The high-entropy password is essential because the encrypted backup is intentionally public and can be attacked offline. There is no automatic key rotation or overwrite.

It then explicitly dispatches `android.yml` (ordinary pushes made by a workflow's GITHUB_TOKEN do not start another workflow automatically). If dispatch fails, the key is still safely committed; run the Android workflow explicitly rather than regenerating a key.

## What happens next

After a successful setup, `signing/ANDROID_OAUTH.txt` contains the real package and SHA-1. Create Google's **Android** OAuth client using **that fingerprint**, then install the new successfully built APK. Trusted APK builds restore the same private key temporarily, verify its certificate, sign the APK, and verify the final APK certificate against the public identity before uploading it. The APK ZIP includes `ANDROID_OAUTH.txt`.

External pull requests do not receive or restore the key. PR-test APKs remain temporary-debug-signed; use a trusted push/manual run APK for OAuth/device tests. Automation currently targets only `arena/c217a41b-photon`; moving to another release branch later requires an explicit reviewed gate update, not a new key.

**Migration warning:** older APKs were signed with unrelated ephemeral debug keys. The permanent key cannot recover those keys or make Android accept an in-place update. Do not uninstall a populated app without preserving needed data. This setup does not implement library export/migration. The stable key provides consistent signing for builds made *after* this transition; it does not fix past signatures.

## Recovery and security

- Download/retain the encrypted backup artifact or clone the committed encrypted backup, and independently retain the password. Keep the public certificate and SHA-1 as well.
- Losing the password and every decrypted private-key backup can make future same-certificate updates impossible. Changing the GitHub secret does **not** re-encrypt the existing backup. Restore the original value if accidentally changed; do not rerun bootstrap to rotate identities.
- Only trusted repository maintainers should be able to alter signing workflows or trigger trusted builds. Repository write access can modify a workflow to misuse signing secrets. Review third-party actions and future workflow changes accordingly.
- Plaintext key/keystore.properties exist only under RUNNER_TEMP/the ephemeral checkout during a trusted job, are excluded from artifacts/Git, and are removed by an always-run cleanup step. They are never intentionally cached. Forced runner shutdown relies on the hosted runner's ephemeral teardown.
- Secrets are passed through environment variables, not embedded in scripts, command-line password arguments or public logs. The public certificate and SHA-1 are not secrets.
- First stable APK build, bootstrap and actual OAuth/device tests remain pending owner setup. Local checks cover Python safety gates, YAML syntax and shell syntax; they are not a real signing/build test.
