#!/usr/bin/env python3
"""Keep the two newest completed versions of each MiddleShare Actions artifact."""

import argparse
from collections import defaultdict
import json
import os
import re
import subprocess


# The old combined APK belongs to the core history, not a permanently retained category.
GROUPS = {
    "middleshare-debug": "core",
    "middleshare-core-debug": "core",
    "middleshare-video-debug": "video",
    "release-apks": "release",
}


def api(endpoint, method="GET", collection=None):
    command = ["gh", "api", "--method", method, endpoint]
    if collection:
        command.extend(["--paginate", "--jq", f".{collection}[]"])
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"GitHub API request failed: {method} {endpoint}: {result.stderr.strip()}")
    output = result.stdout
    if collection:
        return [json.loads(line) for line in output.splitlines() if line.strip()]
    return json.loads(output) if output.strip() else None


def items(endpoint, key):
    return api(endpoint, collection=key)


def older_artifacts(artifacts, active_runs):
    groups = defaultdict(list)
    for artifact in artifacts:
        run_id = artifact.get("workflow_run", {}).get("id")
        if artifact["name"] not in GROUPS or run_id is None or run_id in active_runs:
            continue
        groups[GROUPS[artifact["name"]]].append(artifact)

    remove = []
    for group in groups.values():
        kept = 0
        for artifact in sorted(group, key=lambda a: (a["created_at"], a["id"]), reverse=True):
            if artifact["expired"] or kept >= 2:
                remove.append(artifact)
            else:
                kept += 1
    return remove


def active_run_ids(repo):
    return {
        run["id"]
        for status in ("in_progress", "queued", "waiting", "pending", "requested")
        for run in items(f"repos/{repo}/actions/runs?status={status}&per_page=100", "workflow_runs")
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GH_REPO"))
    parser.add_argument("--apply", action="store_true", help="Delete older artifacts; default is dry-run.")
    args = parser.parse_args(argv)
    if not args.repo or not re.fullmatch(r"[\w.-]+/[\w.-]+", args.repo):
        parser.error("Set GH_REPO or pass --repo OWNER/REPO.")

    base = f"repos/{args.repo}/actions"
    artifacts = items(f"{base}/artifacts?per_page=100", "artifacts")
    active_runs = active_run_ids(args.repo)
    candidates = older_artifacts(artifacts, active_runs)
    statuses = {}
    deleted_bytes = 0
    deleted = 0
    for artifact in candidates:
        size = artifact["size_in_bytes"]
        label = f'{artifact["id"]} {artifact["name"]} ({size / 1024**2:.1f} MiB)'
        if not args.apply:
            print("Would delete", label)
            continue
        # A completed run may have been rerun since the initial snapshot. Protect it too.
        run_id = artifact["workflow_run"]["id"]
        if run_id not in statuses:
            statuses[run_id] = api(f"{base}/runs/{run_id}")["status"]
        if statuses[run_id] != "completed":
            print("Skipping active run's artifact", label)
            continue
        api(f'{base}/artifacts/{artifact["id"]}', method="DELETE")
        deleted += 1
        deleted_bytes += size
        print("Deleted", label, flush=True)

    if args.apply:
        print(f"Deleted {deleted} artifacts ({deleted_bytes / 1024**3:.2f} GiB).")
    else:
        size = sum(artifact["size_in_bytes"] for artifact in candidates)
        print(f"Dry-run: {len(candidates)} older artifacts ({size / 1024**3:.2f} GiB).")


if __name__ == "__main__":
    main()
