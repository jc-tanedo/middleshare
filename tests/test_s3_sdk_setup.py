"""Verify SDK wheel extraction cannot write outside its cache directory."""

from pathlib import Path
import stat
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from setup_s3_sdk import extract_wheel


class SDKSetupTests(unittest.TestCase):
    def test_extracts_imports_but_skips_auxiliary_console_scripts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(root / "fixture.whl", "w") as archive:
                archive.writestr("example/__init__.py", "")
                archive.writestr("example-1.data/scripts/example", "not installed")
            extract_wheel(root / "fixture.whl", root / "output")
            self.assertTrue((root / "output/example/__init__.py").is_file())
            self.assertFalse((root / "output/example-1.data").exists())

    def test_rejects_traversal_and_symlink_entries(self):
        for name, symlink in (
            ("../outside", False),
            ("/absolute", False),
            ("link", True),
        ):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with zipfile.ZipFile(root / "fixture.whl", "w") as archive:
                    info = zipfile.ZipInfo(name)
                    if symlink:
                        info.external_attr = (stat.S_IFLNK | 0o777) << 16
                    archive.writestr(info, "data")
                with self.assertRaises(ValueError):
                    extract_wheel(root / "fixture.whl", root / "output")
