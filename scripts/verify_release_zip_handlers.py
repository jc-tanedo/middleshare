#!/usr/bin/env python3
"""Check the actual release DEX for the ZIP constructors used through reflection."""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


def registered_handlers(compress_jar):
    # Read the registry from the resolved dependency rather than maintaining a second list.
    bytecode = subprocess.run(
        [
            "javap", "-classpath", str(compress_jar), "-c",
            "org.apache.commons.compress.archivers.zip.ExtraFieldUtils",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    handlers = re.findall(
        r"// class (\S+)\s+\d+: invokestatic\s+[^\n]+// Method register:",
        bytecode,
    )
    calls = len(re.findall(r"// Method register:\(Ljava/lang/Class;\)V", bytecode))
    if not handlers or len(handlers) != calls:
        raise ValueError("Could not identify Commons Compress's reflective ZIP registry.")
    return {name.replace("/", ".") for name in handlers}


def verify(apk, mapping, compress_jar, dexdump):
    names = dict(re.findall(
        r"^(\S+) -> (\S+):$", mapping.read_text(encoding="utf-8"), re.MULTILINE,
    ))
    classes = {}
    with tempfile.TemporaryDirectory(prefix="middleshare-dex-") as directory:
        with zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                if not re.fullmatch(r"classes(?:\d+)?\.dex", name):
                    continue
                dex = Path(directory) / name
                dex.write_bytes(archive.read(name))
                output = subprocess.run(
                    [str(dexdump), "-l", "xml", str(dex)],
                    check=True,
                    capture_output=True,
                    text=True,
                ).stdout
                for package in ET.fromstring(output).findall("package"):
                    for cls in package.findall("class"):
                        full_name = ".".join(filter(
                            None, (package.get("name"), cls.get("name")),
                        ))
                        classes[full_name] = cls

    handlers = registered_handlers(compress_jar)
    for name in sorted(handlers):
        cls = classes.get(names.get(name, name))
        if cls is None or cls.get("abstract") != "false" or cls.get("interface") != "false":
            raise ValueError(f"Reflective ZIP handler is missing or abstract in {apk.name}: {name}")
        constructors = cls.findall("constructor")
        if not any(
            method.get("visibility") == "public"
            and method.get("static") == "false"
            and not method.findall("parameter")
            for method in constructors
        ):
            raise ValueError(f"Reflective ZIP handler has no public no-arg constructor: {name}")
    print(f"Verified {len(handlers)} reflective ZIP handlers in {apk.name}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("apk", "mapping", "compress-jar", "dexdump"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    verify(args.apk, args.mapping, args.compress_jar, args.dexdump)


if __name__ == "__main__":
    main()
