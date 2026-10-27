#!/usr/bin/env python3
"""Upload private CI artifacts to S3 and retain two completed builds per type."""

import argparse
from collections import defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib.parse import urlsplit
from urllib.error import HTTPError
import urllib.request

from prune_artifacts import active_run_ids, api


class ArtifactError(ValueError):
    """A safe, user-facing validation failure without SDK request details."""


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = {
    "middleshare-core-debug": ("app/build/outputs/apk/core/debug/*universal*.apk",),
    "middleshare-video-debug": ("app/build/outputs/apk/video/debug/*.apk",),
    "release-apks": ("release-assets/*.apk", "release-assets/SHA256SUMS"),
}


def settings(env):
    endpoint = env.get("S3_ENDPOINT", "https://bucket.s3.jczr.dev").rstrip("/")
    parsed = urlsplit(endpoint)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username
        or parsed.query
        or parsed.fragment
    ):
        raise ArtifactError(
            "Use an HTTPS S3 endpoint without credentials or query parameters."
        )
    repo = env.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo) or any(
        p in (".", "..") for p in repo.split("/")
    ):
        raise ArtifactError("Set GITHUB_REPOSITORY to OWNER/REPO.")
    bucket = env.get("S3_BUCKET", "gh-artifact-store")
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket):
        raise ArtifactError("Invalid S3 bucket name.")
    expiry = int(env.get("S3_LINK_EXPIRY", "86400"))
    if not 60 <= expiry <= 604800:
        raise ArtifactError(
            "Download link expiry must be between 60 seconds and seven days."
        )
    addressing_style = env.get("S3_ADDRESSING_STYLE", "path")
    if addressing_style not in ("path", "virtual"):
        raise ArtifactError("S3 addressing style must be path or virtual.")
    return {
        "endpoint": endpoint,
        "bucket": bucket,
        "region": env.get("S3_REGION", "us-east-1"),
        "prefix": f"ci-artifacts/{repo}/",
        "repo": repo,
        "expiry": expiry,
        "addressing_style": addressing_style,
    }


def client_for(config, env):
    import boto3
    from botocore.config import Config

    if not env.get("S3_ACCESS_KEY_ID") or not env.get("S3_SECRET_ACCESS_KEY"):
        raise ArtifactError(
            "Configure the S3_ACCESS_KEY_ID variable and S3_SECRET_ACCESS_KEY secret."
        )
    return boto3.client(
        "s3",
        endpoint_url=config["endpoint"],
        region_name=config["region"],
        aws_access_key_id=env["S3_ACCESS_KEY_ID"],
        aws_secret_access_key=env["S3_SECRET_ACCESS_KEY"],
        config=Config(
            signature_version="s3v4",
            s3={"addressing_style": config["addressing_style"]},
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
            connect_timeout=15,
            read_timeout=120,
            retries={"mode": "standard", "max_attempts": 3},
        ),
    )


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def upload(client, config, artifact, env, transfer=None, root=ROOT):
    run, attempt = env.get("GITHUB_RUN_ID", ""), env.get("GITHUB_RUN_ATTEMPT", "1")
    if not re.fullmatch(r"[1-9]\d*", run) or not re.fullmatch(r"[1-9]\d*", attempt):
        raise ArtifactError(
            "A valid GitHub run ID and attempt are required for uploads."
        )
    files = []
    for pattern in ARTIFACTS[artifact]:
        matches = sorted(path for path in root.glob(pattern) if path.is_file())
        if not matches:
            raise ArtifactError(f"No build outputs found for {artifact}.")
        files.extend(matches)
    if len({path.name for path in files}) != len(files):
        raise ArtifactError("Artifact file names must be unique.")
    prefix = f'{config["prefix"]}{artifact}/{run}/{attempt}/'
    records = []
    for path in files:
        checksum = digest(path)
        content_type = (
            "application/vnd.android.package-archive"
            if path.suffix == ".apk"
            else "text/plain"
        )
        client.upload_file(
            str(path),
            config["bucket"],
            prefix + path.name,
            Config=transfer,
            ExtraArgs={
                "Metadata": {"sha256": checksum},
                "ContentType": content_type,
                "CacheControl": "private, no-store",
            },
        )
        records.append(
            {"name": path.name, "size": path.stat().st_size, "sha256": checksum}
        )
    manifest = {
        "schema": 1,
        "repository": config["repo"],
        "artifact": artifact,
        "run_id": run,
        "attempt": attempt,
        "commit": env.get("GITHUB_SHA", ""),
        "created_at": datetime.now(timezone.utc).isoformat(),
        "files": records,
    }
    # Publish the completion marker only after every payload has been uploaded.
    client.put_object(
        Bucket=config["bucket"],
        Key=prefix + "manifest.json",
        Body=(json.dumps(manifest, indent=2) + "\n").encode(),
        ContentType="application/json",
        CacheControl="private, no-store",
    )
    return prefix, records


def append_summary(client, config, artifact, prefix, files, summary):
    identity = owned_key(config, prefix + "manifest.json")
    if identity is None:
        raise ArtifactError(
            "Cannot link an artifact outside this repository's storage prefix."
        )
    lines = [
        f"\n### {artifact}\n",
        f'[Run {identity[1]}, attempt {identity[2]}](https://github.com/{config["repo"]}/actions/runs/{identity[1]})\n',
        f'Private downloads; links expire after {config["expiry"] // 3600} hours.\n',
    ]
    for file in [*files, {"name": "manifest.json"}]:
        url = client.generate_presigned_url(
            "get_object",
            Params={"Bucket": config["bucket"], "Key": prefix + file["name"]},
            ExpiresIn=config["expiry"],
        )
        # URLs are bearer credentials: write only to the requested job summary, never logs.
        lines.append(f'- [{file["name"]}]({url})\n')
    lines.append(
        "\nThe manifest includes file sizes and SHA-256 checksums. Older builds may be removed by retention.\n"
    )
    with Path(summary).open("a", encoding="utf-8") as output:
        output.write("\n".join(lines))


def owned_key(config, key):
    groups = "|".join(re.escape(name) for name in ARTIFACTS)
    match = re.fullmatch(
        re.escape(config["prefix"]) + rf"({groups})/([1-9]\d*)/([1-9]\d*)/([^/]+)",
        key,
    )
    if not match or match[4] in (".", ".."):
        return None
    return match[1], int(match[2]), int(match[3])


def deletion_candidates(config, objects, active):
    builds = defaultdict(list)
    for obj in objects:
        identity = owned_key(config, obj["Key"])
        if identity is not None and identity[1] not in active:
            builds[identity].append(obj)
    completed = defaultdict(list)
    remove = []
    for identity, files in builds.items():
        marker = next(
            (file for file in files if file["Key"].endswith("/manifest.json")), None
        )
        if marker is None:
            remove.append((identity, files))
        else:
            completed[identity[0]].append((marker["LastModified"], identity, files))
    for group in completed.values():
        group.sort(key=lambda row: (row[0], row[1][1:]), reverse=True)
        remove.extend((identity, files) for _, identity, files in group[2:])
    return remove


def prune(client, config, active, run_status):
    objects = [
        obj
        for page in client.get_paginator("list_objects_v2").paginate(
            Bucket=config["bucket"],
            Prefix=config["prefix"],
        )
        for obj in page.get("Contents", [])
    ]
    removed = 0
    for identity, files in deletion_candidates(config, objects, active):
        # Recheck for reruns that started after listing active workflows.
        if run_status(identity[1]) != "completed":
            continue
        for offset in range(0, len(files), 1000):
            result = client.delete_objects(
                Bucket=config["bucket"],
                Delete={
                    "Objects": [
                        {"Key": file["Key"]} for file in files[offset : offset + 1000]
                    ]
                },
            )
            if result.get("Errors"):
                raise RuntimeError("S3 could not delete all older artifact objects.")
            removed += len(files[offset : offset + 1000])
    # Clean up multipart data left by an interrupted upload, within our own prefix only.
    for page in client.get_paginator("list_multipart_uploads").paginate(
        Bucket=config["bucket"],
        Prefix=config["prefix"],
    ):
        for upload in page.get("Uploads", []):
            identity = owned_key(config, upload["Key"])
            if (
                identity
                and identity[1] not in active
                and run_status(identity[1]) == "completed"
            ):
                client.abort_multipart_upload(
                    Bucket=config["bucket"],
                    Key=upload["Key"],
                    UploadId=upload["UploadId"],
                )
    print(
        f"Removed {removed} older/incomplete S3 artifact objects; newest two builds per type retained."
    )


def refresh_links(client, config, summary):
    groups = defaultdict(list)
    for page in client.get_paginator("list_objects_v2").paginate(
        Bucket=config["bucket"],
        Prefix=config["prefix"],
    ):
        for obj in page.get("Contents", []):
            identity = owned_key(config, obj["Key"])
            if identity and obj["Key"].endswith("/manifest.json"):
                groups[identity[0]].append(obj)
    count = 0
    for artifact, markers in sorted(groups.items()):
        for marker in sorted(
            markers, key=lambda row: (row["LastModified"], row["Key"]), reverse=True
        )[:2]:
            response = client.get_object(Bucket=config["bucket"], Key=marker["Key"])
            with response["Body"] as stream:
                payload = stream.read(1024 * 1024 + 1)
            if len(payload) > 1024 * 1024:
                raise ArtifactError("Artifact manifest is too large.")
            manifest = json.loads(payload)
            if (
                manifest.get("repository") != config["repo"]
                or manifest.get("artifact") != artifact
            ):
                raise ArtifactError("Artifact manifest ownership does not match.")
            files = manifest["files"]
            if not all(
                re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,200}", file["name"])
                for file in files
            ):
                raise ArtifactError("Invalid file name in artifact manifest.")
            append_summary(
                client,
                config,
                artifact,
                marker["Key"].removesuffix("manifest.json"),
                files,
                summary,
            )
            count += 1
    print(f"Refreshed download links for {count} retained artifact builds.")


def transfer_config():
    from boto3.s3.transfer import TransferConfig

    return TransferConfig(
        multipart_threshold=8 * 1024**2,
        multipart_chunksize=8 * 1024**2,
        max_concurrency=3,
        preferred_transfer_client="classic",
    )


def probe(client, config, env, summary):
    run = env.get("GITHUB_RUN_ID", "")
    if not re.fullmatch(r"[1-9]\d*", run):
        raise ArtifactError("A GitHub run ID is required for the storage check.")
    key = f'{config["prefix"]}.probe/{run}/check.bin'
    try:
        client.list_objects_v2(
            Bucket=config["bucket"], Prefix=config["prefix"], MaxKeys=1
        )
        client.list_multipart_uploads(
            Bucket=config["bucket"], Prefix=config["prefix"], MaxUploads=1
        )
        with tempfile.TemporaryDirectory(prefix="s3-probe-") as directory:
            payload = Path(directory) / "check.bin"
            payload.write_bytes(b"\0" * (9 * 1024**2))
            expected = digest(payload)
            client.upload_file(
                str(payload),
                config["bucket"],
                key,
                Config=transfer_config(),
                ExtraArgs={"CacheControl": "private, no-store"},
            )
        url = client.generate_presigned_url(
            "get_object",
            Params={"Bucket": config["bucket"], "Key": key},
            ExpiresIn=300,
        )
        with urllib.request.urlopen(url, timeout=30) as response:
            actual = hashlib.sha256()
            for block in iter(lambda: response.read(1024 * 1024), b""):
                actual.update(block)
            if actual.hexdigest() != expected:
                raise ArtifactError("Storage check returned unexpected content.")
        try:
            with urllib.request.urlopen(url.split("?", 1)[0], timeout=30):
                raise ArtifactError(
                    "The artifact bucket permits anonymous reads; configure private access first."
                )
        except HTTPError as error:
            if error.code not in (401, 403, 404):
                raise
    finally:
        client.delete_object(Bucket=config["bucket"], Key=key)
    with Path(summary).open("a", encoding="utf-8") as output:
        output.write(
            "\n### Private artifact storage verified\n\nS3 listing, multipart upload, presigned download checksum, anonymous-access denial, and deletion passed.\n"
        )
    print("Private S3 storage check passed; test object removed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("upload", "prune", "links", "probe"))
    parser.add_argument("artifacts", nargs="*")
    args = parser.parse_args()
    if any(name not in ARTIFACTS for name in args.artifacts):
        parser.error("Unknown artifact type.")
    config = settings(os.environ)
    client = client_for(config, os.environ)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if args.command != "prune" and not summary:
        raise ArtifactError(
            "GITHUB_STEP_SUMMARY is required for private download links."
        )
    if args.command == "upload":
        if not args.artifacts:
            parser.error("Select at least one artifact type.")
        transfer = transfer_config()
        # Validate the gateway and private access before publishing real build outputs.
        probe(client, config, os.environ, summary)
        for artifact in args.artifacts:
            prefix, files = upload(client, config, artifact, os.environ, transfer)
            append_summary(client, config, artifact, prefix, files, summary)
            print(f"Uploaded {artifact}: {len(files)} files.")
    elif args.command == "links":
        refresh_links(client, config, summary)
    elif args.command == "probe":
        probe(client, config, os.environ, summary)
    else:
        statuses = {}

        def status(run):
            if run not in statuses:
                statuses[run] = api(f'repos/{config["repo"]}/actions/runs/{run}')[
                    "status"
                ]
            return statuses[run]

        prune(client, config, active_run_ids(config["repo"]), status)


if __name__ == "__main__":
    try:
        main()
    except ArtifactError as error:
        print(f"Artifact storage: {error}", file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        # SDK exceptions can contain request details. Never print signed URLs or credentials.
        response = getattr(error, "response", None)
        code = type(error).__name__
        if isinstance(response, dict):
            code = response.get("Error", {}).get("Code", code)
        if isinstance(error, HTTPError):
            code = f"HTTP{error.code}"
        if not re.fullmatch(r"[\w.-]{1,80}", str(code)):
            code = "S3Error"
        print(
            f"Artifact storage failed ({code}). Check endpoint, bucket permissions, and runner connectivity.",
            file=sys.stderr,
        )
        sys.exit(1)
