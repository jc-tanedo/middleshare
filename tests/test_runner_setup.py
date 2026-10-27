"""Check dependency detection without installing packages on the test machine."""

import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / ".github/actions/setup-runner/setup.sh"


def bash(code, env=None):
    return subprocess.run(
        ["bash", "-c", f"source {shlex.quote(str(SCRIPT))}\n{code}"],
        capture_output=True, text=True, env=env,
    )


class RunnerSetupTests(unittest.TestCase):
    def test_existing_base_tools_do_not_invoke_apt(self):
        result = bash("""
command() { return 0; }
dpkg-query() { printf 'install ok installed'; }
install_packages() { echo 'Unexpected installation'; return 1; }
ensure_tools
""")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("Unexpected installation", result.stdout)

    def test_only_missing_tools_are_requested(self):
        result = bash("""
command() { [[ $2 != unzip && $2 != gh ]]; }
dpkg-query() { printf 'install ok installed'; }
install_packages() { printf 'INSTALL:%s\n' "$@"; }
ensure_tools
""")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            [line for line in result.stdout.splitlines() if line.startswith("INSTALL:")],
            ["INSTALL:unzip", "INSTALL:gh"],
        )

    def test_missing_ca_certificates_are_installed(self):
        result = bash("""
command() { return 0; }
dpkg-query() { return 1; }
install_packages() { printf 'INSTALL:%s\n' "$@"; }
ensure_tools
""")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("INSTALL:ca-certificates", result.stdout)

    def test_only_complete_supported_jdks_are_reused(self):
        for version, omit, expected in ((17, None, 0), (21, None, 0), (11, None, 1), (17, "javap", 1)):
            with self.subTest(version=version, omit=omit), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / "bin").mkdir()
                for name in ("java", "javac", "javap", "keytool"):
                    if name == omit:
                        continue
                    tool = root / "bin" / name
                    tool.write_text(
                        f'#!/bin/bash\necho \'openjdk version "{version}.0.1"\'\n',
                        encoding="utf-8",
                    )
                    tool.chmod(0o755)
                env = dict(os.environ, GITHUB_ENV=str(root / "env"), GITHUB_PATH=str(root / "paths"))
                result = bash(f"reuse_java {shlex.quote(directory)}", env)
                self.assertEqual(result.returncode, expected, result.stderr)
                if expected == 0:
                    self.assertIn(f"JAVA_HOME={root}", (root / "env").read_text())

    def test_only_a_complete_sdk_skips_installation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = [
                "platforms/android-35/android.jar", "platforms/android-35/source.properties",
                "build-tools/35.0.0/source.properties", "platform-tools/source.properties",
                "platform-tools/adb", "licenses/android-sdk-license",
                *(f"build-tools/35.0.0/{name}" for name in ("aapt", "aapt2", "apksigner", "dexdump", "zipalign")),
            ]
            for name in files:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture", encoding="utf-8")
                path.chmod(0o755)
            command = f"sdk_complete {shlex.quote(directory)}"
            self.assertEqual(bash(command).returncode, 0)
            for name in files:
                path = root / name
                path.unlink()
                with self.subTest(missing=name):
                    self.assertNotEqual(bash(command).returncode, 0)
                path.write_text("fixture", encoding="utf-8")
                path.chmod(0o755)
