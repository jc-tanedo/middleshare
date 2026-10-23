#!/usr/bin/env python3
"""Validate version tags and stage only verified, consistently signed release APKs."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def version():
    tag = os.environ.get('RELEASE_TAG', '')
    expected = re.search(r"versionName '([^']+)'", (ROOT / 'app/build.gradle').read_text()).group(1)
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag) or tag != 'v' + expected:
        raise ValueError(f'Tag must equal v{expected}, matching app/build.gradle versionName.')
    return expected


def signing():
    names = ['ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD']
    if any(not os.environ.get(name) for name in names):
        raise ValueError('Configure all four Android signing repository secrets; see RELEASING.md. No release was published.')
    target = Path(os.environ['RUNNER_TEMP']) / 'middleshare-release.jks'
    with open(target, 'wb', opener=lambda path, flags: os.open(path, flags, 0o600)) as output:
        output.write(base64.b64decode(''.join(os.environ[names[0]].split()), validate=True))


def collect():
    ver = version()
    apksigner = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
    destination = ROOT / 'release-assets'
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir()
    certificate = None
    for flavor in ['core', 'video']:
        directory = ROOT / 'app/build/outputs/apk' / flavor / 'release'
        metadata = json.loads((directory / 'output-metadata.json').read_text())
        expected_id = 'dev.middleshare' + ('.video' if flavor == 'video' else '')
        if metadata['applicationId'] != expected_id:
            raise ValueError(f'Unexpected package identity: {flavor}')
        selected = set()
        for entry in metadata['elements']:
            abi = next((f['value'] for f in entry['filters'] if f['filterType'] == 'ABI'), 'universal')
            if flavor == 'core' and abi != 'universal':
                continue
            if abi in selected or entry['versionName'] != ver:
                raise ValueError(f'Duplicate ABI or wrong version: {flavor}/{abi}')
            selected.add(abi)
            apk = directory / entry['outputFile']
            verified = subprocess.run([str(apksigner), 'verify', '--print-certs', str(apk)],
                                      check=True, capture_output=True, text=True).stdout
            if 'CN=Android Debug' in verified:
                raise ValueError('Refusing to publish APKs signed with the public debug key.')
            fingerprint = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', verified).group(1)
            if certificate is not None and fingerprint != certificate:
                raise ValueError('Main app and video add-on have different signing certificates.')
            certificate = fingerprint
            name = f'middleshare-{ver}.apk' if flavor == 'core' else f'middleshare-video-{ver}-{abi}.apk'
            shutil.copyfile(apk, destination / name)
        expected = {'universal'} if flavor == 'core' else {'universal', 'arm64-v8a', 'armeabi-v7a', 'x86_64'}
        if selected != expected:
            raise ValueError(f'Missing/unexpected release APKs: {flavor}: {selected}')
    lines = [hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name for p in sorted(destination.glob('*.apk'))]
    (destination / 'SHA256SUMS').write_text('\n'.join(lines) + '\n')
    print(f'Verified and staged {len(lines)} signed APKs.')


if __name__ == '__main__':
    try:
        {'validate': version, 'signing': signing, 'collect': collect}[sys.argv[1]]()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
