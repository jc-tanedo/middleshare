"""Shared, private-file handling for the separate release and debug keystores."""

import base64
import os


def write_keystore(encoded, target, *, overwrite=False):
    """Validate Base64 before opening the target; never log signing material."""
    payload = base64.b64decode("".join(encoded.split()), validate=True)
    flags = os.O_WRONLY | os.O_CREAT | (os.O_TRUNC if overwrite else os.O_EXCL)
    with os.fdopen(os.open(target, flags, 0o600), "wb") as output:
        os.fchmod(output.fileno(), 0o600)
        output.write(payload)
