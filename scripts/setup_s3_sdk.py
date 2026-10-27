#!/usr/bin/env python3
"""Cache hash-verified, pure-Python S3 SDK wheels without pip, sudo, or system changes."""

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import sys
import tempfile
import urllib.request
import zipfile

LOCK = Path(__file__).with_name("s3-sdk-lock.json")


def extract_wheel(archive, destination):
    with zipfile.ZipFile(archive) as wheel:
        members = []
        for entry in wheel.infolist():
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.filename:
                raise ValueError("Unsafe path in SDK wheel.")
            if stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError("Only ordinary pure-Python wheels are supported.")
            data = next(
                (i for i, part in enumerate(path.parts) if part.endswith(".data")), None
            )
            if data is not None:
                # The SDK needs imports only, not auxiliary command-line entry points.
                if path.parts[data + 1 : data + 2] == ("scripts",):
                    continue
                raise ValueError("Unsupported SDK wheel installation scheme.")
            members.append(entry)
        wheel.extractall(destination, members)


def install():
    content = LOCK.read_bytes()
    lock = json.loads(content)
    if sys.version_info[:2] < tuple(lock["python_minimum"]):
        raise RuntimeError("CI S3 uploads require Python 3.10 or newer.")
    digest = hashlib.sha256(content).hexdigest()
    cache = (
        Path(os.environ.get("RUNNER_TOOL_CACHE", Path.home() / ".cache"))
        / "middleshare-s3"
    )
    target = cache / digest[:20]
    if not (target / ".complete").is_file():
        cache.mkdir(parents=True, exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix="install-", dir=cache))
        try:
            for package in lock["packages"]:
                with urllib.request.urlopen(package["url"], timeout=120) as response:
                    payload = response.read()
                if hashlib.sha256(payload).hexdigest() != package["sha256"]:
                    raise ValueError(f'SDK integrity check failed: {package["name"]}')
                archive = stage / "package.whl"
                archive.write_bytes(payload)
                extract_wheel(archive, stage)
                archive.unlink()
            (stage / ".complete").write_text(digest, encoding="ascii")
            try:
                stage.rename(target)
            except OSError:
                if not (target / ".complete").is_file():
                    raise
        finally:
            if stage.exists():
                shutil.rmtree(stage)
    if (target / ".complete").read_text(encoding="ascii") != digest:
        raise ValueError("Invalid cached S3 SDK.")
    return target


if __name__ == "__main__":
    try:
        path = install()
    except Exception as error:
        # Installer requests carry no credentials; still avoid dumping response bodies.
        message = f"CI S3 SDK setup failed ({type(error).__name__}); Python {sys.version_info.major}.{sys.version_info.minor}, requires 3.10+. Check cache permissions and access to files.pythonhosted.org."
        print(f"::error::{message}", file=sys.stderr)
        raise SystemExit(1) from None
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"site_packages={path}\n")
    print("CI S3 SDK is ready (code is not bundled in APKs).")
