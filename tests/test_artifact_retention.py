"""Protect the two newest downloadable artifacts when pruning Actions storage."""

from contextlib import redirect_stdout
import io
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import prune_artifacts as retention


def artifact(number, name="middleshare-core-debug", expired=False):
    return {
        "id": number,
        "name": name,
        "created_at": f"2026-10-{number:02}T00:00:00Z",
        "expired": expired,
        "size_in_bytes": 1024,
        "workflow_run": {"id": number},
    }


class ArtifactRetentionTests(unittest.TestCase):
    def test_reads_artifacts_from_every_page_with_compatible_gh_flags(self):
        result = SimpleNamespace(returncode=0, stdout='{"id":1}\n{"id":2}\n', stderr="")
        with patch.object(retention.subprocess, "run", return_value=result) as run:
            self.assertEqual(retention.items("endpoint", "artifacts"), [{"id": 1}, {"id": 2}])
        self.assertEqual(
            run.call_args.args[0],
            ["gh", "api", "--method", "GET", "endpoint", "--paginate", "--jq", ".artifacts[]"],
        )

    def test_keeps_two_newest_of_each_type_including_the_legacy_core_history(self):
        artifacts = [artifact(1, "middleshare-debug")]
        for name in ("middleshare-core-debug", "middleshare-video-debug", "release-apks"):
            artifacts.extend(artifact(number, name) for number in (4, 2, 3))
        remove = retention.older_artifacts(artifacts, set())
        self.assertEqual(
            {(a["name"], a["id"]) for a in remove},
            {("middleshare-debug", 1), ("middleshare-core-debug", 2),
             ("middleshare-video-debug", 2), ("release-apks", 2)},
        )

    def test_active_and_unrelated_artifacts_are_protected(self):
        artifacts = [artifact(number) for number in (1, 2, 3, 4)]
        artifacts.extend([artifact(1, "unrelated-data"), artifact(1, "release-apks")])
        orphan = artifact(1)
        orphan.pop("workflow_run")
        artifacts.append(orphan)
        self.assertEqual(
            [a["id"] for a in retention.older_artifacts(artifacts, {4})], [1],
        )

    def test_expired_artifacts_do_not_displace_downloadable_versions(self):
        artifacts = [artifact(3, expired=True), artifact(2), artifact(1)]
        self.assertEqual(
            [a["id"] for a in retention.older_artifacts(artifacts, set())], [3],
        )

    def test_creation_time_ties_use_artifact_id(self):
        artifacts = [artifact(number) for number in (1, 2, 3)]
        for row in artifacts:
            row["created_at"] = "2026-10-01T00:00:00Z"
        self.assertEqual(
            [a["id"] for a in retention.older_artifacts(artifacts, set())], [1],
        )

    def invoke(self, apply, run_status="completed"):
        def items(endpoint, key):
            return [artifact(number) for number in (1, 2, 3)] if key == "artifacts" else []

        with patch.object(retention, "items", side_effect=items):
            with patch.object(retention, "api", return_value={"status": run_status}) as api:
                with redirect_stdout(io.StringIO()):
                    args = ["--repo", "owner/repo"] + (["--apply"] if apply else [])
                    retention.main(args)
                return api.call_args_list

    def test_default_dry_run_does_not_delete_anything(self):
        self.assertEqual(self.invoke(apply=False), [])

    def test_apply_deletes_only_older_completed_artifacts(self):
        calls = self.invoke(apply=True)
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[1].args, ("repos/owner/repo/actions/artifacts/1",))
        self.assertEqual(calls[1].kwargs, {"method": "DELETE"})

    def test_a_run_restarted_after_the_snapshot_is_not_deleted(self):
        calls = self.invoke(apply=True, run_status="in_progress")
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0].args, ("repos/owner/repo/actions/runs/1",))
