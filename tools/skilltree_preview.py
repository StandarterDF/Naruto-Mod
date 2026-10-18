#!/usr/bin/env python3
"""Offline editor for the mod's skill tree.

Reads the node table straight out of ``Skill.java`` (plus the branch colours from
``SkillBranch.java``, the effect names from ``SkillEffect.java`` and the labels from the
language files) and writes a single self-contained HTML page that draws the board the way
``SkillTreeScreen`` does - same grid, same node boxes, same stepped links, hover route
highlight, pan and zoom - and lets that board be edited by hand:

* drag a node to another grid cell (cells snap, occupied cells are refused);
* click a node to open its card: cost, branch, technique key, effect and amount, prerequisites,
  the four language strings, and a delete button;
* ``+ узел`` adds a node next to what you are looking at, ``связать`` links one node to
  another by clicking, ``отмена`` / Ctrl+Z undoes, ``сохранить`` (or Ctrl+S) writes the whole
  table to ``tmp/skilltree-edits.json`` (through the companion server started with
  ``--serve``, or as a download when the page is opened without it);
* the lint runs live in the page - duplicate cells, unknown or long prerequisites, techniques
  hanging off other techniques, duplicates of unlock keys, empty names, unreachable nodes -
  and offending nodes are outlined in red.

``--apply`` takes that file and writes the changes back into ``Skill.java`` and both language
files, so a layout can be planned, tweaked by hand in the browser and ported back without ever
guessing a coordinate. Backups of the touched files land in ``tmp/backup/``.

Usage:
    python tools/skilltree_preview.py                  # write tmp/skilltree-preview.html
    python tools/skilltree_preview.py --serve --open   # + save endpoint, open the browser
    python tools/skilltree_preview.py --apply          # port tmp/skilltree-edits.json back
    python tools/skilltree_preview.py --check          # lint only, write nothing
"""

import argparse
import json
import re
import sys
import time
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKILL_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/Skill.java"
BRANCH_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillBranch.java"
EFFECT_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillEffect.java"
LANG_EN = ROOT / "src/main/resources/assets/narutomod/lang/en_us.json"
LANG_RU = ROOT / "src/main/resources/assets/narutomod/lang/ru_ru.json"
DEFAULT_OUT = ROOT / "tmp/skilltree-preview.html"
DEFAULT_EDITS = ROOT / "tmp/skilltree-edits.json"

DECL = re.compile(
    r'(?s)(node|unlock)\(\s*"([a-z_]+)"\s*,\s*SkillBranch\.([A-Z_]+)\s*,\s*'
    r"(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,(.*?)\)\s*;"
)
QUOTED = re.compile(r'"([a-z_:]+)"')
EFFECT_DECL = re.compile(r"SkillEffect\.([A-Z_]+)\s*,\s*([0-9.]+)f")
BRANCH = re.compile(r'([A-Z_]+)\("([^"]+)",\s*(0x[0-9A-Fa-f]+)\)')
EFFECT_NAME = re.compile(r"^    ([A-Z_]+),?$", re.M)
STATIC_START = re.compile(r"    static \{\n")
BLOCK_END = "\n    }\n"

# Mirrors SkillTreeScreen.
GRID_X = 132
GRID_Y = 94


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_like(path: Path, text: str) -> None:
    """Write the text back keeping whatever line endings the file already uses."""
    if b"\r\n" in path.read_bytes():
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf-8"))


def backup(path: Path, stamp: str) -> Path:
    dest = ROOT / "tmp/backup" / stamp / path.name
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(path.read_bytes())
    return dest


def parse_skills():
    src = read_text(SKILL_JAVA)
    nodes = []
    for match in DECL.finditer(src):
        kind, path, branch, x, y, cost, tail = match.groups()
        quoted = QUOTED.findall(tail)
        if kind == "unlock":
            unlock, requires, effect, amount = quoted[0], quoted[1:], "", 0.0
        else:
            unlock, requires = None, quoted
            found = EFFECT_DECL.search(tail)
            effect = found.group(1) if found else ""
            amount = float(found.group(2)) if found else 0.0
        nodes.append(
            {
                "path": path,
                "branch": branch,
                "x": int(x),
                "y": int(y),
                "cost": int(cost),
                "unlock": unlock,
                "effect": effect,
                "amount": amount,
                "requires": requires,
            }
        )
    return nodes


def parse_branches():
    branches = {}
    order = []
    for match in BRANCH.finditer(read_text(BRANCH_JAVA)):
        name, key, colour = match.groups()
        branches[name] = {"key": key, "colour": int(colour, 16)}
        order.append(name)
    return branches, order


def parse_effects():
    names = EFFECT_NAME.findall(read_text(EFFECT_JAVA))
    return [name for name in names if name != "UNLOCK"]


def load_lang(nodes):
    def load(path):
        try:
            return json.loads(read_text(path))
        except FileNotFoundError:
            return {}

    en, ru = load(LANG_EN), load(LANG_RU)
    for node in nodes:
        base = "naruto.skill." + node["path"]
        node["name"] = en.get(base + ".name", node["path"])
        node["desc"] = en.get(base + ".desc", "")
        node["name_ru"] = ru.get(base + ".name", "")
        node["desc_ru"] = ru.get(base + ".desc", "")
    return en, ru


def lint(nodes, branches):
    problems = []
    notes = []
    by_path = {}
    for node in nodes:
        if node["path"] in by_path:
            problems.append("duplicate path: %s" % node["path"])
        by_path[node["path"]] = node
    cells = {}
    keys = {}
    for node in nodes:
        cell = (node["x"], node["y"])
        if cell in cells:
            problems.append(
                "duplicate cell %s: %s and %s" % (cell, cells[cell], node["path"])
            )
        cells[cell] = node["path"]
        if node["branch"] not in branches:
            problems.append("unknown branch %s on %s" % (node["branch"], node["path"]))
        for lang, key in (("en", "name"), ("ru", "name_ru")):
            if node["path"] != "root" and not node[key]:
                problems.append(
                    "missing %s lang: naruto.skill.%s.name" % (lang, node["path"])
                )
        if node["unlock"]:
            if node["unlock"] in keys:
                problems.append(
                    "duplicate unlock key %s: %s and %s"
                    % (node["unlock"], keys[node["unlock"]], node["path"])
                )
            else:
                keys[node["unlock"]] = node["path"]
        if node["unlock"] is not None and node["requires"]:
            # A technique must be entered through a training passive. Linking two techniques
            # directly makes the tree read as "the next box over" instead of a progression.
            if all(
                by_path.get(req, {}).get("unlock") is not None
                for req in node["requires"]
            ):
                problems.append(
                    "technique hangs off techniques (needs a passive): %s <- %s"
                    % (node["path"], ", ".join(node["requires"]))
                )
        for req in node["requires"]:
            other = by_path.get(req)
            if other is None:
                problems.append("unknown prerequisite %s -> %s" % (node["path"], req))
                continue
            step = max(abs(node["x"] - other["x"]), abs(node["y"] - other["y"]))
            # The branch trunks leave the root on a longer spoke; that star is intentional.
            limit = 3 if other["path"] == "root" else 2
            if step > limit:
                problems.append(
                    "long link (%d steps): %s -> %s" % (step, node["path"], req)
                )
            elif step > 1:
                notes.append("%d-step link: %s -> %s" % (step, node["path"], req))

    # Reachability from the root.
    root = next((n for n in nodes if n["path"] == "root"), None)
    seen = set()
    stack = [root["path"]] if root else []
    children = {}
    for node in nodes:
        for req in node["requires"]:
            children.setdefault(req, []).append(node["path"])
    while stack:
        path = stack.pop()
        if path in seen:
            continue
        seen.add(path)
        stack.extend(children.get(path, []))
    for node in nodes:
        if node["path"] not in seen:
            problems.append("unreachable from root: %s" % node["path"])

    # Branch boxes, to catch two branches sitting on top of each other.
    boxes = {}
    for node in nodes:
        box = boxes.setdefault(
            node["branch"], [node["x"], node["y"], node["x"], node["y"]]
        )
        box[0] = min(box[0], node["x"])
        box[1] = min(box[1], node["y"])
        box[2] = max(box[2], node["x"])
        box[3] = max(box[3], node["y"])
    names = sorted(boxes)
    for i, a in enumerate(names):
        for b in names[i + 1 :]:
            if a == "ROOT" or b == "ROOT":
                continue
            ba, bb = boxes[a], boxes[b]
            if ba[0] > bb[2] or bb[0] > ba[2] or ba[1] > bb[3] or bb[1] > ba[3]:
                continue
            notes.append("branch boxes overlap: %s %s and %s %s" % (a, ba, b, bb))
    return problems, notes, boxes


# --------------------------------------------------------------------------------------------
# Writing an edited table back into the Java source and the language files.
# --------------------------------------------------------------------------------------------


def format_amount(value) -> str:
    return "%g" % float(value)


def render_statement(node) -> str:
    requires = "".join(', "%s"' % req for req in node.get("requires") or [])
    unlock = node.get("unlock") or None
    if unlock:
        return 'unlock("%s", SkillBranch.%s, %d, %d, %d,\n                "%s"%s);' % (
            node["path"],
            node["branch"],
            int(node["x"]),
            int(node["y"]),
            int(node["cost"]),
            unlock,
            requires,
        )
    return (
        'node("%s", SkillBranch.%s, %d, %d, %d,\n'
        "                SkillEffect.%s, %sf%s);"
        % (
            node["path"],
            node["branch"],
            int(node["x"]),
            int(node["y"]),
            int(node["cost"]),
            node["effect"],
            format_amount(node.get("amount") or 0),
            requires,
        )
    )


def patch_static_block(src: str, edit_nodes) -> tuple:
    """Rewrite the nodes of the static block in place, keeping the comments around them."""
    start = STATIC_START.search(src)
    if start is None:
        raise SystemExit("cannot find the node table in %s" % SKILL_JAVA)
    begin = start.end()
    end = src.index(BLOCK_END, begin)
    block = src[begin:end]

    by_path = {n["path"]: n for n in edit_nodes}
    statements = list(DECL.finditer(block))
    existing = [m.group(2) for m in statements]
    removed = [p for p in existing if p not in by_path]
    branch_of = {m.group(2): m.group(3) for m in statements}
    relocated = [
        n
        for n in edit_nodes
        if n["path"] in branch_of and n["branch"] != branch_of[n["path"]]
    ]
    relocated_paths = {n["path"] for n in relocated}
    added = [n for n in edit_nodes if n["path"] not in existing]

    # New and moved nodes land after the last statement of their branch, so the sections of
    # the table keep reading branch by branch.
    anchors = {}
    for match in statements:
        path = match.group(2)
        if path in by_path and path not in relocated_paths:
            anchors[match.group(3)] = match
    inserts, tail = {}, []
    for node in added + relocated:
        anchor = anchors.get(node["branch"])
        if anchor is None:
            tail.append(node)
        else:
            inserts.setdefault(anchor.end(), []).append(node)

    out, updated, pos = [], 0, 0
    for match in statements:
        path = match.group(2)
        out.append(block[pos : match.start()])
        pos = match.end()
        if path not in relocated_paths:
            if path in by_path:
                rendered = render_statement(by_path[path])
                if rendered != match.group(0):
                    updated += 1
                out.append(rendered)
            else:
                out.append("// removed with the editor: %s" % path)
        for node in inserts.get(match.end(), []):
            out.append("\n        " + render_statement(node))
    out.append(block[pos:])
    new_block = "".join(out)
    if tail:
        new_block += "".join("\n        " + render_statement(n) for n in tail)

    return src[:begin] + new_block + src[end:], {
        "updated": updated,
        "added": [n["path"] for n in added],
        "removed": removed,
        "moved": [
            "%s: %s -> %s" % (n["path"], branch_of[n["path"]], n["branch"])
            for n in relocated
        ],
    }


def patch_lang(path: Path, updates: dict, removals=()) -> tuple:
    """Rewrite language values in place; new keys go after the last skill key of the file."""
    text = read_text(path)
    added = changed = removed = 0
    for key, value in updates.items():
        literal = json.dumps(value, ensure_ascii=False)
        pattern = re.compile(r'("%s"\s*:\s*)"(?:[^"\\]|\\.)*"' % re.escape(key))
        found = pattern.search(text)
        if found is None:
            anchor = text.rfind('"naruto.skill.')
            if anchor < 0:
                raise SystemExit("cannot find the skill block in %s" % path)
            line_end = text.index("\n", anchor)
            text = (
                text[: line_end + 1]
                + "  "
                + json.dumps(key, ensure_ascii=False)
                + ": "
                + literal
                + ",\n"
                + text[line_end + 1 :]
            )
            added += 1
        elif found.group(0) != found.group(1) + literal:
            text = pattern.sub(lambda m: m.group(1) + literal, text, count=1)
            changed += 1
    for key in removals:
        pattern = re.compile(
            r'[ \t]*"%s"\s*:\s*"(?:[^"\\]|\\.)*",?[ \t]*\r?\n' % re.escape(key)
        )
        text, count = pattern.subn("", text, count=1)
        removed += count
    write_like(path, text)
    return added, changed, removed


def lang_values(node, lang: str) -> tuple:
    structured = node.get(lang)
    if isinstance(structured, dict):
        name = structured.get("name", "")
        desc = structured.get("desc", "")
    else:
        name = node.get("name" if lang == "en" else "name_ru", "")
        desc = node.get("desc" if lang == "en" else "desc_ru", "")
    base = "naruto.skill." + node["path"]
    return base + ".name", name, base + ".desc", desc


def apply_edits(edits_path: Path) -> int:
    payload = json.loads(read_text(edits_path))
    edit_nodes = payload.get("nodes") or []
    branches, _order = parse_branches()
    effects = parse_effects()

    errors = []
    seen = set()
    for node in edit_nodes:
        path = node.get("path", "?")
        if not path or not re.fullmatch(r"[a-z0-9_]+", str(path)):
            errors.append("bad path: %r" % path)
        elif path in seen:
            errors.append("duplicate path: %s" % path)
        seen.add(path)
        if node.get("branch") not in branches:
            errors.append("%s: unknown branch %s" % (path, node.get("branch")))
        if node.get("unlock"):
            if not re.fullmatch(r"[a-z_]+:[a-z_]+", str(node["unlock"])):
                errors.append("%s: suspicious unlock key %r" % (path, node["unlock"]))
        else:
            if node.get("effect") not in effects:
                errors.append("%s: unknown effect %r" % (path, node.get("effect")))
            try:
                float(node.get("amount") or 0)
            except (TypeError, ValueError):
                errors.append("%s: bad amount %r" % (path, node.get("amount")))
    for node in edit_nodes:
        for req in node.get("requires") or []:
            if req == node.get("path"):
                errors.append("%s: requires itself" % node.get("path"))
            elif req not in seen:
                errors.append("%s: unknown prerequisite %s" % (node.get("path"), req))
    if errors:
        print("edits rejected:")
        for error in errors:
            print("  - " + error)
        return 1

    current = parse_skills()
    current_paths = {n["path"] for n in current}
    edit_paths = {n["path"] for n in edit_nodes}
    dead = sorted(current_paths - edit_paths)

    stamp = time.strftime("%Y%m%d-%H%M%S")
    backups = [
        backup(SKILL_JAVA, stamp),
        backup(LANG_EN, stamp),
        backup(LANG_RU, stamp),
    ]

    src = read_text(SKILL_JAVA)
    patched, report = patch_static_block(src, edit_nodes)
    write_like(SKILL_JAVA, patched)

    lang_report = {}
    for path, lang in ((LANG_EN, "en"), (LANG_RU, "ru")):
        updates = {}
        for node in edit_nodes:
            name_key, name, desc_key, desc = lang_values(node, lang)
            updates[name_key] = name
            updates[desc_key] = desc
        removals = []
        for node in current:
            if node["path"] in dead:
                base = "naruto.skill." + node["path"]
                removals += [base + ".name", base + ".desc"]
        lang_report[lang] = patch_lang(path, updates, removals)

    print("applied %s" % edits_path.relative_to(ROOT).as_posix())
    print("  statements updated: %d" % report["updated"])
    if report["added"]:
        print("  added: %s" % ", ".join(report["added"]))
    if report["removed"]:
        print("  removed: %s" % ", ".join(report["removed"]))
    if report["moved"]:
        print("  moved branches: %s" % ", ".join(report["moved"]))
    for lang, (added, changed, removed) in sorted(lang_report.items()):
        print(
            "  lang %s: %d added, %d updated, %d removed"
            % (lang, added, changed, removed)
        )
    print("  backups: %s" % backups[0].parent.relative_to(ROOT).as_posix())
    return 0


# --------------------------------------------------------------------------------------------
# The page itself.
# --------------------------------------------------------------------------------------------

HTML = r"""<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>Дерево скилов - редактор</title>
<style>
  html, body { margin: 0; height: 100%; background: #101418; overflow: hidden;
               font-family: Consolas, monospace; }
  canvas { display: block; }
  .panel { position: fixed; background: #000c; color: #ddd; font-size: 12px;
           padding: 6px 9px; border-radius: 4px; line-height: 1.6; }
  #legend { right: 8px; top: 8px; width: 118px; }
  #legend div { cursor: pointer; }
  #legend div:hover { color: #fff; }
  #legend .off { opacity: .35; }
  #tools { left: 8px; top: 8px; width: 316px; }
  #tools button, #card button { background: #23292f; color: #ddd; border: 1px solid #444;
        border-radius: 3px; font: inherit; padding: 2px 7px; margin: 0 4px 4px 0;
        cursor: pointer; }
  #tools button:hover, #card button:hover { background: #2f3740; }
  #tools button.on { border-color: #a06cff; color: #fff; }
  #tools .line { line-height: 1.5; }
  #card { left: 8px; top: 132px; width: 316px; display: none;
          max-height: calc(100% - 200px); overflow: auto; }
  #card .row { margin: 3px 0; }
  #card label { display: inline-block; width: 72px; color: #9a9a9a; vertical-align: top; }
  #card input, #card select, #card textarea { background: #1a1f24; color: #eee;
        border: 1px solid #3a424a; border-radius: 3px; font: inherit; padding: 1px 4px; }
  #card input[type=number] { width: 58px; }
  #card input[type=text] { width: 214px; }
  #card textarea { width: 214px; height: 30px; }
  .chip { display: inline-block; background: #2a3140; border: 1px solid #4a5568;
          border-radius: 8px; padding: 0 6px; margin: 1px 3px 1px 0; cursor: pointer; }
  .chip:hover { border-color: #ff6b6b; color: #fff; }
  #problems { left: 8px; top: 8px; width: 470px; display: none;
              max-height: 70%; overflow: auto; }
  #problems div { cursor: pointer; }
  #problems div:hover { color: #fff; }
  #problems .note { color: #9a9a9a; }
  #hud { left: 8px; bottom: 8px; }
  #tip { position: fixed; display: none; pointer-events: none; max-width: 480px;
         background: #14001fee; border: 1px solid #a06cff; color: #eee;
         font-size: 12px; padding: 7px 9px; border-radius: 3px;
         white-space: pre-wrap; z-index: 10; }
  #banner { position: fixed; left: 50%; top: 10px; transform: translateX(-50%);
            display: none; background: #16210f; border: 1px solid #4caf50;
            color: #e8ffe8; font-size: 12px; padding: 6px 10px; border-radius: 4px;
            z-index: 20; max-width: 80%; }
  #banner button { background: #2b3a2b; color: #e8ffe8; border: 1px solid #4caf50;
            border-radius: 3px; font: inherit; padding: 1px 7px; margin-left: 8px;
            cursor: pointer; }
</style>
</head>
<body>
<canvas id="board"></canvas>
<div id="banner"></div>
<div id="legend" class="panel"></div>
<div id="tools" class="panel"></div>
<div id="card" class="panel"></div>
<div id="problems" class="panel"></div>
<div id="hud" class="panel"></div>
<div id="tip"></div>
<script>
const DATA = __DATA__;

const GRID_X = 132, GRID_Y = 94, NODE = 22, HALF = NODE / 2;
const MIN_ZOOM = 0.2, MAX_ZOOM = 1.6;
const HIGHLIGHT = 0xFFFFD37F;

const branches = DATA.branches;
const branchNames = DATA.branchList;
const effects = DATA.effects;
let nodes = DATA.nodes;

const canvas = document.getElementById('board');
const ctx = canvas.getContext('2d');
const tip = document.getElementById('tip');
const hud = document.getElementById('hud');
const legendBox = document.getElementById('legend');
const toolsBox = document.getElementById('tools');
const card = document.getElementById('card');
const problemsBox = document.getElementById('problems');
const banner = document.getElementById('banner');

let panX = 0, panY = 0, zoom = 0.6;
let hovered = -1, selected = -1, linkFrom = -1;
let isolate = null, colourByBranch = true, alwaysNames = false, showGrid = true;
let pan = null, nodeDrag = null, dragTarget = null;
let dirty = false, undoStack = [], problems = [], notes = [], problemPaths = new Set();
let index = new Map();
let bannerTimer = 0;

function f(id) { return document.getElementById(id); }
function width() { return canvas.width / devicePixelRatio; }
function height() { return canvas.height / devicePixelRatio; }
function branchVisible(name) { return !isolate || name === isolate; }
function nodeX(n) { return n.x * GRID_X; }
function nodeY(n) { return n.y * GRID_Y; }
function toTree(px, py) {
  return [(px - (width() / 2 + panX)) / zoom, (py - (height() / 2 + panY)) / zoom];
}
function nodeAt(px, py) {
  const [tx, ty] = toTree(px, py);
  for (let i = 0; i < nodes.length; i++) {
    if (Math.abs(tx - nodeX(nodes[i])) <= HALF && Math.abs(ty - nodeY(nodes[i])) <= HALF) return i;
  }
  return -1;
}
function occupied(x, y, except) {
  return nodes.some((n, i) => i !== except && n.x === x && n.y === y);
}
function centreOn(n) {
  panX = -nodeX(n) * zoom;
  panY = -nodeY(n) * zoom;
}
function fit() {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  nodes.forEach(n => {
    if (!branchVisible(n.branch)) return;
    x0 = Math.min(x0, nodeX(n)); y0 = Math.min(y0, nodeY(n));
    x1 = Math.max(x1, nodeX(n)); y1 = Math.max(y1, nodeY(n));
  });
  if (!isFinite(x0)) return;
  const pad = 90;
  zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(
    width() / (x1 - x0 + pad * 2), height() / (y1 - y0 + pad * 2))));
  panX = -(x0 + x1) / 2 * zoom;
  panY = -(y0 + y1) / 2 * zoom;
}
function route(start) {
  const lit = new Set();
  if (start < 0) return lit;
  const stack = [start];
  while (stack.length) {
    const i = stack.pop();
    if (lit.has(i)) continue;
    lit.add(i);
    nodes[i].req.forEach(j => { if (j !== undefined) stack.push(j); });
  }
  return lit;
}
function drawLine(x1, y1, x2, y2, width, colour) {
  const steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
  const cells = Math.max(1, Math.ceil(steps / width));
  ctx.fillStyle = colour;
  for (let i = 0; i <= cells; i++) {
    const t = i / cells;
    const x = Math.round(x1 + (x2 - x1) * t);
    const y = Math.round(y1 + (y2 - y1) * t);
    ctx.fillRect(x - width / 2, y - width / 2, width, width);
  }
}
function rgba(colour, alpha) {
  const a = ((colour >>> 24) & 255) / 255 * alpha;
  const r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
  return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
}

function lint() {
  const out = [], grey = [];
  const byPath = new Map(nodes.map(n => [n.path, n]));
  const cells = new Map(), keys = new Map();
  nodes.forEach(n => {
    const cell = n.x + ',' + n.y;
    if (cells.has(cell)) out.push({text: 'клетка ' + cell + ' занята дважды: ' + cells.get(cell) + ' и ' + n.path, path: n.path});
    else cells.set(cell, n.path);
    if (!branches[n.branch]) out.push({text: 'неизвестная ветка ' + n.branch + ' у ' + n.path, path: n.path});
    if (!n.name) out.push({text: 'нет EN-названия: ' + n.path, path: n.path});
    if (!n.name_ru) out.push({text: 'нет RU-названия: ' + n.path, path: n.path});
    if (n.unlock) {
      if (keys.has(n.unlock)) out.push({text: 'дубль ключа ' + n.unlock + ': ' + keys.get(n.unlock) + ' и ' + n.path, path: n.path});
      else keys.set(n.unlock, n.path);
    }
    if (n.unlock && n.requires.length) {
      const allTech = n.requires.every(r => { const o = byPath.get(r); return o && o.unlock; });
      if (allTech) out.push({text: 'техника висит на технике: ' + n.path + ' <- ' + n.requires.join(', '), path: n.path});
    }
    n.requires.forEach(r => {
      const o = byPath.get(r);
      if (!o) { out.push({text: 'неизвестный предок: ' + n.path + ' -> ' + r, path: n.path}); return; }
      const step = Math.max(Math.abs(n.x - o.x), Math.abs(n.y - o.y));
      const limit = o.path === 'root' ? 3 : 2;
      if (step > limit) out.push({text: 'длинная связь (' + step + ' шага): ' + n.path + ' -> ' + r, path: n.path});
      else if (step > 1) grey.push({text: step + '-шаговая связь: ' + n.path + ' -> ' + r});
    });
  });
  const seen = new Set(), stack = byPath.has('root') ? ['root'] : [];
  const children = new Map();
  nodes.forEach(n => n.requires.forEach(r => children.set(r, (children.get(r) || []).concat(n.path))));
  while (stack.length) {
    const p = stack.pop();
    if (seen.has(p)) continue;
    seen.add(p);
    (children.get(p) || []).forEach(c => stack.push(c));
  }
  nodes.forEach(n => { if (!seen.has(n.path)) out.push({text: 'недостижим из корня: ' + n.path, path: n.path}); });
  const boxes = {};
  nodes.forEach(n => {
    const b = boxes[n.branch] = boxes[n.branch] || [n.x, n.y, n.x, n.y];
    b[0] = Math.min(b[0], n.x); b[1] = Math.min(b[1], n.y);
    b[2] = Math.max(b[2], n.x); b[3] = Math.max(b[3], n.y);
  });
  const names = Object.keys(boxes).sort();
  names.forEach((a, i) => names.slice(i + 1).forEach(b => {
    if (a === 'ROOT' || b === 'ROOT') return;
    const ba = boxes[a], bb = boxes[b];
    if (ba[0] > bb[2] || bb[0] > ba[2] || ba[1] > bb[3] || bb[1] > ba[3]) return;
    grey.push({text: 'боксы веток пересекаются: ' + a + ' и ' + b});
  }));
  return {problems: out, notes: grey};
}

function reindex() {
  index = new Map();
  nodes.forEach((n, i) => index.set(n.path, i));
  nodes.forEach(n => { n.req = n.requires.map(p => index.get(p)); });
  const result = lint();
  problems = result.problems;
  notes = result.notes;
  problemPaths = new Set(problems.map(p => p.path).filter(Boolean));
}

function draw() {
  const w = width(), h = height();
  ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
  ctx.fillStyle = '#101418';
  ctx.fillRect(0, 0, w, h);
  ctx.save();
  ctx.translate(w / 2 + panX, h / 2 + panY);
  ctx.scale(zoom, zoom);

  const cell = Math.max(1, Math.ceil(1 / zoom));
  const lit = route(hovered >= 0 ? hovered : selected);

  if (showGrid) {
    ctx.strokeStyle = '#ffffff10';
    ctx.lineWidth = 1 / zoom;
    for (let gx = -20; gx <= 20; gx++) {
      ctx.beginPath(); ctx.moveTo(gx * GRID_X, -20 * GRID_Y);
      ctx.lineTo(gx * GRID_X, 20 * GRID_Y); ctx.stroke();
    }
    for (let gy = -20; gy <= 20; gy++) {
      ctx.beginPath(); ctx.moveTo(-20 * GRID_X, gy * GRID_Y);
      ctx.lineTo(20 * GRID_X, gy * GRID_Y); ctx.stroke();
    }
  }

  for (let pass = 0; pass < 2; pass++) {
    nodes.forEach((n, i) => {
      if (!branchVisible(n.branch)) return;
      const onRoute = lit.size > 1 && lit.has(i);
      if ((pass === 1) !== onRoute) return;
      n.req.forEach(j => {
        if (j === undefined) return;
        const colour = onRoute ? rgba(HIGHLIGHT, 1)
          : (colourByBranch ? rgba((branches[n.branch] || {colour: 0x888888}).colour, .55) : '#3a3a3a');
        drawLine(nodeX(nodes[j]), nodeY(nodes[j]), nodeX(n), nodeY(n),
                 onRoute ? cell + 1 : cell, colour);
      });
    });
  }

  if (dragTarget && !dragTarget.ok) {
    ctx.strokeStyle = '#ff5555';
    ctx.lineWidth = 2 / zoom;
    ctx.strokeRect(dragTarget.x * GRID_X - HALF, dragTarget.y * GRID_Y - HALF, NODE, NODE);
  }

  nodes.forEach((n, i) => {
    if (!branchVisible(n.branch)) return;
    const cx = nodeX(n), cy = nodeY(n);
    const b = (branches[n.branch] || {colour: 0x888888}).colour;
    const isHover = hovered === i, isSel = selected === i, isSrc = linkFrom === i;
    ctx.fillStyle = isSel ? '#ffffff' : (isSrc ? '#a06cff'
      : (problemPaths.has(n.path) ? '#ff5555' : rgba(b, .9)));
    ctx.fillRect(cx - HALF - 1, cy - HALF - 1, NODE + 2, NODE + 2);
    ctx.fillStyle = isHover ? '#3a3a3a' : '#1e1e1e';
    ctx.fillRect(cx - HALF, cy - HALF, NODE, NODE);
    ctx.fillStyle = '#ffe082';
    ctx.font = '10px Consolas, monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(n.cost >= 1000 ? (n.cost / 1000) + 'k' : n.cost), cx, cy - 3);
    if (alwaysNames || zoom >= 0.65 || isHover || isSel) {
      ctx.font = '8px Consolas, monospace';
      ctx.fillStyle = (isHover || isSel) ? '#ffffff' : '#d8d8d8';
      ctx.fillText(n.name, cx, cy + HALF + 7);
      ctx.font = '7px Consolas, monospace';
      ctx.fillStyle = '#9a9a9a';
      ctx.fillText(n.branch.slice(0, 4), cx, cy + HALF + 16);
    }
  });
  ctx.restore();

  hud.textContent = 'зум ' + zoom.toFixed(2) + '   узлов ' + nodes.length
    + '   наведение: ' + (hovered >= 0 ? nodes[hovered].path : '-')
    + '   ветка: ' + (isolate || 'все')
    + (dirty ? '   ● есть несохранённые правки' : '   правки сохранены') + '\n'
    + 'колесо = зум, ЛКМ по пустому = панорама, Shift+ЛКМ тоже, тянуть узел = переставить, '
    + 'клик по узлу = свойства, F = вписать, N = имена, G = сетка, Ctrl+Z = отмена, Ctrl+S = сохранить';
}

function buildLegend() {
  legendBox.innerHTML = '';
  const all = document.createElement('div');
  all.textContent = 'все ветки';
  all.onclick = () => { isolate = null; fit(); draw(); buildLegend(); };
  all.className = isolate ? 'off' : '';
  legendBox.appendChild(all);
  branchNames.forEach(name => {
    const row = document.createElement('div');
    row.innerHTML = '<span style="color:' + rgba(branches[name].colour, 1) + '">&#9632;</span> ' + name;
    row.className = isolate === name ? '' : (isolate ? 'off' : '');
    row.onclick = () => { isolate = isolate === name ? null : name; fit(); draw(); buildLegend(); };
    legendBox.appendChild(row);
  });
}

function buildTools() {
  toolsBox.innerHTML =
    '<button id="bSave">сохранить</button>' +
    '<button id="bAdd">+ узел</button>' +
    '<button id="bLink">связать</button>' +
    '<button id="bUndo">отмена</button>' +
    '<button id="bGrid">сетка</button>' +
    '<button id="bFit">вписать</button>' +
    '<div class="line" id="lProblems"></div>' +
    '<div class="line" id="lHint"></div>';
  f('bSave').onclick = save;
  f('bAdd').onclick = addNode;
  f('bLink').onclick = () => startLink();
  f('bUndo').onclick = undo;
  f('bGrid').onclick = () => { showGrid = !showGrid; draw(); };
  f('bFit').onclick = () => { fit(); draw(); };
  f('lProblems').onclick = toggleProblems;
  syncTools();
}

function syncTools() {
  const line = f('lProblems');
  line.innerHTML = problems.length
    ? '<span style="color:#ff8888">проблем: ' + problems.length + '</span> (клик — список)'
    : '<span style="color:#7fd18a">проблем нет</span>';
  f('lHint').textContent = dirty ? '● правки не сохранены (Ctrl+S)' : 'правки сохранены';
  f('bLink').className = linkFrom >= 0 ? 'on' : '';
  f('bGrid').className = showGrid ? 'on' : '';
}

function toggleProblems() {
  if (problemsBox.style.display === 'block') {
    problemsBox.style.display = 'none';
    return;
  }
  problemsBox.innerHTML = '';
  const add = (item, cls) => {
    const row = document.createElement('div');
    row.textContent = item.text;
    if (cls) row.className = cls;
    if (item.path) row.onclick = () => {
      const i = index.get(item.path);
      if (i === undefined) return;
      select(i); centreOn(nodes[i]); draw();
    };
    problemsBox.appendChild(row);
  };
  if (!problems.length) add({text: 'проблем нет'});
  problems.forEach(item => add(item));
  notes.forEach(item => add(item, 'note'));
  problemsBox.style.display = 'block';
}

function buildCard() {
  card.innerHTML =
    '<div class="row"><b id="cTitle"></b></div>' +
    '<div class="row"><label>путь</label><input id="fPath" type="text"></div>' +
    '<div class="row"><label>ветка</label><select id="fBranch"></select></div>' +
    '<div class="row"><label>клетка</label><input id="fX" type="number"> <input id="fY" type="number"></div>' +
    '<div class="row"><label>цена</label><input id="fCost" type="number" min="0" step="10"></div>' +
    '<div class="row"><label>техника</label><input id="fUnlock" type="text" placeholder="пусто = пассивка"></div>' +
    '<div class="row" id="rEffect"><label>эффект</label><select id="fEffect"></select></div>' +
    '<div class="row" id="rAmount"><label>величина</label><input id="fAmount" type="number" step="0.001"></div>' +
    '<div class="row"><label>предки</label><span id="fReq"></span> <button id="bReq">+</button></div>' +
    '<div class="row"><label>EN имя</label><input id="fNameEn" type="text"></div>' +
    '<div class="row"><label>EN описание</label><textarea id="fDescEn"></textarea></div>' +
    '<div class="row"><label>RU имя</label><input id="fNameRu" type="text"></div>' +
    '<div class="row"><label>RU описание</label><textarea id="fDescRu"></textarea></div>' +
    '<div class="row"><button id="bDelete">удалить узел</button></div>';

  f('fBranch').innerHTML = branchNames.map(n => '<option>' + n + '</option>').join('');
  f('fEffect').innerHTML = effects.map(n => '<option>' + n + '</option>').join('');

  f('fX').onchange = () => moveTo(+f('fX').value, +f('fY').value);
  f('fY').onchange = () => moveTo(+f('fX').value, +f('fY').value);
  f('fCost').onchange = () => { pushUndo(); nodes[selected].cost = Math.max(0, Math.round(+f('fCost').value || 0)); touch(); };
  f('fBranch').onchange = () => { pushUndo(); nodes[selected].branch = f('fBranch').value; touch(); };
  f('fUnlock').onchange = () => {
    pushUndo();
    const value = f('fUnlock').value.trim();
    nodes[selected].unlock = /^[a-z_]+:[a-z_]+$/.test(value) ? value : null;
    touch(); syncCard();
  };
  f('fEffect').onchange = () => { pushUndo(); nodes[selected].effect = f('fEffect').value; touch(); };
  f('fAmount').onchange = () => { pushUndo(); nodes[selected].amount = +f('fAmount').value || 0; touch(); };
  f('fNameEn').onchange = () => { pushUndo(); nodes[selected].name = f('fNameEn').value; touch(); };
  f('fDescEn').onchange = () => { pushUndo(); nodes[selected].desc = f('fDescEn').value; touch(); };
  f('fNameRu').onchange = () => { pushUndo(); nodes[selected].name_ru = f('fNameRu').value; touch(); };
  f('fDescRu').onchange = () => { pushUndo(); nodes[selected].desc_ru = f('fDescRu').value; touch(); };
  f('fPath').onchange = () => {
    const n = nodes[selected];
    const value = f('fPath').value.trim();
    if (value === n.path) return;
    if (!/^[a-z0-9_]+$/.test(value) || index.has(value)) { flash('путь занят или недопустим'); syncCard(); return; }
    pushUndo();
    const old = n.path;
    n.path = value;
    nodes.forEach(o => { o.requires = o.requires.map(r => r === old ? value : r); });
    touch(); syncCard();
  };
  f('fReq').onclick = event => {
    const chip = event.target.closest('.chip');
    if (!chip) return;
    pushUndo();
    nodes[selected].requires = nodes[selected].requires.filter(r => r !== chip.dataset.path);
    touch(); syncCard();
  };
  f('bReq').onclick = () => startLink();
  f('bDelete').onclick = deleteNode;
}

function syncCard() {
  if (selected < 0 || selected >= nodes.length) { card.style.display = 'none'; return; }
  const n = nodes[selected];
  card.style.display = 'block';
  f('cTitle').textContent = n.path + '  [' + n.branch + ']';
  f('fPath').value = n.path;
  f('fBranch').value = n.branch;
  f('fX').value = n.x;
  f('fY').value = n.y;
  f('fCost').value = n.cost;
  f('fUnlock').value = n.unlock || '';
  f('fEffect').value = n.effect || effects[0];
  f('fAmount').value = n.amount || 0;
  f('rEffect').style.display = n.unlock ? 'none' : '';
  f('rAmount').style.display = n.unlock ? 'none' : '';
  f('fReq').innerHTML = n.requires.length
    ? n.requires.map(r => '<span class="chip" data-path="' + r + '">' + r + ' ✕</span>').join('')
    : '<i style="color:#9a9a9a">нет</i>';
  f('fNameEn').value = n.name || '';
  f('fDescEn').value = n.desc || '';
  f('fNameRu').value = n.name_ru || '';
  f('fDescRu').value = n.desc_ru || '';
}

function select(i) {
  selected = i;
  syncCard();
  syncTools();
  draw();
}

function touch() {
  dirty = true;
  reindex();
  syncTools();
  draw();
}

function snapshot() { return JSON.stringify(nodes); }
function pushUndo() { undoStack.push(snapshot()); if (undoStack.length > 200) undoStack.shift(); }
function undo() {
  const state = undoStack.pop();
  if (!state) { flash('отменять нечего'); return; }
  nodes = JSON.parse(state);
  dirty = true;
  selected = -1;
  touch(); syncCard();
}

function flash(text) {
  showBanner(text);
  clearTimeout(bannerTimer);
  bannerTimer = setTimeout(hideBanner, 2600);
}

function showBanner(text, actionLabel, action) {
  banner.innerHTML = '';
  banner.appendChild(document.createTextNode(text));
  if (actionLabel && action) {
    const button = document.createElement('button');
    button.textContent = actionLabel;
    button.onclick = () => { action(); hideBanner(); };
    banner.appendChild(button);
  }
  const close = document.createElement('button');
  close.textContent = '×';
  close.onclick = hideBanner;
  banner.appendChild(close);
  banner.style.display = 'block';
}

function hideBanner() { banner.style.display = 'none'; }

function moveTo(x, y) {
  const n = nodes[selected];
  if (!isFinite(x) || !isFinite(y) || occupied(x, y, selected)) { flash('клетка занята'); syncCard(); return; }
  if (x === n.x && y === n.y) return;
  pushUndo();
  n.x = x; n.y = y;
  syncCard();
  touch();
}

function freeCell() {
  const [tx, ty] = toTree(width() / 2, height() / 2);
  const cx = Math.round(tx / GRID_X), cy = Math.round(ty / GRID_Y);
  for (let r = 0; r < 24; r++) {
    for (let dx = -r; dx <= r; dx++) {
      for (let dy = -r; dy <= r; dy++) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
        if (!occupied(cx + dx, cy + dy, -1)) return [cx + dx, cy + dy];
      }
    }
  }
  return [cx, cy];
}

function addNode() {
  const branch = (selected >= 0 && nodes[selected]) ? nodes[selected].branch : (isolate || 'ROOT');
  let path = 'new_node';
  let i = 1;
  while (index.has(path)) path = 'new_node_' + (++i);
  const cell = freeCell();
  pushUndo();
  nodes.push({
    path, branch, x: cell[0], y: cell[1], cost: 200, unlock: null,
    effect: effects[0], amount: 1, requires: [],
    name: path, desc: '', name_ru: '', desc_ru: '',
  });
  touch();
  select(nodes.length - 1);
  flash('узел ' + path + ' создан: заполните имя и предков');
}

function deleteNode() {
  if (selected < 0) return;
  const node = nodes[selected];
  if (!confirm('Удалить узел ' + node.path + '? Ссылки на него у других узлов тоже пропадут.')) return;
  pushUndo();
  nodes.splice(selected, 1);
  nodes.forEach(o => { o.requires = o.requires.filter(r => r !== node.path); });
  selected = -1;
  touch(); syncCard();
}

function startLink() {
  if (selected < 0) { flash('сначала выберите узел'); return; }
  linkFrom = selected;
  hideTip();
  syncTools();
  draw();
  flash('кликните узел-предок для ' + nodes[selected].path + ' (Esc — отмена)');
}

function tryLink(from, to) {
  linkFrom = -1;
  if (from === to) { flash('узел не может требовать сам себя'); return; }
  const src = nodes[from], dst = nodes[to];
  if (dst.requires.includes(src.path)) { flash('связь уже есть: ' + src.path + ' -> ' + dst.path); return; }
  if (route(from).has(to)) { flash('получится кольцо: ' + dst.path + ' уже выше ' + src.path); return; }
  pushUndo();
  dst.requires.push(src.path);
  touch();
  select(to);
}

function collect() {
  return {
    version: 1,
    savedAt: new Date().toISOString(),
    nodes: nodes.map(n => ({
      path: n.path, branch: n.branch, x: n.x, y: n.y, cost: n.cost,
      unlock: n.unlock || null,
      effect: n.unlock ? null : n.effect,
      amount: n.unlock ? 0 : n.amount,
      requires: n.requires.slice(),
      en: {name: n.name || '', desc: n.desc || ''},
      ru: {name: n.name_ru || '', desc: n.desc_ru || ''},
    })),
  };
}

async function save() {
  const text = JSON.stringify(collect(), null, 2);
  try {
    const response = await fetch('save', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: text,
    });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    dirty = false;
    syncTools();
    showBanner('Сохранено: ' + (await response.text()) + ' — теперь скажите агенту «примени правки».');
  } catch (error) {
    const blob = new Blob([text], {type: 'application/json'});
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'skilltree-edits.json';
    document.body.appendChild(link);
    link.click();
    link.remove();
    dirty = false;
    syncTools();
    showBanner('Сервер сохранения не запущен — файл скачан как skilltree-edits.json, положите его в tmp/ и скажите агенту «примени правки».');
  }
}

function normalise(list) {
  const out = [];
  (list || []).forEach(raw => {
    if (!raw || !raw.path || !branches[raw.branch]) return;
    const en = raw.en || {}, ru = raw.ru || {};
    out.push({
      path: String(raw.path), branch: raw.branch,
      x: Math.round(raw.x), y: Math.round(raw.y),
      cost: Math.max(0, Math.round(raw.cost || 0)),
      unlock: raw.unlock || null,
      effect: raw.effect || effects[0],
      amount: +raw.amount || 0,
      requires: (raw.requires || []).filter(r => typeof r === 'string'),
      name: en.name || String(raw.path), desc: en.desc || '',
      name_ru: ru.name || '', desc_ru: ru.desc || '',
    });
  });
  return out.length ? out : null;
}

function loadEdits(payload) {
  const clean = normalise(payload.nodes);
  if (!clean) { flash('в файле нет пригодных узлов'); return; }
  pushUndo();
  nodes = clean;
  selected = -1;
  touch(); syncCard();
  fit(); draw();
  flash('загружено ' + nodes.length + ' узлов из файла правок');
}

function showTip(i, event) {
  const n = nodes[i];
  tip.style.display = 'block';
  tip.style.left = Math.min(event.clientX + 14, innerWidth - 490) + 'px';
  tip.style.top = (event.clientY + 14) + 'px';
  tip.textContent = n.name + (n.name_ru ? '  /  ' + n.name_ru : '') + '\n'
    + n.branch + '   ' + (n.unlock ? 'техника: ' + n.unlock : 'пассивка: ' + n.effect + ' ' + n.amount) + '\n'
    + (n.desc || '') + (n.desc_ru ? '\n' + n.desc_ru : '') + '\n'
    + 'цена ' + n.cost + ' xp'
    + (n.requires.length ? '   предки: ' + n.requires.join(', ') : '   предков нет');
}
function hideTip() { tip.style.display = 'none'; }

window.addEventListener('resize', () => { resize(); draw(); });
function resize() {
  canvas.width = innerWidth * devicePixelRatio;
  canvas.height = innerHeight * devicePixelRatio;
  canvas.style.width = innerWidth + 'px';
  canvas.style.height = innerHeight + 'px';
}

canvas.addEventListener('contextmenu', event => event.preventDefault());
canvas.addEventListener('mousedown', event => {
  hideTip();
  if (event.button === 1 || event.button === 2 || event.shiftKey) {
    pan = {x: event.clientX, y: event.clientY};
    return;
  }
  if (event.button !== 0) return;
  const i = nodeAt(event.offsetX, event.offsetY);
  if (linkFrom >= 0) {
    if (i >= 0) tryLink(linkFrom, i); else { linkFrom = -1; syncTools(); draw(); }
    return;
  }
  if (i < 0) { pan = {x: event.clientX, y: event.clientY}; return; }
  nodeDrag = {index: i, moved: false, snapshot: snapshot()};
});
canvas.addEventListener('mousemove', event => {
  if (pan) { panX += event.movementX; panY += event.movementY; draw(); return; }
  if (nodeDrag) {
    const n = nodes[nodeDrag.index];
    const [tx, ty] = toTree(event.offsetX, event.offsetY);
    const cx = Math.round(tx / GRID_X), cy = Math.round(ty / GRID_Y);
    const ok = !occupied(cx, cy, nodeDrag.index);
    dragTarget = {x: cx, y: cy, ok: ok};
    if (ok && (cx !== n.x || cy !== n.y)) {
      n.x = cx; n.y = cy;
      nodeDrag.moved = true;
      dirty = true;
      syncCard();
      syncTools();
    }
    draw();
    return;
  }
  const i = nodeAt(event.offsetX, event.offsetY);
  if (i !== hovered) { hovered = i; draw(); }
  if (i >= 0) showTip(i, event); else hideTip();
});
canvas.addEventListener('mouseleave', () => { hideTip(); hovered = -1; draw(); });
window.addEventListener('mouseup', () => {
  if (pan) { pan = null; return; }
  if (!nodeDrag) return;
  dragTarget = null;
  if (nodeDrag.moved) {
    undoStack.push(nodeDrag.snapshot);
    if (undoStack.length > 200) undoStack.shift();
    syncTools();
  } else {
    select(nodeDrag.index);
  }
  nodeDrag = null;
  draw();
});
canvas.addEventListener('wheel', event => {
  event.preventDefault();
  const previous = zoom;
  zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.1, -Math.sign(event.deltaY))));
  const cx = width() / 2 + panX, cy = height() / 2 + panY;
  panX += (event.offsetX - cx) * (1 - zoom / previous);
  panY += (event.offsetY - cy) * (1 - zoom / previous);
  draw();
}, { passive: false });
window.addEventListener('keydown', event => {
  if (event.ctrlKey && (event.key === 'z' || event.key === 'Z')) {
    if (event.target === document.body) { undo(); event.preventDefault(); }
    return;
  }
  if (event.ctrlKey && (event.key === 's' || event.key === 'S')) {
    save();
    event.preventDefault();
    return;
  }
  if (event.target !== document.body) return;
  if (event.key === 'Escape') {
    if (linkFrom >= 0) { linkFrom = -1; syncTools(); draw(); } else select(-1);
    return;
  }
  if (event.key === 'f' || event.key === 'F') { fit(); draw(); }
  if (event.key === 'n' || event.key === 'N') { alwaysNames = !alwaysNames; draw(); }
  if (event.key === 'g' || event.key === 'G') { showGrid = !showGrid; syncTools(); draw(); }
  if (event.key === 'c' || event.key === 'C') { colourByBranch = !colourByBranch; draw(); }
});
window.addEventListener('beforeunload', event => {
  if (!dirty) return;
  event.preventDefault();
  event.returnValue = '';
});

// Hooks for screenshots, automated checks and the agent's own runs.
window.previewHover = path => { hovered = index.has(path) ? index.get(path) : -1; hideTip(); draw(); };
window.previewBranch = name => { isolate = branches[name] ? name : null; fit(); buildLegend(); draw(); };
window.previewFit = () => { fit(); draw(); };
window.previewSelect = path => { if (index.has(path)) { select(index.get(path)); centreOn(nodes[selected]); draw(); } };
window.previewMove = (path, x, y) => {
  if (!index.has(path)) return 'no such node';
  selected = index.get(path);
  moveTo(x, y);
  return nodes[selected].x + ',' + nodes[selected].y;
};
window.previewData = () => nodes;
window.previewCollect = () => collect();
window.previewSave = () => save();
window.previewProblems = () => problems.map(p => p.text);
window.previewState = () => ({
  zoom, panX, panY, dirty, selected: (nodes[selected] || {}).path || null, problems: problems.length,
  boxes: nodes.reduce((acc, n) => {
    const b = acc[n.branch] = acc[n.branch] || [1e9, 1e9, -1e9, -1e9];
    b[0] = Math.min(b[0], n.x); b[1] = Math.min(b[1], n.y);
    b[2] = Math.max(b[2], n.x); b[3] = Math.max(b[3], n.y);
    return acc;
  }, {}),
});

resize();
buildLegend();
buildTools();
buildCard();
reindex();
fit();
draw();

fetch('skilltree-edits.json', { cache: 'no-store' })
  .then(response => response.ok ? response.json() : null)
  .then(payload => {
    if (!payload || !Array.isArray(payload.nodes) || !payload.nodes.length) return;
    showBanner('Найден файл правок: ' + payload.nodes.length + ' узлов.', 'загрузить',
      () => loadEdits(payload));
  })
  .catch(() => {});
</script>
</body>
</html>
"""


def write_html(out: Path, nodes, branches, order, effects) -> None:
    payload = {
        "nodes": nodes,
        "branches": {
            name: {"colour": data["colour"]} for name, data in branches.items()
        },
        "branchList": order,
        "effects": effects,
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        HTML.replace("__DATA__", json.dumps(payload, ensure_ascii=False)),
        encoding="utf-8",
    )
    print("wrote %s" % out)


def serve(port: int, out: Path, open_browser: bool) -> int:
    import http.server

    edits = DEFAULT_EDITS

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(out.parent), **kwargs)

        def do_POST(self):
            if self.path.split("?")[0].rstrip("/") != "/save":
                self.send_error(404, "unknown endpoint")
                return
            try:
                length = int(self.headers.get("Content-Length") or 0)
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                count = len(payload["nodes"])
            except Exception as exc:  # noqa: BLE001 - report whatever the page sent
                self.send_error(400, "bad payload: %s" % exc)
                return
            edits.parent.mkdir(parents=True, exist_ok=True)
            edits.write_text(
                json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            message = "%s (%d nodes)" % (edits.relative_to(ROOT).as_posix(), count)
            data = message.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            print("saved %s" % message)

        def log_message(self, fmt, *args):
            pass

    url = "http://127.0.0.1:%d/%s" % (port, out.name)
    with http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler) as httpd:
        print("serving %s at %s" % (out.parent, url))
        print("save endpoint: POST /save -> %s" % edits.relative_to(ROOT).as_posix())
        print("Ctrl+C to stop")
        if open_browser:
            webbrowser.open(url)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nstopped")
    return 0


def report(nodes, branches, order, boxes, problems, notes) -> None:
    print("nodes: %d   branches: %d" % (len(nodes), len(branches)))
    for name in order:
        if name in boxes:
            print("  %-10s %s" % (name, boxes[name]))
    for note in notes:
        print("  note: " + note)
    if problems:
        print("problems:")
        for problem in problems:
            print("  - " + problem)
    else:
        print("no layout problems found")


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "out",
        nargs="?",
        default=str(DEFAULT_OUT),
        help="where to write the HTML preview",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="also dump the parsed table next to the HTML",
    )
    parser.add_argument(
        "--serve",
        nargs="?",
        const=8765,
        type=int,
        metavar="PORT",
        help="serve the preview with a /save endpoint (default port 8765)",
    )
    parser.add_argument(
        "--open", action="store_true", help="open the preview in the browser"
    )
    parser.add_argument(
        "--apply",
        nargs="?",
        const=str(DEFAULT_EDITS),
        metavar="EDITS",
        help="write an edits file back into Skill.java and the language files",
    )
    parser.add_argument("--check", action="store_true", help="lint only, write nothing")
    args = parser.parse_args()
    out = Path(args.out)

    if args.apply:
        code = apply_edits(Path(args.apply))
        if code:
            return code
        print()

    nodes = parse_skills()
    branches, order = parse_branches()
    effects = parse_effects()
    load_lang(nodes)
    problems, notes, boxes = lint(nodes, branches)
    report(nodes, branches, order, boxes, problems, notes)

    if args.check:
        return 1 if problems else 0

    write_html(out, nodes, branches, order, effects)
    if args.json:
        payload = {
            "nodes": nodes,
            "branches": {
                name: {"colour": data["colour"]} for name, data in branches.items()
            },
        }
        json_out = out.with_suffix(".json")
        json_out.write_text(
            json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        print("wrote %s" % json_out)

    if args.serve:
        return serve(args.serve, out, args.open)
    if args.open:
        webbrowser.open(out.resolve().as_uri())
    return 0


if __name__ == "__main__":
    sys.exit(main())
