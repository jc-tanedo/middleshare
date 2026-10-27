#!/usr/bin/env python3
"""Validate version tags and stage only verified, consistently signed release APKs."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from signing import write_keystore

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_ABIS = {"universal", "arm64-v8a", "armeabi-v7a", "x86_64"}
DEBUG_CERTIFICATE_NAMES = ("CN=Android Debug", "CN=MiddleShare Debug")


def version():
    tag = os.environ.get("RELEASE_TAG", "")
    source = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    expected = re.search(r"versionName '([^']+)'", source).group(1)
    if (
        not re.fullmatch(r"v\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.[1-9]\d*)?", tag)
        or tag != "v" + expected
    ):
        raise ValueError(f"Tag must equal v{expected}, matching app/build.gradle versionName.")
    return expected


def signing():
    names = (
        "ANDROID_KEYSTORE_BASE64",
        "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD",
    )
    if any(not os.environ.get(name) for name in names):
        raise ValueError(
            "Configure all four Android signing repository secrets; "
            "see RELEASING.md. No release was published."
        )
    target = Path(os.environ["RUNNER_TEMP"]) / "middleshare-release.jks"
    write_keystore(os.environ[names[0]], target, overwrite=True)


def certificate_fingerprint(verified):
    if any(name in verified for name in DEBUG_CERTIFICATE_NAMES):
        raise ValueError("Refusing to publish APKs signed with a debug key.")
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})\b", verified)
    if not match:
        raise ValueError("APK verification did not report a SHA-256 signing certificate.")
    return match.group(1).lower()


def collect():
    ver = version()
    apksigner = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0/apksigner"
    destination = ROOT / "release-assets"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir()
    certificate = None
    for flavor in ("core", "video"):
        directory = ROOT / "app/build/outputs/apk" / flavor / "release"
        metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
        expected_id = "dev.middleshare" + (".video" if flavor == "video" else "")
        if metadata["applicationId"] != expected_id:
            raise ValueError(f"Unexpected package identity: {flavor}")
        selected = set()
        for entry in metadata["elements"]:
            abi = next(
                (item["value"] for item in entry["filters"] if item["filterType"] == "ABI"),
                "universal",
            )
            if flavor == "core" and abi != "universal":
                continue
            if abi in selected or entry["versionName"] != ver:
                raise ValueError(f"Duplicate ABI or wrong version: {flavor}/{abi}")
            selected.add(abi)
            apk = directory / entry["outputFile"]
            verified = subprocess.run(
                [str(apksigner), "verify", "--print-certs", str(apk)],
                check=True,
                capture_output=True,
                text=True,
            ).stdout
            fingerprint = certificate_fingerprint(verified)
            if certificate is not None and fingerprint != certificate:
                raise ValueError("Main app and video add-on have different signing certificates.")
            certificate = fingerprint
            name = (
                f"middleshare-{ver}.apk"
                if flavor == "core"
                else f"middleshare-video-{ver}-{abi}.apk"
            )
            shutil.copyfile(apk, destination / name)
        expected = {"universal"} if flavor == "core" else EXPECTED_ABIS
        if selected != expected:
            raise ValueError(f"Missing/unexpected release APKs: {flavor}: {selected}")
    lines = [
        hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name
        for path in sorted(destination.glob("*.apk"))
    ]
    (destination / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Verified and staged {len(lines)} signed APKs.")


def publish():
    ver = version()
    directory = ROOT / "release-assets"
    apks = sorted(directory.glob("*.apk"))
    checksums = directory / "SHA256SUMS"
    if len(apks) != len(EXPECTED_ABIS) + 1 or not checksums.is_file():
        raise ValueError("Collect the five verified APKs and checksums before publishing.")
    command = [
        "gh", "release", "create", "v" + ver,
        *(str(path) for path in apks), str(checksums),
        "--verify-tag", "--title", "MiddleShare v" + ver,
        "--generate-notes", "--notes-file", str(ROOT / ".github/release-notes.md"),
    ]
    if "-" in ver:
        command.append("--prerelease")
    subprocess.run(command, check=True, cwd=ROOT)


if __name__ == "__main__":
    commands = {"validate": version, "signing": signing, "collect": collect, "publish": publish}
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=commands)
    args = parser.parse_args()
    try:
        commands[args.command]()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
