#!/usr/bin/env python3
"""Prepare the optional, separate CI debug identity without touching release secrets."""

import os
from pathlib import Path

from signing import write_keystore


def prepare():
    encoded = os.environ.get("ANDROID_DEBUG_KEYSTORE_BASE64", "")
    password = os.environ.get("ANDROID_DEBUG_KEYSTORE_PASSWORD", "")
    if not encoded and not password:
        print(
            "Using a temporary debug key. Configure the debug signing secrets "
            "for updateable CI APKs."
        )
        return
    if not encoded or not password:
        raise ValueError("Configure both debug signing secrets; see RELEASING.md.")
    path = Path(os.environ["RUNNER_TEMP"]) / "middleshare-debug.jks"
    write_keystore(encoded, path)
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env:
        env.write(f"ANDROID_DEBUG_KEYSTORE_PATH={path}\n")


if __name__ == "__main__":
    try:
        prepare()
    except (ValueError, OSError, KeyError):
        raise SystemExit(
            "Could not prepare debug signing. Check both debug secrets and RELEASING.md."
        ) from None
