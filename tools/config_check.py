#!/usr/bin/env python3
"""Compare a live plugins/Heroes folder with the repo's default configs.

Usage: python tools/config_check.py <live plugins/Heroes dir> [--resources src/main/resources] [--values]

The plugin never overwrites an existing config file, so a live server keeps its old files and
must have new keys copied in by hand. Many keys are read with no fallback, and a missing one
stops the plugin or an ability from loading. This lists, per file:

  MISSING FILE   a config the repo ships that the live folder does not have (created on next start)
  MISSING        a key the repo has and the live file lacks, with the repo's default value
  EXTRA          a key only the live file has: usually a key this build renamed or removed

--values also lists keys present in both whose values differ (LIVE vs REPO). Use it to see
which in-game /balance edits have not been copied back into the repo.

List entries carrying a `type` (passives) or `action` (bot movement) are matched by that value.
lobby.yml and game-config.yml legitimately differ per server (coordinates, webhooks); read
their EXTRA and value lines with that in mind.

Needs PyYAML (pip install pyyaml). Read-only. Exits 1 when a file or key is missing.
"""
import argparse
import sys
from pathlib import Path

import yaml

from patchnotes import flatten, show

# Shipped in the jar but never extracted to the data folder, or not a config at all.
NOT_EXTRACTED = {"paper-plugin.yml", "bot-names.yml", "example-hero.yml", "example.yml"}


def load_flat(path):
    doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    flat = {}
    flatten(doc, (), flat)
    return flat


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("live_dir", help="the server's plugins/Heroes folder")
    parser.add_argument("--resources", default="src/main/resources", help="repo defaults (default: %(default)s)")
    parser.add_argument("--values", action="store_true", help="also list keys whose values differ")
    args = parser.parse_args()

    live_root = Path(args.live_dir)
    repo_root = Path(args.resources)
    if not live_root.is_dir():
        sys.exit(f"Not a folder: {live_root}")
    if not repo_root.is_dir():
        sys.exit(f"Not a folder: {repo_root} (run from the repository root or pass --resources)")

    missing = extra = differing = 0
    for repo_file in sorted(repo_root.rglob("*.yml")):
        relative = repo_file.relative_to(repo_root)
        if relative.parts[0] == "maps" or relative.name in NOT_EXTRACTED:
            continue
        live_file = live_root / relative
        if not live_file.exists():
            missing += 1
            print(f"MISSING FILE {relative}")
            continue
        repo_flat = load_flat(repo_file)
        live_flat = load_flat(live_file)
        for path in sorted(set(repo_flat) | set(live_flat)):
            dotted = ".".join(path)
            if path not in live_flat:
                missing += 1
                print(f"MISSING {relative} {dotted} (repo default: {show(repo_flat[path])})")
            elif path not in repo_flat:
                extra += 1
                print(f"EXTRA   {relative} {dotted} = {show(live_flat[path])}")
            elif args.values and repo_flat[path] != live_flat[path]:
                differing += 1
                print(f"VALUE   {relative} {dotted} LIVE {show(live_flat[path])} | REPO {show(repo_flat[path])}")

    summary = f"{missing} missing, {extra} extra"
    if args.values:
        summary += f", {differing} differing"
    print(summary, file=sys.stderr)
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
