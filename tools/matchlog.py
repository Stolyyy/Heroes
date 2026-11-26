#!/usr/bin/env python3
"""Per-hero balance summary from the plugin's match-log/*.jsonl files.

Usage: python tools/matchlog.py <match-log dir or .jsonl file> [--mode 1v1]
"""
import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path


def read_games(target):
    path = Path(target)
    files = sorted(path.glob("*.jsonl")) if path.is_dir() else [path]
    for file in files:
        with open(file, encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    yield json.loads(line)


def new_row():
    return {"games": 0, "wins": 0, "damage": 0.0, "abilities": defaultdict(float)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("target")
    parser.add_argument("--mode", help="only this mode, e.g. 1v1 / 2v2 / Custom")
    args = parser.parse_args()

    rows = defaultdict(lambda: defaultdict(new_row))  # mode -> hero -> row
    picks_per_mode = defaultdict(int)
    for game in read_games(args.target):
        if args.mode and game["mode"].lower() != args.mode.lower():
            continue
        for p in game["participants"]:
            if p["isBot"] or p["leftEarly"] or not p["hero"]:
                continue
            row = rows[game["mode"]][p["hero"]]
            row["games"] += 1
            row["wins"] += game["winner"] == p["team"]
            row["damage"] += p["damageDealt"]
            for ability, damage in p["damageByAbility"].items():
                row["abilities"][ability] += damage
            picks_per_mode[game["mode"]] += 1

    if not rows:
        sys.exit("no matching games")

    for mode, heroes in sorted(rows.items()):
        print(f"== {mode} ({picks_per_mode[mode]} human picks) ==")
        print(f"{'hero':<18}{'games':>6}{'pick%':>7}{'win%':>7}{'avg dmg':>9}  top abilities (share of damage)")
        for hero, row in sorted(heroes.items(), key=lambda kv: -kv[1]["games"]):
            games = row["games"]
            total = sum(row["abilities"].values())
            top = sorted(row["abilities"].items(), key=lambda kv: -kv[1])[:3]
            shares = ", ".join(f"{name} {100 * dmg / total:.0f}%" for name, dmg in top)
            print(f"{hero:<18}{games:>6}{100 * games / picks_per_mode[mode]:>6.1f}%"
                  f"{100 * row['wins'] / games:>6.1f}%{row['damage'] / games:>9.1f}  {shares}")
        print()


main()
