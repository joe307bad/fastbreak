#!/usr/bin/env python3
"""Build the WireMock stubs the iOS screenshot capture runs against.

Screenshots are taken against a mock so a capture never changes because a score
did, and so no shot can catch a sync banner waiting on the network. The stub
bodies are real payloads: either the copies the web build already downloaded
into web/data, or a one-time fetch from the CDN.

The generated stubs are not committed - they are ~15MB of chart JSON. Run this
(or just run capture-ios-screenshots.sh, which runs it for you) to rebuild them.

    python3 scripts/seed-wiremock-stubs.py [--refresh]
"""

import argparse
import json
import pathlib
import shutil
import sys
import urllib.request

CDN = "https://d2jyizt5xogu23.cloudfront.net"
SPORTS = ("nfl", "mlb", "nba", "nhl")

# Frozen so "updated 6h ago" never drifts to "updated 3 days ago" between runs.
FROZEN_UPDATED_AT = "2026-10-22T09:00:00Z"

ROOT = pathlib.Path(__file__).resolve().parent
MOCK = ROOT / "wiremock"
FILES = MOCK / "__files"
MAPPINGS = MOCK / "mappings"
WEB_DATA = ROOT.parent.parent / "web" / "data"


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def write_mapping(name: str, url_path: str, body_file: str) -> None:
    (MAPPINGS / name).write_text(json.dumps({
        "request": {"method": "GET", "urlPath": url_path},
        "response": {
            "status": 200,
            "headers": {"Content-Type": "application/json"},
            "bodyFileName": body_file,
        },
    }, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh", action="store_true",
                        help="re-fetch payloads from the CDN even if present")
    args = parser.parse_args()

    for directory in (FILES, MAPPINGS):
        directory.mkdir(parents=True, exist_ok=True)
        for stale in directory.glob("*.json"):
            stale.unlink()

    # The registry drives everything: the app syncs exactly what it lists, so a
    # listed entry with no stub shows up in the app as a failed chart.
    registry_path = WEB_DATA / "registry.json"
    if registry_path.exists() and not args.refresh:
        registry = json.loads(registry_path.read_text())
        print(f"registry: {registry_path}")
    else:
        registry = json.loads(fetch(f"{CDN}/registry"))
        print(f"registry: {CDN}/registry")

    kept = {}
    for key, entry in registry.items():
        name = key.split("/")[-1]
        if not name.endswith(".json"):
            continue  # system/releaseId and friends
        kept[key] = {**entry, "updatedAt": FROZEN_UPDATED_AT}

    for key in list(kept):
        name = key.split("/")[-1]
        local = WEB_DATA / "charts" / name
        target = FILES / name
        if local.exists() and not args.refresh:
            shutil.copyfile(local, target)
        else:
            try:
                target.write_bytes(fetch(f"{CDN}/{key}"))
            except Exception as error:  # noqa: BLE001 - report and carry on
                print(f"  ! {name}: {error}; dropping from the registry")
                del kept[key]
                continue
        write_mapping(f"chart-{name}", f"/{key}", name)

    (MAPPINGS / "registry.json").write_text(json.dumps({
        "request": {"method": "GET", "urlPath": "/registry"},
        "response": {
            "status": 200,
            "headers": {"Content-Type": "application/json"},
            "jsonBody": kept,
        },
    }, indent=2))

    # Team rosters carry the palettes, so a screenshot asking for a team theme
    # needs these or it falls back to the default purple.
    for sport in SPORTS:
        name = f"{sport}__teams.json"
        target = FILES / name
        if not target.exists() or args.refresh:
            try:
                target.write_bytes(fetch(f"{CDN}/prod/teams/{name}"))
            except Exception as error:  # noqa: BLE001
                print(f"  ! {name}: {error}")
                continue
        write_mapping(f"teams-{sport}", f"/prod/teams/{name}", name)

    # Anything unstubbed answers empty rather than hanging on a host that is not
    # there. Lowest priority, so every real stub wins.
    (MAPPINGS / "zz-catch-all.json").write_text(json.dumps({
        "priority": 10,
        "request": {"method": "GET", "urlPattern": "/.*"},
        "response": {
            "status": 200,
            "headers": {"Content-Type": "application/json"},
            "body": "{}",
        },
    }, indent=2))

    print(f"stubs: {len(kept)} registry entries, "
          f"{len(list(FILES.glob('*.json')))} bodies")
    return 0


if __name__ == "__main__":
    sys.exit(main())
