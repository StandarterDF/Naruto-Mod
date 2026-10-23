#!/usr/bin/env python3
"""Automatic cost balance and board layout for the mod's skill tree.

The tree lives as one static table in ``skills/Skill.java``. Hand-pricing and hand-placing a
hundred and twenty nodes is error-prone, so this script derives both the price and the cell of
every node from the graph itself, and can write them back:

* **Costs** grow with a node's distance from the root (the longest prerequisite chain), passives
  scale with how big their number is, and techniques carry a multiplier that grows with how
  decisive they are. The clone family therefore reads chibi < shadow < multi, and the signature
  techniques (multi clone, Kamui, Rasenshuriken, Sage Mode, Eight Gates) top the board - above any
  plain stat node such as the old ``+250 chakra`` capstone.
* **Layout** is optional (``--relayout``): by default ``--apply`` only rewrites prices and leaves the
  coordinates alone, because the hand-tuned board already has far fewer crossings than the automatic
  arrangement. When asked, the layout gives every branch a direction out of the root, shapes it as a
  tidy tree, snaps each node to the closest legal cell and then compacts inward; that result is a
  starting point, not a guarantee, so the number of edge crossings is always printed for comparison.

Usage:
    python tools/skilltree_auto.py            # report: proposed costs + layout
    python tools/skilltree_auto.py --apply    # write costs back (coordinates untouched)
    python tools/skilltree_auto.py --relayout # also rewrite coordinates with the automatic layout
    python tools/skilltree_auto.py --check     # lint the current file, exit 1 on a board-rule break
"""

import argparse
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
SKILL_JAVA = ROOT_DIR / "src/main/java/com/sekwah/narutomod/skills/Skill.java"

DECL = re.compile(
    r'(?s)(node|unlock)\(\s*"([a-z_]+)"\s*,\s*SkillBranch\.([A-Z_]+)\s*,\s*'
    r"(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,(.*?)\)\s*;"
)
QUOTED = re.compile(r'"([a-z_:]+)"')
EFFECT = re.compile(r"SkillEffect\.([A-Z_]+)\s*,\s*(-?\d+(?:\.\d+)?)f")

# ----------------------------------------------------------------------------------------------
# Balance model
# ----------------------------------------------------------------------------------------------

APEX = {
    "clone_multi",
    "space_kamui",
    "elem_rasenshuriken",
    "body_sage_mode",
    "body_eight_gates",
}
STRONG = {"elem_rasengan", "body_second_breath", "space_phasing", "mind_sensing"}
SIMPLE = {"elem_fire", "body_fist", "agility_jump", "elem_water"}
# Chores and quality-of-life: gathering, storage, survival and travel. Cheap on purpose so a ninja
# can pick them up almost as soon as their branch opens.
UTILITY = {
    "elem_campfire",
    "elem_ore_excavation",
    "elem_wide_dig",
    "elem_ore_sense",
    "space_marker",
    "space_thread",
    "space_adaptation",
    "clone_meditation",
    "clone_recall",
    "clone_storage",
    "body_feeding",
    "mind_silence",
    "agility_adhesion",
    "agility_water_walk",
}
RANK_FACTOR = {
    "utility": 0.7,
    "simple": 1.0,
    "normal": 1.35,
    "strong": 1.9,
    "apex": 2.6,
}
MAX_DEPTH_BAND = 7


def band(depth):
    """Base price of a node at the given distance from the root."""
    return 50.0 + 50.0 * min(depth, MAX_DEPTH_BAND)


def passive_factor(effect, amount):
    if effect == "MAX_CHAKRA":
        return 0.7 + amount / 100
    if effect == "CHAKRA_REGEN":
        return 0.8 + amount / 1.5
    if effect == "MAX_STAMINA":
        return 0.8 + amount / 200
    if effect == "STAMINA_REGEN":
        return 0.8 + amount / 1.5
    if effect == "MAX_HEALTH":
        return 0.8 + amount / 15
    if effect == "ARMOR":
        return 0.8 + amount / 8
    if effect == "ATTACK_DAMAGE":
        return 0.8 + amount / 5
    if effect == "MOVEMENT_SPEED":
        return 0.8 + amount / 0.03
    if effect in ("COST_REDUCTION", "COOLDOWN_REDUCTION"):
        return 0.8 + amount / 0.15
    if effect == "MAX_SUBSTITUTIONS":
        return 0.6 * amount
    return 1.0


def rank(node):
    if node["path"] in APEX:
        return "apex"
    if node["path"] in STRONG:
        return "strong"
    if node["path"] in UTILITY:
        return "utility"
    if node["path"] in SIMPLE:
        return "simple"
    return "normal"


def nice(value):
    value = max(5.0, value)
    if value < 100:
        return int(round(value / 5.0) * 5)
    return int(round(value / 10.0) * 10)


def proposed_cost(node, depth):
    if not node["req"]:
        return 40
    base = band(depth[node["path"]])
    if node["kind"] == "unlock":
        return nice(base * RANK_FACTOR[rank(node)])
    return nice(base * passive_factor(node["eff"], node["amt"]))


# ----------------------------------------------------------------------------------------------
# Layout model
# ----------------------------------------------------------------------------------------------

# Each branch is its own board (one tab in game), so it grows out of its own centre; the
# direction is picked by declaration order so neighbouring tabs look different.
DIRECTIONS = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))


def primary_parent(nodes, path):
    """The prerequisite the tidy pass parents a node under: a same-branch one, else the first."""
    reqs = nodes[path]["req"]
    same = [r for r in reqs if nodes[r]["branch"] == nodes[path]["branch"]]
    if same:
        return same[0]
    return reqs[0] if reqs else None


def branch_centres(nodes):
    centres = {}
    for p, n in nodes.items():
        if not n["req"]:
            centres.setdefault(n["branch"], p)
    return centres


def compute_layout(nodes, order):
    """Lay every branch out as its own board, because in game each branch is a separate tab.

    A branch grows out of its own centre along one of four directions (picked by declaration
    order so neighbouring tabs look different), is tidied as a tree and compacted inward. Cells
    only ever collide inside a branch, so nothing needs to be reserved across boards.
    """
    depth = depths(nodes, order)
    primary = {p: primary_parent(nodes, p) for p in order}
    centres = branch_centres(nodes)

    members = {}
    for p in order:
        members.setdefault(nodes[p]["branch"], []).append(p)

    directions = DIRECTIONS
    ideal, pos, occupied = {}, {}, set()

    for index, branch in enumerate(members):
        group = members[branch]
        centre = centres.get(branch)
        if centre is None:
            continue
        bset = set(group)
        kids = {p: [] for p in group}
        for p in group:
            pp = primary[p]
            if pp is not None and pp in bset and pp != p:
                kids[pp].append(p)

        v, slot = {}, [0]

        def walk(p):
            ch = kids[p]
            if not ch:
                v[p] = slot[0]
                slot[0] += 1
            else:
                for c in ch:
                    walk(c)
                v[p] = (v[ch[0]] + v[ch[-1]]) // 2

        walk(centre)
        u = {centre: 0}
        queue = [centre]
        while queue:
            p = queue.pop(0)
            for c in kids[p]:
                u.setdefault(c, u[p] + 1)
                queue.append(c)
        for p in group:
            u.setdefault(p, 0)
            v.setdefault(p, slot[0])
            if p not in kids:
                slot[0] += 1

        d = directions[index % len(directions)]
        perp = (-d[1], d[0])
        v0 = v[centre]
        for p in group:
            ideal[p] = (
                u[p] * d[0] + (v[p] - v0) * perp[0],
                u[p] * d[1] + (v[p] - v0) * perp[1],
            )

        order_by_depth = sorted(group, key=lambda p: (depth[p], order.index(p)))
        for p in order_by_depth:
            if p == centre:
                pos[p] = (0, 0)
                occupied.add((0, 0))
                continue
            pos[p] = find_cell(
                nodes, pos, occupied, (round(ideal[p][0]), round(ideal[p][1])), p
            )
            occupied.add(pos[p])

        # Compaction inside the board: no node further out than it needs to be.
        kids_all = {p: [] for p in group}
        for p in group:
            for r in nodes[p]["req"]:
                if r in kids_all:
                    kids_all[r].append(p)
        neighbours = (
            (0, -1),
            (0, 1),
            (-1, 0),
            (1, 0),
            (-1, -1),
            (-1, 1),
            (1, -1),
            (1, 1),
        )
        for _ in range(200):
            moved = False
            for p in sorted(group, key=lambda q: -max(abs(pos[q][0]), abs(pos[q][1]))):
                if p == centre:
                    continue
                cur = pos[p]
                radius = max(abs(cur[0]), abs(cur[1]))
                if radius <= 1:
                    continue
                options = [
                    (cur[0] + dx, cur[1] + dy)
                    for dx, dy in neighbours
                    if (cur[0] + dx, cur[1] + dy) not in occupied
                    and max(abs(cur[0] + dx), abs(cur[1] + dy)) < radius
                ]
                options.sort(key=lambda c: max(abs(c[0]), abs(c[1])))
                for cell in options:
                    if all(
                        max(abs(cell[0] - pos[q][0]), abs(cell[1] - pos[q][1]))
                        <= (4 if q == centre else 2)
                        for q in nodes[p]["req"] + kids_all[p]
                    ):
                        occupied.discard(cur)
                        occupied.add(cell)
                        pos[p] = cell
                        moved = True
                        break
            if not moved:
                break
    return pos, ideal, depth


def find_cell(nodes, pos, occupied, target, path):
    """Closest free cell to ``target`` that keeps every prerequisite within reach."""
    parents = nodes[path]["req"]
    best, best_cost = None, None
    for r in range(0, 24):
        ring = [
            (target[0] + dx, target[1] + dy)
            for dx in range(-r, r + 1)
            for dy in range(-r, r + 1)
            if max(abs(dx), abs(dy)) == r
        ]
        ring.sort(key=lambda c: (c[0] - target[0]) ** 2 + (c[1] - target[1]) ** 2)
        for cell in ring:
            if cell in occupied:
                continue
            dist = [
                max(abs(cell[0] - pos[q][0]), abs(cell[1] - pos[q][1]))
                for q in parents
                if q in pos
            ]
            if all(
                d <= (4 if not nodes[q]["req"] else 2)
                for d, q in zip(dist, parents)
                if q in pos
            ):
                return cell
            if best is None or sum(dist) < best_cost:
                best, best_cost = cell, sum(dist)
    return best if best is not None else target


# ----------------------------------------------------------------------------------------------
# Parsing / writing / validation
# ----------------------------------------------------------------------------------------------


def parse():
    src = SKILL_JAVA.read_text(encoding="utf-8")
    nodes, order, spans = {}, [], []
    for m in DECL.finditer(src):
        kind, path, branch, x, y, cost, tail = m.groups()
        q = QUOTED.findall(tail)
        if kind == "unlock":
            unlock, req, eff, amt = q[0], q[1:], "UNLOCK", 0.0
        else:
            unlock, req = None, q
            f = EFFECT.search(tail)
            eff, amt = f.group(1), float(f.group(2))
        nodes[path] = dict(
            kind=kind,
            path=path,
            branch=branch,
            x=int(x),
            y=int(y),
            cost=int(cost),
            unlock=unlock,
            req=req,
            eff=eff,
            amt=amt,
        )
        order.append(path)
        spans.append((m.start(), m.end(), path))
    return src, nodes, order, spans


def depths(nodes, order):
    depth = {p: 0 for p in nodes}
    for _ in range(len(nodes) + 2):
        changed = False
        for p in order:
            if not nodes[p]["req"]:
                continue
            d = max([depth[r] + 1 for r in nodes[p]["req"] if r in depth] or [0])
            if d > depth[p]:
                depth[p] = d
                changed = True
        if not changed:
            break
    return depth


def validate(nodes, pos):
    """Board rules: cells are unique inside a branch, links stay short and none cuts a node box."""
    problems, cells = [], {}
    centres = branch_centres(nodes)
    for p in nodes:
        key = (nodes[p]["branch"], pos[p])
        if key in cells:
            problems.append(
                "duplicate cell %s/%s: %s and %s"
                % (nodes[p]["branch"], pos[p], cells[key], p)
            )
        cells[key] = p
    for p in nodes:
        for r in nodes[p]["req"]:
            if r not in pos:
                problems.append("unknown prerequisite %s -> %s" % (p, r))
                continue
            if nodes[r]["branch"] != nodes[p]["branch"]:
                problems.append(
                    "cross-branch prerequisite %s (%s) -> %s (%s)"
                    % (p, nodes[p]["branch"], r, nodes[r]["branch"])
                )
                continue
            d = max(abs(pos[p][0] - pos[r][0]), abs(pos[p][1] - pos[r][1]))
            limit = 4 if r == centres.get(nodes[p]["branch"]) else 2
            if d > limit:
                problems.append("long link (%d): %s -> %s" % (d, p, r))
    for branch, centre in centres.items():
        if nodes[centre]["kind"] == "unlock":
            problems.append(
                "a technique is the centre of %s (a branch must open on a passive): %s"
                % (branch, centre)
            )
    for child, parent, through in link_hits(nodes, pos):
        problems.append(
            "link through box: %s -> %s passes over %s" % (child, parent, through)
        )
    return problems


# The board draws each link as a straight run of one-pixel cells between the two node centres
# (see skilltree_preview.py), so a link that clips a third node's 22x22 box is visible even when
# no two links cross. These are the preview's pixel constants.
GRID_X, GRID_Y, HALF = 132.0, 94.0, 11.0


def link_hits(nodes, pos):
    hits = []
    for p in nodes:
        for r in nodes[p]["req"]:
            ax, ay = pos[r][0] * GRID_X, pos[r][1] * GRID_Y
            bx, by = pos[p][0] * GRID_X, pos[p][1] * GRID_Y
            for q in nodes:
                # Boards are separate tabs, so a link may only be checked against its own board.
                if q in (p, r) or nodes[q]["branch"] != nodes[p]["branch"]:
                    continue
                cx, cy = pos[q][0] * GRID_X, pos[q][1] * GRID_Y
                for i in range(1, 200):
                    t = i / 200
                    if (
                        abs(ax + (bx - ax) * t - cx) <= HALF
                        and abs(ay + (by - ay) * t - cy) <= HALF
                    ):
                        hits.append((p, r, q))
                        break
    return hits


def _orient(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def _proper(a, b, c, d):
    """True when the open segments a-b and c-d cross (shared endpoints do not count)."""
    return (
        _orient(a, b, c) * _orient(a, b, d) < 0
        and _orient(c, d, a) * _orient(c, d, b) < 0
    )


def count_crossings(nodes, pos):
    edges = sorted({(r, p) for p in nodes for r in nodes[p]["req"]})
    found = 0
    for i, e1 in enumerate(edges):
        for e2 in edges[i + 1 :]:
            if len(set(e1) | set(e2)) < 4:
                continue
            if _proper(pos[e1[0]], pos[e1[1]], pos[e2[0]], pos[e2[1]]):
                found += 1
    return found


def write(src, nodes, spans, costs, positions):
    out, cursor = [], 0
    for start, end, path in spans:
        out.append(src[cursor:start])
        stmt = src[start:end]
        n = nodes[path]
        x, y = positions[path] if positions else (n["x"], n["y"])
        cost = costs[path] if costs else n["cost"]
        head = re.compile(
            r"(SkillBranch\.%s\s*,\s*)-?\d+(\s*,\s*)-?\d+(\s*,\s*)\d+(\s*,)"
            % n["branch"]
        )
        stmt = head.sub(
            lambda mm: (
                mm.group(1)
                + str(x)
                + mm.group(2)
                + str(y)
                + mm.group(3)
                + str(cost)
                + mm.group(4)
            ),
            stmt,
            count=1,
        )
        out.append(stmt)
        cursor = end
    out.append(src[cursor:])
    return "".join(out)


def save(text):
    data = SKILL_JAVA.read_bytes()
    if b"\r\n" in data:
        text = text.replace("\n", "\r\n")
    SKILL_JAVA.write_bytes(text.encode("utf-8"))


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--relayout", action="store_true", help="also rewrite coordinates")
    args = ap.parse_args()

    src, nodes, order, spans = parse()
    depth = depths(nodes, order)

    current_pos = {p: (nodes[p]["x"], nodes[p]["y"]) for p in order}
    current_crossings = count_crossings(nodes, current_pos)
    if args.check:
        problems = validate(nodes, current_pos)
        for pr in problems:
            print("  - " + pr)
        print(
            "layout problems: %d   crossings: %d" % (len(problems), current_crossings)
        )
        return 1 if problems else 0

    costs = {p: proposed_cost(nodes[p], depth) for p in order}

    print(
        "old total %d   new total %d"
        % (sum(n["cost"] for n in nodes.values()), sum(costs.values()))
    )
    print("--- most expensive (new) ---")
    for p in sorted(order, key=lambda p: -costs[p])[:8]:
        n = nodes[p]
        print(
            "  %5d (was %4d) d%-2d %-22s %s"
            % (costs[p], n["cost"], depth[p], p, n["unlock"] or n["eff"])
        )
    print("--- clone family ---")
    for p in [
        "clone_shadow",
        "clone_drill",
        "clone_chibi",
        "clone_multi",
        "clone_legend",
    ]:
        n = nodes[p]
        print(
            "  %5d (was %4d) d%-2d %-16s %s"
            % (costs[p], n["cost"], depth[p], p, n["unlock"] or n["eff"])
        )

    if not args.relayout:
        pos = current_pos
        problems = validate(nodes, pos)
        print("--- layout (unchanged) ---")
        print(
            "layout problems: %d   crossings: %d" % (len(problems), current_crossings)
        )
        for pr in problems:
            print("  PROBLEM: " + pr)
        if args.apply:
            save(write(src, nodes, spans, costs, None))
            print("applied costs to %s (coordinates untouched)" % SKILL_JAVA.name)
        return 0

    pos, ideal, _ = compute_layout(nodes, order)
    problems = validate(nodes, pos)
    crossings = count_crossings(nodes, pos)
    print("--- proposed layout (per board) ---")
    seen = []
    for p in order:
        name = nodes[p]["branch"]
        if name in seen:
            continue
        seen.append(name)
        xs = [pos[q][0] for q in order if nodes[q]["branch"] == name]
        ys = [pos[q][1] for q in order if nodes[q]["branch"] == name]
        print(
            "  %-11s %3d nodes  x[%3d..%3d] y[%3d..%3d]"
            % (name, len(xs), min(xs), max(xs), min(ys), max(ys))
        )
    for pr in problems:
        print("  PROBLEM: " + pr)
    print(
        "layout problems: %d   crossings: %d (was %d)"
        % (len(problems), crossings, current_crossings)
    )

    if args.apply:
        save(write(src, nodes, spans, costs, pos))
        print("applied costs and layout to %s" % SKILL_JAVA.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
