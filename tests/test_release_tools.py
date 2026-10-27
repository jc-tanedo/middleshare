"""Regression checks for release certificate checks and private signing-file handling."""

import base64
from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import debug_signing
import release
from signing import write_keystore


class CertificateTests(unittest.TestCase):
    def test_rejects_both_default_and_project_debug_certificates(self):
        for subject in ("CN=Android Debug, O=Android", "CN=MiddleShare Debug"):
            with self.subTest(subject=subject), self.assertRaises(ValueError):
                release.certificate_fingerprint(
                    f"Signer #1 certificate DN: {subject}\n"
                    f"Signer #1 certificate SHA-256 digest: {'a' * 64}\n"
                )

    def test_accepts_complete_release_fingerprint(self):
        self.assertEqual(
            "ab" * 32,
            release.certificate_fingerprint(
                "Signer #1 certificate DN: CN=MiddleShare\n"
                f"Signer #1 certificate SHA-256 digest: {'AB' * 32}\n"
            ),
        )

    def test_rejects_missing_or_truncated_fingerprint(self):
        for output in ("", "Signer #1 certificate SHA-256 digest: abc123"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                release.certificate_fingerprint(output)

    def test_version_tag_must_match_the_project_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text("versionName '1.2.3'", encoding="utf-8")
            with patch.object(release, "ROOT", root):
                with patch.dict(os.environ, {"RELEASE_TAG": "v1.2.3"}):
                    self.assertEqual("1.2.3", release.version())
                with patch.dict(os.environ, {"RELEASE_TAG": "v1.2.2"}):
                    with self.assertRaises(ValueError):
                        release.version()


class PrereleaseTests(unittest.TestCase):
    def project(self, directory, version):
        root = Path(directory)
        (root / "app").mkdir()
        (root / "app/build.gradle").write_text(f"versionName '{version}'", encoding="utf-8")
        return root

    def test_preview_suffixes_must_match_the_committed_version(self):
        for suffix in ("alpha.1", "beta.2", "rc.1"):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as directory:
                version = "1.2.3-" + suffix
                root = self.project(directory, version)
                with patch.object(release, "ROOT", root):
                    with patch.dict(os.environ, {"RELEASE_TAG": "v" + version}):
                        self.assertEqual(version, release.version())
                    with patch.dict(os.environ, {"RELEASE_TAG": "v1.2.3"}):
                        with self.assertRaises(ValueError):
                            release.version()

    def test_invalid_preview_suffixes_are_rejected_even_if_the_version_matches(self):
        for suffix in ("rc", "rc.0", "rc.01", "preview.1", "rc.1.extra"):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as directory:
                version = "1.2.3-" + suffix
                root = self.project(directory, version)
                with patch.object(release, "ROOT", root):
                    with patch.dict(os.environ, {"RELEASE_TAG": "v" + version}):
                        with self.assertRaises(ValueError):
                            release.version()

    def test_publication_marks_only_preview_versions_as_prereleases(self):
        for version in ("1.2.3", "1.2.3-rc.1"):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = self.project(directory, version)
                assets = root / "release-assets"
                assets.mkdir()
                for number in range(5):
                    (assets / f"fixture-{number}.apk").write_bytes(b"fixture")
                (assets / "SHA256SUMS").write_text("fixture", encoding="utf-8")
                with patch.object(release, "ROOT", root):
                    with patch.dict(os.environ, {"RELEASE_TAG": "v" + version}):
                        with patch.object(release.subprocess, "run") as run:
                            release.publish()
                command = run.call_args.args[0]
                self.assertEqual(command[:4], ["gh", "release", "create", "v" + version])
                self.assertEqual("--prerelease" in command, "-" in version)
                self.assertIn("--verify-tag", command)

    def test_publication_requires_collected_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.project(directory, "1.2.3-rc.1")
            with patch.object(release, "ROOT", root):
                with patch.dict(os.environ, {"RELEASE_TAG": "v1.2.3-rc.1"}):
                    with patch.object(release.subprocess, "run") as run:
                        with self.assertRaises(ValueError):
                            release.publish()
                        run.assert_not_called()


class SigningFileTests(unittest.TestCase):
    def test_decodes_wrapped_base64_and_creates_private_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "fixture.jks"
            encoded = base64.b64encode(b"fixture-only").decode("ascii")
            write_keystore("\n" + encoded + "\n", target)
            self.assertEqual(b"fixture-only", target.read_bytes())
            self.assertEqual(0o600, target.stat().st_mode & 0o777)

    def test_invalid_base64_does_not_create_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "fixture.jks"
            with self.assertRaises(ValueError):
                write_keystore("invalid!", target)
            self.assertFalse(target.exists())

    def test_overwrite_is_explicit_and_restores_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "fixture.jks"
            target.write_bytes(b"old fixture")
            target.chmod(0o644)
            encoded = base64.b64encode(b"new fixture").decode("ascii")
            with self.assertRaises(FileExistsError):
                write_keystore(encoded, target)
            self.assertEqual(b"old fixture", target.read_bytes())
            write_keystore(encoded, target, overwrite=True)
            self.assertEqual(b"new fixture", target.read_bytes())
            self.assertEqual(0o600, target.stat().st_mode & 0o777)

    def test_debug_setup_rejects_partial_secrets_and_keeps_output_private(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "middleshare-debug.jks"
            environment = {
                "RUNNER_TEMP": directory,
                "GITHUB_ENV": str(Path(directory) / "environment"),
                "ANDROID_DEBUG_KEYSTORE_BASE64": base64.b64encode(b"fixture-only").decode(),
            }
            with patch.dict(os.environ, environment, clear=True):
                with self.assertRaises(ValueError):
                    debug_signing.prepare()
                self.assertFalse(target.exists())
                os.environ["ANDROID_DEBUG_KEYSTORE_PASSWORD"] = "fixture-only"
                output = io.StringIO()
                with redirect_stdout(output):
                    debug_signing.prepare()
                self.assertEqual("", output.getvalue())
                self.assertEqual(b"fixture-only", target.read_bytes())
                self.assertIn(
                    f"ANDROID_DEBUG_KEYSTORE_PATH={target}",
                    Path(environment["GITHUB_ENV"]).read_text(encoding="utf-8"),
                )


if __name__ == "__main__":
    unittest.main()
