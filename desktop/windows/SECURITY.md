# Windows security and trust procedure

## What this build does

- Runs as the current user; the installer does not request admin privileges, add an antivirus exclusion, install a root certificate, disable Windows security or change system policy.
- Does not read browser cookies, Windows credentials, Google tokens, microphone, camera or personal media libraries. Google login is not implemented in this beta.
- Uses HTTPS YouTube inputs and exact host-boundary media/artwork allowlists. Searches cannot become process flags. Process launch uses ArgumentList, not a shell. User configuration/plugins and Node/Python injection environment options are disabled for the extractor.
- Verifies bundled extractor/runtime hashes before execution; release CI checks the pinned official extractor digest. Runtime checks detect accidental/local changes but do not authenticate a maliciously replaced whole unsigned installation.
- Saves downloads only at a user-chosen path, writes a temporary file and checks completion before replacing the target. Cancelling/closing stops lookup/download work. No telemetry, analytics, remote extraction fallback, silent self-updates or account-history writes.

## Release checklist

1. Review changes, dependencies and bundled executable provenance. Run core checks, native startup smoke test and dependency audit. CI fails known high/critical NuGet warnings. This does not audit every bundled native dependency.
2. Generate SHA256SUMS from the final installer; publish alongside the exact immutable build and source. On Windows: `Get-FileHash .\Photon-Windows-x64-Setup.exe -Algorithm SHA256`. Compare against the official release, not an untrusted forwarded checksum.
3. Scan the final EXE with an updated Microsoft Defender. If desired, submit the **public** installer to VirusTotal and retain its hash/report. No detections is not proof of safety; never upload private signing keys or user data.
4. If Defender or SmartScreen misclassifies the file, use Microsoft's submission portal: https://www.microsoft.com/en-us/wdsi/filesubmission . Select the actual affected product and describe the unsigned open-source app, exact hash and source/build URLs. Review approval is not guaranteed, and distinct builds can receive distinct verdicts.
5. For no-cost publisher signing, investigate SignPath Foundation: https://signpath.org/apply and https://signpath.org/terms.html . It reviews eligibility, project reputation, dependencies and the source-to-binary pipeline; acceptance is NOT automatic. Do not claim approval before receiving it. Sign only eligible artifacts under its approved policy, then timestamp, verify Authenticode, rescan and compute final hashes **after signing**.
6. A self-signed certificate is not a globally trusted Windows publisher identity. Do not reuse the Android key, ask users to trust a home-made root, disable SmartScreen or claim that a hash/scan prevents every warning.

## User-facing expectations

The initial Windows release has **no trusted Authenticode signature**. SmartScreen may display “unrecognized app” or unknown publisher. That is not proof of malware, but also not a reason to bypass a harmful-file detection. Verify provenance and scan results; stop and report an unexpected harmful verdict. There is no guaranteed warning-free “safe everywhere” certificate or review result.

The Windows trust process is separate from the Android Play Protect appeal; approval for one APK does not approve this EXE.
