"""Safety gates only; no real private key or password is generated in tests."""
import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_signing", pathlib.Path(__file__).with_name("ci-signing.py"))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningSafetyTest(unittest.TestCase):
    def invoke(self, mode="bootstrap", **changes):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": "push",
               "GITHUB_REF": "refs/heads/arena/c217a41b-photon",
               "PHOTON_SIGNING_PASSWORD": "unit_test_only_not_a_real_password_" * 2}
        env.update(changes)
        with patch.dict(os.environ, env, clear=True), patch.object(signing.sys, "argv", ["ci-signing.py", mode]):
            signing.main()

    def test_local_execution_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "trusted"):
            self.invoke(GITHUB_ACTIONS="false")

    def test_pull_requests_cannot_restore_keys(self):
        with self.assertRaisesRegex(SystemExit, "trusted"):
            self.invoke(mode="restore", GITHUB_EVENT_NAME="pull_request")

    def test_other_branches_cannot_sign(self):
        with self.assertRaisesRegex(SystemExit, "branch"):
            self.invoke(GITHUB_REF="refs/heads/main")

    def test_missing_or_short_password_is_rejected_without_disclosure(self):
        for value in ("", "short", "x" * 39, "x" * 129, "x" * 45 + "!"):
            with self.assertRaises(SystemExit) as error:
                self.invoke(PHOTON_SIGNING_PASSWORD=value)
            if value:
                self.assertNotIn(value, str(error.exception))

    def test_existing_identity_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            backup = pathlib.Path(temp) / "already-encrypted.enc"
            backup.write_bytes(b"test fixture, not a key")
            with patch.object(signing, "BACKUP", backup), self.assertRaisesRegex(SystemExit, "Refusing"):
                self.invoke(RUNNER_TEMP=temp)
            self.assertEqual(backup.read_bytes(), b"test fixture, not a key")

    def test_missing_backup_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.object(signing, "BACKUP", pathlib.Path(temp) / "absent"), self.assertRaisesRegex(SystemExit, "Missing"):
                self.invoke(mode="restore", RUNNER_TEMP=temp)


if __name__ == "__main__":
    unittest.main()
