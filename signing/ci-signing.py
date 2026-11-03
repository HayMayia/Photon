#!/usr/bin/env python3
"""One-time encrypted signing-key bootstrap / trusted-CI restore.

Private files exist only under RUNNER_TEMP. Never prints passwords or private keys.
The encrypted backup and certificate are public; the high-entropy password belongs
only in the owner's password manager and GitHub Actions secret.
"""
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BACKUP = ROOT / "signing/photon-signing.p12.enc"
CERT = ROOT / "signing/photon-signing-cert.pem"
IDENTITY = ROOT / "signing/ANDROID_OAUTH.txt"


def run(*args, capture=False):
    return subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE if capture else None).stdout


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("bootstrap", "restore"):
        raise SystemExit("Use bootstrap or restore")
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_EVENT_NAME") not in ("push", "workflow_dispatch"):
        raise SystemExit("Signing is restricted to trusted push/manual Actions jobs")
    if os.environ.get("GITHUB_REF") != "refs/heads/arena/c217a41b-photon":
        raise SystemExit("Signing is restricted to the Photon session branch")
    password = os.environ.get("PHOTON_SIGNING_PASSWORD", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{40,128}", password):
        raise SystemExit("Set PHOTON_SIGNING_PASSWORD to 40–128 password-manager-generated random letters/digits/_/-. Never send it in chat.")
    temp = pathlib.Path(os.environ["RUNNER_TEMP"])
    key = temp / "photon-signing.p12"
    mode = sys.argv[1]
    os.umask(0o077)
    if mode == "bootstrap":
        if BACKUP.exists() or CERT.exists() or IDENTITY.exists():
            raise SystemExit("Signing identity already exists. Refusing to rotate or overwrite it.")
        run("keytool", "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12", "-alias", "photon",
            "-storepass:env", "PHOTON_SIGNING_PASSWORD", "-keypass:env", "PHOTON_SIGNING_PASSWORD",
            "-keyalg", "RSA", "-keysize", "4096", "-validity", "10950", "-dname", "CN=Photon, O=Photon")
        run("openssl", "enc", "-aes-256-cbc", "-salt", "-pbkdf2", "-iter", "600000",
            "-in", str(key), "-out", str(BACKUP), "-pass", "env:PHOTON_SIGNING_PASSWORD")
        run("keytool", "-exportcert", "-rfc", "-keystore", str(key), "-storetype", "PKCS12", "-alias", "photon",
            "-storepass:env", "PHOTON_SIGNING_PASSWORD", "-file", str(CERT))
        fingerprint = run("openssl", "x509", "-in", str(CERT), "-noout", "-fingerprint", "-sha1", capture=True).strip().split("=", 1)[1]
        IDENTITY.write_text("Photon Android OAuth registration\n\nPackage name: io.github.haymayia.photon\nSHA-1 certificate fingerprint: " + fingerprint + "\n\nClient type: Android\nScope: https://www.googleapis.com/auth/youtube.force-ssl\n\nPublic certificate identity only. No private key or password is included.\n")
        print(IDENTITY.read_text())
    else:
        if not BACKUP.is_file() or not CERT.is_file():
            raise SystemExit("Missing encrypted signing identity. Run the one-time bootstrap first.")
        run("openssl", "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-iter", "600000",
            "-in", str(BACKUP), "-out", str(key), "-pass", "env:PHOTON_SIGNING_PASSWORD")
        # PKCS12 integrity/password validation and exact public-certificate match before signing.
        exported = run("keytool", "-exportcert", "-rfc", "-keystore", str(key), "-storetype", "PKCS12", "-alias", "photon",
            "-storepass:env", "PHOTON_SIGNING_PASSWORD", capture=True)
        def pem_body(text):
            return "".join(line.strip() for line in text.splitlines() if not line.startswith("-----"))
        if pem_body(exported) != pem_body(CERT.read_text()):
            raise SystemExit("Decrypted key does not match the registered certificate")
        (ROOT / "keystore.properties").write_text(
            f"storeFile={key}\nstorePassword={password}\nkeyAlias=photon\nkeyPassword={password}\n")
        print("Stable Photon signing identity restored and certificate verified.")


if __name__ == "__main__":
    main()
