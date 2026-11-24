#!/usr/bin/env python3
"""Advance version.properties after a formal release has been published.

Input file uses key=value lines (comments are preserved). The formal APK for
release N is `N * 100000 + 99999`; the next alpha line must therefore use
`(N + 1) * 100000 + run`. This script performs the manual bookkeeping step that
used to be easy to forget:

  versionName=1.4.1, releaseNumber=101, lastPublishedReleaseNumber=100
  -> versionName=1.4.2, releaseNumber=102, lastPublishedReleaseNumber=101

Only the patch component is bumped. Maintainers can adjust a minor/major bump
in the generated pull request.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

KEYS = ("versionName", "releaseNumber", "lastPublishedReleaseNumber")
VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", default="version.properties")
    parser.add_argument("--released-release", type=int, help="releaseNumber that was just published")
    parser.add_argument("--json", action="store_true", help="emit a single JSON object")
    return parser.parse_args()


def read_properties(path: Path) -> tuple[list[str], dict[str, str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    values: dict[str, str] = {}
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return lines, values


def write_properties(path: Path, lines: list[str], updates: dict[str, str]) -> None:
    rendered: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0].strip()
            if key in updates:
                rendered.append(f"{key}={updates[key]}")
                continue
        rendered.append(line)
    path.write_text("\n".join(rendered) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    path = Path(args.file)
    lines, values = read_properties(path)

    missing = [key for key in KEYS if key not in values]
    if missing:
        print(f"missing keys: {', '.join(missing)}", file=sys.stderr)
        return 2

    try:
        release = int(values["releaseNumber"])
        last_published = int(values["lastPublishedReleaseNumber"])
    except ValueError as exc:
        print(f"releaseNumber/lastPublishedReleaseNumber must be integers: {exc}", file=sys.stderr)
        return 2

    match = VERSION_RE.match(values["versionName"])
    if not match:
        print(f"versionName must be major.minor.patch: {values['versionName']}", file=sys.stderr)
        return 2

    # Idempotent: a retried workflow or a manually-merged bump PR should not
    # advance the version a second time. When the workflow knows which release
    # was just published, use it to distinguish "pre-release" from "already
    # advanced" state.
    if args.released_release is not None:
        if last_published == args.released_release:
            result = {"bumped": False, "reason": "already advanced"}
            print(json.dumps(result, ensure_ascii=False) if args.json else "already advanced")
            return 0
        if release != args.released_release:
            print(
                f"version.properties releaseNumber={release} does not match the published release "
                f"{args.released_release}; refusing to guess",
                file=sys.stderr,
            )
            return 2
    elif last_published == release:
        result = {"bumped": False, "reason": "already advanced"}
        print(json.dumps(result, ensure_ascii=False) if args.json else "already advanced")
        return 0

    if last_published != release - 1:
        print(
            "lastPublishedReleaseNumber must equal releaseNumber-1 before a formal release "
            f"(got {last_published} / {release})",
            file=sys.stderr,
        )
        return 2

    major, minor, patch = (int(part) for part in match.groups())
    next_name = f"{major}.{minor}.{patch + 1}"
    next_release = release + 1
    updates = {
        "versionName": next_name,
        "releaseNumber": str(next_release),
        "lastPublishedReleaseNumber": str(release),
    }
    write_properties(path, lines, updates)
    result = {
        "bumped": True,
        "previous_version": values["versionName"],
        "previous_release": release,
        "next_version": next_name,
        "next_release": next_release,
    }
    print(json.dumps(result, ensure_ascii=False) if args.json else json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
