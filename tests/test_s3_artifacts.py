"""Private uploads, safe retention, and download links without real S3 credentials."""

from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
from importlib.metadata import PackageNotFoundError, version
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import s3_artifacts as storage


ENV = {
    "GITHUB_REPOSITORY": "owner/repo",
    "GITHUB_RUN_ID": "10",
    "GITHUB_RUN_ATTEMPT": "2",
}
CONFIG = storage.settings(ENV)
CORE = "middleshare-core-debug"
VIDEO = "middleshare-video-debug"


def pinned_sdk_available():
    packages = json.loads((storage.ROOT / "scripts/s3-sdk-lock.json").read_text())[
        "packages"
    ]
    expected = next(
        package["version"] for package in packages if package["name"] == "boto3"
    )
    try:
        return version("boto3") == expected
    except PackageNotFoundError:
        return False


def build(run, artifact=CORE, complete=True, owner="owner/repo", attempt=1):
    prefix = f"ci-artifacts/{owner}/{artifact}/{run}/{attempt}/"
    names = ["app.apk", "manifest.json"] if complete else ["app.apk"]
    return [
        {
            "Key": prefix + name,
            "LastModified": datetime(2026, 10, run, tzinfo=timezone.utc),
        }
        for name in names
    ]


class Client:
    def __init__(self, objects=(), multipart=()):
        self.objects = list(objects)
        self.multipart = list(multipart)
        self.uploads, self.markers, self.deletes, self.aborts = [], [], [], []

    def upload_file(self, path, bucket, key, **kwargs):
        self.uploads.append((path, bucket, key, kwargs))

    def put_object(self, **kwargs):
        self.markers.append(kwargs)

    def generate_presigned_url(self, operation, Params, ExpiresIn):
        assert operation == "get_object"
        return f'https://s3.example.test/{Params["Bucket"]}/{Params["Key"]}?X-Amz-Signature=test-signature'

    def get_paginator(self, operation):
        items = self.objects if operation == "list_objects_v2" else self.multipart
        key = "Contents" if operation == "list_objects_v2" else "Uploads"

        class Pages:
            def paginate(self, **kwargs):
                # Two pages exercise traversal rather than only the first response.
                return [{key: items[:1]}, {key: items[1:]}]

        return Pages()

    def delete_objects(self, **kwargs):
        self.deletes.extend(item["Key"] for item in kwargs["Delete"]["Objects"])
        return {}

    def abort_multipart_upload(self, **kwargs):
        self.aborts.append(kwargs)

    def list_objects_v2(self, **kwargs):
        return {}

    def list_multipart_uploads(self, **kwargs):
        return {}

    def delete_object(self, **kwargs):
        self.deletes.append(kwargs["Key"])


class S3ArtifactTests(unittest.TestCase):
    def test_build_outputs_are_not_uploaded_when_the_private_storage_check_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            env = dict(ENV, GITHUB_STEP_SUMMARY=str(Path(directory) / "summary"))
            with patch.dict(os.environ, env):
                with patch.object(sys, "argv", ["s3_artifacts.py", "upload", CORE]):
                    with patch.object(storage, "client_for", return_value=Client()):
                        with patch.object(
                            storage, "transfer_config", return_value=None
                        ):
                            with patch.object(
                                storage,
                                "probe",
                                side_effect=storage.ArtifactError("public bucket"),
                            ):
                                with patch.object(storage, "upload") as upload:
                                    with self.assertRaises(ValueError):
                                        storage.main()
                                    upload.assert_not_called()

    def test_https_and_project_scope_are_required(self):
        for values in (
            dict(ENV, S3_ENDPOINT="http://s3.example.test"),
            dict(ENV, S3_ENDPOINT="https://user:password@s3.example.test"),
            dict(ENV, GITHUB_REPOSITORY="../other"),
            dict(ENV, S3_LINK_EXPIRY="604801"),
        ):
            with self.subTest(values=values), self.assertRaises(ValueError):
                storage.settings(values)

    def test_uploads_only_expected_outputs_and_commits_a_checksum_manifest_last(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "app/build/outputs/apk/core/debug"
            output.mkdir(parents=True)
            (output / "app-core-universal-debug.apk").write_bytes(b"APK")
            (output / "app-core-arm64-debug.apk").write_bytes(b"excluded split")
            (output / "private.jks").write_bytes(b"never upload")
            client = Client()
            prefix, files = storage.upload(client, CONFIG, CORE, ENV, root=root)
            self.assertEqual(prefix, f"ci-artifacts/owner/repo/{CORE}/10/2/")
            self.assertEqual(len(client.uploads), 1)
            manifest = json.loads(client.markers[0]["Body"])
            self.assertEqual(
                manifest["files"][0]["sha256"], hashlib.sha256(b"APK").hexdigest()
            )
            self.assertEqual(files[0]["name"], "app-core-universal-debug.apk")
            self.assertNotIn("ACL", client.uploads[0][3]["ExtraArgs"])
            summary = root / "summary.md"
            with redirect_stdout(io.StringIO()) as logs:
                storage.append_summary(client, CONFIG, CORE, prefix, files, summary)
            self.assertNotIn("test-signature", logs.getvalue())
            self.assertIn("test-signature", summary.read_text())
            self.assertIn("24 hours", summary.read_text())

    def test_failed_payload_upload_has_no_completion_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "app/build/outputs/apk/video/debug"
            output.mkdir(parents=True)
            (output / "video.apk").write_bytes(b"APK")
            client = Client()

            def failed(*args, **kwargs):
                raise OSError("simulated upload failure")

            client.upload_file = failed
            with self.assertRaises(OSError):
                storage.upload(client, CONFIG, VIDEO, ENV, root=root)
            self.assertEqual(client.markers, [])

    def test_retention_keeps_two_per_type_and_protects_active_and_foreign_objects(self):
        objects = [
            obj
            for kind in (CORE, VIDEO)
            for run in (1, 2, 3)
            for obj in build(run, kind)
        ]
        objects.extend(build(4))
        objects.extend(build(5, complete=False))
        objects.extend(build(1, owner="other/repo"))
        objects.append(
            {
                "Key": "ci-artifacts/owner/repo/unrelated/1/1/keep",
                "LastModified": datetime.now(timezone.utc),
            }
        )
        candidates = storage.deletion_candidates(CONFIG, objects, {4})
        self.assertEqual(
            {identity for identity, files in candidates},
            {(CORE, 1, 1), (VIDEO, 1, 1), (CORE, 5, 1)},
        )

    def test_restarted_runs_and_unrelated_multipart_uploads_are_not_deleted(self):
        objects = [obj for run in (1, 2, 3) for obj in build(run)]
        objects.extend(build(6, complete=False))
        multipart = [
            {"Key": build(7)[0]["Key"], "UploadId": "stale"},
            {"Key": build(8)[0]["Key"], "UploadId": "active"},
            {"Key": build(1, owner="other/repo")[0]["Key"], "UploadId": "foreign"},
        ]
        client = Client(objects, multipart)
        with redirect_stdout(io.StringIO()):
            storage.prune(
                client,
                CONFIG,
                {8},
                lambda run: "in_progress" if run == 6 else "completed",
            )
        self.assertEqual(set(client.deletes), {obj["Key"] for obj in build(1)})
        self.assertEqual([item["UploadId"] for item in client.aborts], ["stale"])

    def test_malformed_keys_never_enter_the_retention_scope(self):
        for key in (
            "ci-artifacts/other/repo/middleshare-core-debug/1/1/file.apk",
            "ci-artifacts/owner/repo/middleshare-core-debug/../1/file.apk",
            "ci-artifacts/owner/repo/middleshare-core-debug/1/1/../file.apk",
            "ci-artifacts/owner/repo/.probe/1/check.txt",
        ):
            self.assertIsNone(storage.owned_key(CONFIG, key))

    def test_refresh_links_selects_only_the_latest_two_completed_manifests(self):
        client = Client([obj for run in (1, 2, 3) for obj in build(run)])

        def read_manifest(**kwargs):
            body = {
                "repository": "owner/repo",
                "artifact": CORE,
                "files": [{"name": "app.apk"}],
            }
            return {"Body": io.BytesIO(json.dumps(body).encode())}

        client.get_object = read_manifest
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            with redirect_stdout(io.StringIO()):
                storage.refresh_links(client, CONFIG, summary)
            text = summary.read_text()
            self.assertIn(f"/{CORE}/3/1/app.apk", text)
            self.assertIn(f"/{CORE}/2/1/app.apk", text)
            self.assertNotIn(f"/{CORE}/1/1/app.apk", text)

    def test_probe_checks_private_access_and_always_removes_its_test_object(self):
        for public in (False, True):
            with self.subTest(
                public=public
            ), tempfile.TemporaryDirectory() as directory:
                client = Client()
                replies = [io.BytesIO(b"\0" * (9 * 1024**2))]
                replies.append(
                    io.BytesIO(b"public")
                    if public
                    else HTTPError("https://example.test", 403, "Denied", {}, None)
                )
                with patch.object(storage, "transfer_config", return_value=None):
                    with patch.object(
                        storage.urllib.request, "urlopen", side_effect=replies
                    ):
                        with redirect_stdout(io.StringIO()):
                            if public:
                                with self.assertRaises(ValueError):
                                    storage.probe(
                                        client, CONFIG, ENV, Path(directory) / "summary"
                                    )
                            else:
                                storage.probe(
                                    client, CONFIG, ENV, Path(directory) / "summary"
                                )
                self.assertEqual(
                    client.deletes, ["ci-artifacts/owner/repo/.probe/10/check.bin"]
                )

    @unittest.skipUnless(
        pinned_sdk_available(), "CI-only SDK is tested after bootstrap"
    )
    def test_sdk_uses_explicit_credentials_path_style_and_seaweed_compatible_checksums(
        self,
    ):
        # Do not consult the developer machine's unrelated AWS profiles/identities.
        with patch("boto3.client") as factory:
            storage.client_for(
                CONFIG,
                {
                    "S3_ACCESS_KEY_ID": "test-access",
                    "S3_SECRET_ACCESS_KEY": "test-secret",
                },
            )
        arguments = factory.call_args.kwargs
        self.assertEqual(arguments["endpoint_url"], "https://bucket.s3.jczr.dev")
        self.assertEqual(arguments["aws_access_key_id"], "test-access")
        self.assertEqual(arguments["config"].signature_version, "s3v4")
        self.assertEqual(arguments["config"].s3["addressing_style"], "path")
        self.assertEqual(
            arguments["config"].request_checksum_calculation, "when_required"
        )
        self.assertEqual(storage.transfer_config().multipart_threshold, 8 * 1024**2)
