"""Inventory of config keys read by the Java code, with the unit each read implies.

Usage: python tools/yml_keys.py [src/main/java] [--csv]

For every require*/get* config read in ability/ and hero/ this prints the key, the file, the
accessor, and a guess at the unit from how the value is consumed on the same or next lines:
  ticks   -> passed to runTaskLater/runTaskTimer or compared with a tick counter
  ms      -> compared with System.currentTimeMillis() / passed to a Cooldown or *Ms field
  seconds -> multiplied by 1000 / 20 right after being read
  mult    -> used with * on another value, or key ends in mult
  add     -> used with + / -, or key ends in add/bonus
It is the first step of the YML consistency pass; nothing is modified.
"""
import re
import sys
from pathlib import Path

READ = re.compile(r"(?P<acc>require(?:Double|Int|Long|String|Boolean|Section)|getDouble|getInt|getLong|getString|getBoolean|orTemplate)\(\s*(?:[\w.]+,\s*)?\"(?P<key>[\w.\-]+)\"")


def guess_unit(key, window):
    k = key.lower()
    if k.endswith("_ms") or "currentTimeMillis" in window or "Cooldown(" in window or "Ms" in window:
        return "ms"
    if k.endswith("_ticks") or "runTaskLater" in window or "runTaskTimer" in window or "ticks" in k:
        return "ticks"
    if "* 1000" in window or "*1000" in window or "* 20" in window:
        return "seconds"
    if k.endswith("_mult") or k.endswith("multiplier") or re.search(r"\*\s*\w", window):
        return "mult"
    if k.endswith("_add") or k.endswith("_bonus") or re.search(r"[+\-]\s*\w", window):
        return "add"
    return "?"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    root = Path(args[0] if args else "src/main/java")
    csv = "--csv" in sys.argv
    rows = []
    for path in root.rglob("*.java"):
        rel = path.as_posix()
        if "/ability/" not in rel and "/hero/" not in rel:
            continue
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        for i, line in enumerate(lines):
            for m in READ.finditer(line):
                window = " ".join(lines[i:i + 3])
                rows.append((m.group("key"), rel.split("me/stolyy/heroes/")[-1], i + 1, m.group("acc"), guess_unit(m.group("key"), window)))
    rows.sort()
    if csv:
        print("key,file,line,accessor,unit")
        for r in rows:
            print(",".join(map(str, r)))
        return
    width = max(len(r[0]) for r in rows) if rows else 10
    for key, file, line, acc, unit in rows:
        print(f"{key:<{width}}  {unit:<7}  {acc:<14}  {file}:{line}")
    print(f"\n{len(rows)} reads; units: " + ", ".join(f"{u}={sum(1 for r in rows if r[4]==u)}" for u in ("ms", "ticks", "seconds", "mult", "add", "?")))


if __name__ == "__main__":
    main()
