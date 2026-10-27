"""Ensure the optimized-APK check rejects the failure seen in production."""

from contextlib import redirect_stdout
import io
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import verify_release_zip_handlers as verifier


REGISTRY = """
10: ldc_w #64 // class org/apache/commons/compress/archivers/zip/AsiExtraField
13: invokestatic #65 // Method register:(Ljava/lang/Class;)V
"""


class ReleaseZipHandlerTests(unittest.TestCase):
    def verify_fixture(self, cls):
        xml = f'<api><package name="h0">{cls}</package></api>'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, mapping = root / "fixture.apk", root / "mapping.txt"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"mock DEX")
            mapping.write_text(
                "org.apache.commons.compress.archivers.zip.AsiExtraField -> h0.a:\n",
                encoding="utf-8",
            )

            def run(command, **kwargs):
                return SimpleNamespace(stdout=REGISTRY if command[0] == "javap" else xml)

            with patch.object(verifier.subprocess, "run", side_effect=run):
                with redirect_stdout(io.StringIO()):
                    verifier.verify(apk, mapping, root / "compress.jar", root / "dexdump")

    def test_accepts_a_concrete_renamed_handler_with_a_public_no_arg_constructor(self):
        self.verify_fixture(
            '<class name="a" abstract="false" interface="false">'
            '<constructor visibility="public" static="false"/></class>'
        )

    def test_rejects_abstract_missing_and_uninstantiable_handlers(self):
        for cls in (
            "",
            '<class name="a" abstract="true" interface="false"/>',
            '<class name="a" abstract="false" interface="true"/>',
            '<class name="a" abstract="false" interface="false"/>',
            '<class name="a" abstract="false" interface="false">'
            '<constructor visibility="private" static="false"/></class>',
            '<class name="a" abstract="false" interface="false">'
            '<constructor visibility="public" static="true"/></class>',
            '<class name="a" abstract="false" interface="false">'
            '<constructor visibility="public" static="false">'
            '<parameter type="int"/></constructor></class>',
        ):
            with self.subTest(cls=cls), self.assertRaises(ValueError):
                self.verify_fixture(cls)

    def test_rejects_unrecognized_or_partially_parsed_registries(self):
        for bytecode in (
            "",
            REGISTRY + "20: invokestatic #65 // Method register:(Ljava/lang/Class;)V",
        ):
            with self.subTest(bytecode=bytecode):
                result = SimpleNamespace(stdout=bytecode)
                with patch.object(verifier.subprocess, "run", return_value=result):
                    with self.assertRaises(ValueError):
                        verifier.registered_handlers(Path("compress.jar"))
