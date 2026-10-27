#!/usr/bin/env python3
"""Index the mod's techniques (jutsu) for quick lookup and duplicate detection.

Scans abilities/jutsus/*.java and abilities/utility/*.java, extracts the class name,
base class, combo, costs, nature and activation type, and reports overlaps.

Usage:
  python tools/jutsu_index.py                 # print table
  python tools/jutsu_index.py --dup           # combos registered more than once
  python tools/jutsu_index.py --similar NAME  # techniques close to NAME
  python tools/jutsu_index.py --json          # write tmp/jutsu_index.json
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JUTSU_DIRS = [
    ROOT / "src/main/java/com/sekwah/narutomod/abilities/jutsus",
    ROOT / "src/main/java/com/sekwah/narutomod/abilities/utility",
]

CLASS_RE = re.compile(r"\bclass\s+(\w+)\s+extends\s+(\w+)")
COMBO_RE = re.compile(r"COMBO\s*=\s*(\d+)\s*L?")
COST_RE = re.compile(r"(\w*COST\w*)\s*=\s*([\d.]+)f?")
NATURE_RE = re.compile(
    r"Nature\s+nature\(\)[^{]*\{\s*return\s+[^;]*Nature\.(\w+)", re.S
)
ACT_RE = re.compile(
    r"ActivationType\s+activationType\(\)[^{]*\{\s*return\s+ActivationType\.(\w+)", re.S
)


def scan():
    rows = []
    for d in JUTSU_DIRS:
        if not d.exists():
            continue
        for f in sorted(d.glob("*.java")):
            text = f.read_text(encoding="utf-8", errors="replace")
            m = CLASS_RE.search(text)
            if not m:
                continue
            cls, base = m.group(1), m.group(2)
            combo = COMBO_RE.search(text)
            costs = {}
            for cm in COST_RE.finditer(text):
                costs[cm.group(1).lower()] = cm.group(2)
            nature = NATURE_RE.search(text)
            act = ACT_RE.search(text)
            rows.append(
                {
                    "file": f.name,
                    "class": cls,
                    "base": base,
                    "combo": combo.group(1) if combo else None,
                    "costs": costs,
                    "nature": nature.group(1) if nature else None,
                    "activation": act.group(1) if act else None,
                }
            )
    return rows


def dup_combos(rows):
    from collections import Counter

    c = Counter(r["combo"] for r in rows if r["combo"])
    return {k: v for k, v in c.items() if v > 1}


def similar(rows, name):
    name_l = name.lower()

    # tokenise camelCase
    def tokens(s):
        return set(re.findall(r"[A-Z][a-z]+|[a-z]+", s))

    target_tokens = tokens(name)
    out = []
    for r in rows:
        cls_tokens = tokens(r["class"])
        base_tokens = tokens(r["base"])
        score = len(target_tokens & (cls_tokens | base_tokens))
        # also substring name match
        if name_l in r["class"].lower():
            score += 3
        if score > 0:
            out.append((score, r))
    out.sort(key=lambda x: -x[0])
    return out[:15]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dup", action="store_true", help="show duplicate combos")
    ap.add_argument("--similar", metavar="NAME", help="techniques close to NAME")
    ap.add_argument("--json", action="store_true", help="write tmp/jutsu_index.json")
    args = ap.parse_args()

    rows = scan()
    if args.dup:
        d = dup_combos(rows)
        if not d:
            print("No duplicate combos found.")
        else:
            print("Duplicate combos:")
            for combo, n in d.items():
                print(f"  {combo}: {n} techniques")
                for r in rows:
                    if r["combo"] == combo:
                        print(f"     - {r['class']} ({r['file']})")
        return

    if args.similar:
        res = similar(rows, args.similar)
        print(f"Techniques similar to {args.similar}:")
        for score, r in res:
            print(
                f"  [{score}] {r['class']} (base={r['base']}, combo={r['combo']}, costs={r['costs']}, nature={r['nature']})"
            )
        return

    if args.json:
        out = ROOT / "tmp" / "jutsu_index.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"wrote {out} ({len(rows)} techniques)")
        return

    # default table
    print(f"{'class':32} {'base':28} {'combo':>8}  costs  nature  activation")
    for r in rows:
        costs = ",".join(f"{k}:{v}" for k, v in r["costs"].items()) or "-"
        print(
            f"{r['class']:32} {r['base']:28} {str(r['combo'] or '-'):>8}  {costs:24} {str(r['nature'] or '-'):8} {str(r['activation'] or '-')}"
        )


if __name__ == "__main__":
    main()
