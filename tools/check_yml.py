#!/usr/bin/env python3
"""Syntax check for every resource YML: parse errors, duplicate keys and tab indentation.

Usage: python tools/check_yml.py [src/main/resources]

Runs in CI before the Gradle build. The plugin only loads these files when the server starts,
so without this a typo in a hand-edited hero YML is first seen as a broken hero in game.
A duplicate key is reported because YAML silently keeps the last one, which hides an edit.

Needs PyYAML (pip install pyyaml). Exits 1 when any file has a problem.
"""
import sys
from pathlib import Path

import yaml


class StrictLoader(yaml.SafeLoader):
    """SafeLoader that rejects a mapping with the same key twice."""


def construct_mapping(loader, node, deep=False):
    seen = set()
    for key_node, _ in node.value:
        key = loader.construct_object(key_node, deep=True)
        if key in seen:
            raise yaml.constructor.ConstructorError(
                None, None, f"duplicate key '{key}'", key_node.start_mark)
        seen.add(key)
    return yaml.SafeLoader.construct_mapping(loader, node, deep=deep)


StrictLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, construct_mapping)


def check(path):
    text = path.read_text(encoding="utf-8")
    for number, line in enumerate(text.splitlines(), 1):
        indent = line[:len(line) - len(line.lstrip())]
        if "\t" in indent:
            return f"line {number}: tab used for indentation"
    try:
        yaml.load(text, Loader=StrictLoader)
    except yaml.YAMLError as error:
        return " ".join(str(error).split())
    return None


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
    files = sorted(root.rglob("*.yml"))
    if not files:
        sys.exit(f"No .yml files under {root}")
    failures = 0
    for path in files:
        problem = check(path)
        if problem:
            failures += 1
            print(f"FAIL {path.relative_to(root)}: {problem}")
    print(f"{len(files)} file(s) checked, {failures} with problems")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
