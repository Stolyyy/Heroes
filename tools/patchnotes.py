#!/usr/bin/env python3
"""Patch-note lines for hero and template YML changes between two git refs.

Usage: python tools/patchnotes.py <old-ref> [new-ref] [--all] [--yml]

Prints one line per changed value in the format /balance writes to patch-notes.yml:
    {hero} {path} {old} -> {new}
Added and removed keys are listed as `(new)` / `(removed)`. new-ref defaults to HEAD.

Paths match /balance: list entries that carry a `type` (passives) or `action` (bot movement)
are addressed by that value, e.g. `bulk abilities.passives.bulkslam.damage`.

By default only gameplay values are shown. Text, cosmetics and bot tuning (name, description,
visuals, skins, unlock, bot, ai, strategy, movement, recovery) are hidden; --all shows them.
--yml wraps the lines as a dated section ready to paste into patch-notes.yml.

Needs PyYAML (pip install pyyaml). Run from the repository root.
"""
import argparse
import datetime
import subprocess
import sys

import yaml

RESOURCES = "src/main/resources"
HIDDEN_TOP_LEVEL = {"skins", "unlock", "bot", "ai", "strategy", "movement", "recovery"}
HIDDEN_ANYWHERE = {"name", "description", "visuals"}
MISSING = object()


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, encoding="utf-8")


def tracked_files(ref):
    """Config files this tool compares, as {target name: repo path}, at one ref."""
    out = git("ls-tree", "-r", "--name-only", ref, "--", f"{RESOURCES}/heroes", f"{RESOURCES}/templates.yml")
    if out.returncode != 0:
        sys.exit(f"Unknown ref '{ref}': {out.stderr.strip()}")
    files = {}
    for path in out.stdout.splitlines():
        if path.endswith(".yml"):
            files[path.rsplit("/", 1)[-1][:-4]] = path
    return files


def load(ref, path):
    out = git("show", f"{ref}:{path}")
    if out.returncode != 0:
        return {}
    return yaml.safe_load(out.stdout) or {}


def list_key(item):
    for field in ("type", "action"):
        if isinstance(item, dict) and isinstance(item.get(field), str):
            return item[field]
    return None


def flatten(node, prefix, out):
    if isinstance(node, dict):
        for key, value in node.items():
            flatten(value, prefix + (str(key),), out)
    elif isinstance(node, list) and node and all(list_key(item) for item in node):
        seen = {}
        for item in node:
            key = list_key(item)
            seen[key] = seen.get(key, 0) + 1
            segment = key if seen[key] == 1 else f"{key}#{seen[key]}"
            flatten(item, prefix + (segment,), out)
    elif isinstance(node, list):
        out[prefix] = ", ".join(str(item) for item in node)
    else:
        out[prefix] = node


def hidden(path):
    return path[0] in HIDDEN_TOP_LEVEL or any(part in HIDDEN_ANYWHERE for part in path)


def same(old, new):
    if isinstance(old, bool) or isinstance(new, bool):
        return old == new
    if isinstance(old, (int, float)) and isinstance(new, (int, float)):
        return float(old) == float(new)
    return old == new


def show(value):
    if value is MISSING:
        return ""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def diff_target(target, old_doc, new_doc, show_all):
    old_flat, new_flat = {}, {}
    flatten(old_doc, (), old_flat)
    flatten(new_doc, (), new_flat)
    lines = []
    for path in sorted(set(old_flat) | set(new_flat)):
        if not show_all and hidden(path):
            continue
        old = old_flat.get(path, MISSING)
        new = new_flat.get(path, MISSING)
        dotted = ".".join(path)
        if old is MISSING:
            lines.append(f"{target} {dotted} (new) -> {show(new)}")
        elif new is MISSING:
            lines.append(f"{target} {dotted} {show(old)} -> (removed)")
        elif not same(old, new):
            lines.append(f"{target} {dotted} {show(old)} -> {show(new)}")
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("old_ref")
    parser.add_argument("new_ref", nargs="?", default="HEAD")
    parser.add_argument("--all", action="store_true", help="include text, cosmetics and bot tuning")
    parser.add_argument("--yml", action="store_true", help="print as a dated patch-notes.yml section")
    args = parser.parse_args()

    old_files = tracked_files(args.old_ref)
    new_files = tracked_files(args.new_ref)

    lines = []
    for target in sorted(set(old_files) | set(new_files)):
        if target not in old_files:
            lines.append(f"{target} (new hero)")
            continue
        if target not in new_files:
            lines.append(f"{target} (removed)")
            continue
        lines.extend(diff_target(target, load(args.old_ref, old_files[target]),
                                 load(args.new_ref, new_files[target]), args.all))

    if args.yml:
        today = datetime.date.today().isoformat()
        print(yaml.safe_dump({today: lines}, sort_keys=False, allow_unicode=True, width=1000), end="")
    else:
        for line in lines:
            print(line)
    print(f"{len(lines)} change(s) between {args.old_ref} and {args.new_ref}", file=sys.stderr)


if __name__ == "__main__":
    main()
