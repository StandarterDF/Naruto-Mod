#!/usr/bin/env python3
"""Offline editor for the mod's skill tree.

Reads the node table straight out of ``Skill.java`` (plus the branch colours from
``SkillBranch.java``, the effect names from ``SkillEffect.java`` and the labels from the
language files) and writes a single self-contained HTML page that draws the board the way
``SkillTreeScreen`` does - same grid, same node boxes, same stepped links, hover route
highlight, pan and zoom - and lets that board be edited by hand:

* the same two-level tab bar the screen has: four sections on the top row, the branches of the
  active section below it, each tab carrying its node count (a dot marks a branch with lint
  problems, an empty dojutsu branch stays clickable and says it is still in development);
* ``обзор`` lays every board out side by side, so all sixteen branches fit on one screen and a
  click on a board opens it - sixteen overlapping boards would be unreadable;
* a search box (Ctrl+F or ``/``) finds a node by path, English or Russian name, technique key or
  branch and jumps to it across boards, and the right-hand panel lists the nodes of the board;
* each board remembers its own pan and zoom, ``[`` and ``]`` cycle through the branches;
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
SECTION_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillSection.java"
EFFECT_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillEffect.java"
LANG_EN = ROOT / "src/main/resources/assets/narutomod/lang/en_us.json"
LANG_RU = ROOT / "src/main/resources/assets/narutomod/lang/ru_ru.json"
DEFAULT_OUT = ROOT / "tmp/skilltree-preview.html"
DEFAULT_EDITS = ROOT / "tmp/skilltree-edits.json"

DECL = re.compile(
    r'(?s)(node|unlock)\(\s*"([a-z0-9_]+)"\s*,\s*SkillBranch\.([A-Z_]+)\s*,\s*'
    r"(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,(.*?)\)\s*;"
)
QUOTED = re.compile(r'"([a-z_:]+)"')
EFFECT_DECL = re.compile(r"SkillEffect\.([A-Z_]+)\s*,\s*([0-9.]+)f")
BRANCH = re.compile(
    r'([A-Z_]+)\(\s*(?:SkillSection\.([A-Z_]+)\s*,\s*)?"([^"]+)"\s*,\s*(0x[0-9A-Fa-f]+)\)'
)
SECTION = re.compile(r'([A-Z_]+)\(\s*"([^"]+)"\s*,\s*(0x[0-9A-Fa-f]+)\s*\)')
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
        name, section, key, colour = match.groups()
        branches[name] = {
            "key": key,
            "colour": int(colour, 16),
            "section": section,
        }
        order.append(name)
    return branches, order


def parse_sections():
    """The first level of the tab bar; branches name their section."""
    sections = {}
    order = []
    for match in SECTION.finditer(read_text(SECTION_JAVA)):
        name, key, colour = match.groups()
        sections[name] = {"key": key, "colour": int(colour, 16)}
        order.append(name)
    return sections, order


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


def load_tab_lang(branches, sections):
    """Tab labels come from the same language files as the in-game tab bar.

    The editor shows the Russian names (the page is Russian) and keeps the English ones for the
    tooltips, so a board reads "Медицина" instead of a raw enum constant. Returns the hint the game
    prints for a branch that has no nodes yet.
    """

    def load(path):
        try:
            return json.loads(read_text(path))
        except FileNotFoundError:
            return {}

    en, ru = load(LANG_EN), load(LANG_RU)
    for table in (branches, sections):
        for name, data in table.items():
            key = data["key"]
            data["label"] = ru.get(key) or en.get(key) or name
            data["label_en"] = en.get(key) or name
    return ru.get("naruto.gui.skill.section.empty") or en.get(
        "naruto.gui.skill.section.empty", ""
    )


def lint(nodes, branches):
    problems = []
    notes = []
    by_path = {}
    for node in nodes:
        if node["path"] in by_path:
            problems.append("duplicate path: %s" % node["path"])
        by_path[node["path"]] = node

    # Each branch is its own board with exactly one centre - the node nobody has to precede.
    centres = {}
    for node in nodes:
        if not node["requires"]:
            if node["branch"] in centres:
                problems.append(
                    "two centres in %s: %s and %s"
                    % (node["branch"], centres[node["branch"]], node["path"])
                )
            centres[node["branch"]] = node["path"]

    cells = {}
    keys = {}
    for node in nodes:
        # Cells are local to a board, so the same coordinate may be reused across tabs.
        cell = (node["branch"], node["x"], node["y"])
        if cell in cells:
            problems.append(
                "duplicate cell %s/%s: %s and %s"
                % (node["branch"], (node["x"], node["y"]), cells[cell], node["path"])
            )
        cells[cell] = node["path"]
        if node["branch"] not in branches:
            problems.append("unknown branch %s on %s" % (node["branch"], node["path"]))
        for lang, key in (("en", "name"), ("ru", "name_ru")):
            if not node[key]:
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
            # Rinnegan is deliberately gated on Sharingan mastery, so its cross-tab
            # prerequisites are an intentional exception to the per-board rule.
            is_rinnegan_sharingan = (
                node["path"] == "eye_rinnegan" and other["branch"] == "SHARINGAN"
            )
            if other["branch"] != node["branch"] and not is_rinnegan_sharingan:
                problems.append(
                    "cross-tab prerequisite %s (%s) -> %s (%s)"
                    % (node["path"], node["branch"], req, other["branch"])
                )
                continue
            if is_rinnegan_sharingan:
                continue
            step = max(abs(node["x"] - other["x"]), abs(node["y"] - other["y"]))
            # The star spokes leaving a tab centre are longer on purpose; every other link is short.
            limit = 4 if other["path"] == centres.get(node["branch"]) else 2
            if step > limit:
                problems.append(
                    "long link (%d steps): %s -> %s" % (step, node["path"], req)
                )
            elif step > 1:
                notes.append("%d-step link: %s -> %s" % (step, node["path"], req))

    # Reachability from each tab's own centre.
    children = {}
    for node in nodes:
        for req in node["requires"]:
            children.setdefault(req, []).append(node["path"])
    for branch, centre in centres.items():
        seen = set()
        stack = [centre]
        while stack:
            path = stack.pop()
            if path in seen:
                continue
            seen.add(path)
            stack.extend(children.get(path, []))
        for node in nodes:
            if node["branch"] == branch and node["path"] not in seen:
                problems.append(
                    "unreachable from %s centre: %s" % (branch, node["path"])
                )

    # Every branch that has nodes must have a centre; a missing one means its nodes have no entry
    # point at all. A branch with no nodes (the reserved dojutsu boards) is a legal empty tab.
    populated = {node["branch"] for node in nodes}
    for branch in branches:
        if branch in populated and branch not in centres:
            problems.append("branch %s has no centre" % branch)

    boxes = {}
    for node in nodes:
        box = boxes.setdefault(
            node["branch"], [node["x"], node["y"], node["x"], node["y"]]
        )
        box[0] = min(box[0], node["x"])
        box[1] = min(box[1], node["y"])
        box[2] = max(box[2], node["x"])
        box[3] = max(box[3], node["y"])

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

    try:
        shown = edits_path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        shown = str(edits_path)
    print("applied %s" % shown)
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
  .panel button { background: #23292f; color: #ddd; border: 1px solid #444;
        border-radius: 3px; font: inherit; padding: 2px 7px; margin: 0 4px 4px 0;
        cursor: pointer; }
  .panel button:hover { background: #2f3740; }
  .panel button.on { border-color: #a06cff; color: #fff; }

  /* Two-level tab bar, exactly like the screen: sections on top, branches below. */
  #tabs { left: 342px; right: 214px; top: 8px; padding: 5px 7px; z-index: 6; }
  #tabs .tabs { display: flex; flex-wrap: wrap; gap: 3px; }
  #tabs .tabs + .tabs { margin-top: 4px; }
  #tabs button { margin: 0; }
  #tabs button.empty { color: #8a8a8a; font-style: italic; }
  #tabs button.warn { border-color: #a04a4a; }
  #tabs button .n { color: #8fa3b8; }

  /* Board facts and the node list of the active board. */
  #info { right: 8px; top: 8px; width: 198px; max-height: calc(100% - 16px); overflow: auto; }
  #info .head { color: #7a8794; font-size: 10px; letter-spacing: .04em;
                text-transform: uppercase; margin: 6px 0 2px; }
  #info .head:first-child { margin-top: 0; }
  #info .row { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #info .node { cursor: pointer; padding-left: 2px; }
  #info .node:hover { color: #fff; }
  #info .node.sel { color: #ffe082; }
  #info .ok { color: #7fd18a; }
  #info .bad { color: #ff8888; }
  #info .muted { color: #8a8a8a; }

  #tools { left: 8px; top: 8px; width: 320px; z-index: 5; }
  #tools .line { line-height: 1.5; }
  #tools .search { position: relative; margin: 2px 0 4px; }
  #tools input[type=search] { width: 100%; box-sizing: border-box; background: #1a1f24;
        color: #eee; border: 1px solid #3a424a; border-radius: 3px; font: inherit;
        padding: 2px 6px; }
  #hits { display: none; position: absolute; left: 0; right: 0; top: 100%; margin-top: 2px;
          max-height: 280px; overflow: auto; background: #0b0e12; border: 1px solid #4a5568;
          border-radius: 3px; z-index: 30; }
  #hits .hit { padding: 2px 6px; cursor: pointer; border-bottom: 1px solid #1e242a; }
  #hits .hit:last-child { border-bottom: 0; }
  #hits .hit:hover { background: #232c36; }
  #hits .hit .where { color: #7a8794; }
  #hits .none { padding: 2px 6px; color: #8a8a8a; }

  #card { left: 8px; top: 176px; width: 320px; display: none;
          max-height: calc(100% - 240px); overflow: auto; z-index: 5; }
  #card .row { margin: 3px 0; }
  #card label { display: inline-block; width: 72px; color: #9a9a9a; vertical-align: top; }
  #card input, #card select, #card textarea { background: #1a1f24; color: #eee;
        border: 1px solid #3a424a; border-radius: 3px; font: inherit; padding: 1px 4px; }
  #card input[type=number] { width: 58px; }
  #card input[type=text] { width: 218px; }
  #card textarea { width: 218px; height: 30px; }
  .chip { display: inline-block; background: #2a3140; border: 1px solid #4a5568;
          border-radius: 8px; padding: 0 6px; margin: 1px 3px 1px 0; cursor: pointer; }
  .chip:hover { border-color: #ff6b6b; color: #fff; }

  #problems { left: 8px; top: 8px; width: 480px; display: none;
              max-height: 76%; overflow: auto; z-index: 20; position: relative; }
  #problems div { cursor: pointer; }
  #problems div:hover { color: #fff; }
  #problems .note { color: #9a9a9a; }

  #hud { left: 8px; bottom: 8px; z-index: 5; }
  #tip { position: fixed; display: none; pointer-events: none; max-width: 480px;
         background: #14001fee; border: 1px solid #a06cff; color: #eee;
         font-size: 12px; padding: 7px 9px; border-radius: 3px;
         white-space: pre-wrap; z-index: 10; }
  #banner { position: fixed; left: 50%; top: 80px; transform: translateX(-50%);
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
<div id="tabs" class="panel">
  <div id="rowSections" class="tabs"></div>
  <div id="rowBranches" class="tabs"></div>
</div>
<div id="info" class="panel"></div>
<div id="tools" class="panel"></div>
<div id="card" class="panel"></div>
<div id="problems" class="panel"></div>
<div id="hud" class="panel"></div>
<div id="tip"></div>
<script>
const DATA = __DATA__;

const GRID_X = 132, GRID_Y = 94, NODE = 22, HALF = NODE / 2;
const MIN_ZOOM = 0.08, MAX_ZOOM = 1.6;
const HIGHLIGHT = 0xFFFFD37F;

const branches = DATA.branches;
const branchNames = DATA.branchList;
const sections = DATA.sections || {};
const sectionNames = DATA.sectionList || [];
const effects = DATA.effects;
const emptyHint = DATA.emptyHint || 'Ветка пока пуста.';
let nodes = DATA.nodes;

const canvas = document.getElementById('board');
const ctx = canvas.getContext('2d');
const tip = document.getElementById('tip');
const hud = document.getElementById('hud');
const infoBox = document.getElementById('info');
const toolsBox = document.getElementById('tools');
const card = document.getElementById('card');
const problemsBox = document.getElementById('problems');
const banner = document.getElementById('banner');

// One board at a time, like in game; "overview" lays all boards out side by side so the whole
// tree (16 branches) can be seen at once. Views are remembered per board.
let mode = 'board';
let board = branchNames.find(name => nodes.some(n => n.branch === name)) || branchNames[0] || null;
let activeSection = board && branches[board] ? branches[board].section : (sectionNames[0] || null);
let views = new Map();
let overviewView = null;
let layout = {pos: new Map(), visible: new Set(), frames: []};

let panX = 0, panY = 0, zoom = 0.6;
let hovered = -1, selected = -1, linkFrom = -1;
let colourByBranch = true, alwaysNames = false, showGrid = true;
let pan = null, nodeDrag = null, dragTarget = null;
let dirty = false, undoStack = [], problems = [], notes = [], problemPaths = new Set();
let index = new Map();
let bannerTimer = 0;

function f(id) { return document.getElementById(id); }
function width() { return canvas.width / devicePixelRatio; }
function height() { return canvas.height / devicePixelRatio; }
function branchLabel(name) { return (branches[name] && branches[name].label) || name; }
function sectionLabel(name) { return (sections[name] && sections[name].label) || name; }
function nodeX(n) { const p = layout.pos.get(n.path); return p ? p.x : n.x * GRID_X; }
function nodeY(n) { const p = layout.pos.get(n.path); return p ? p.y : n.y * GRID_Y; }
function toTree(px, py) {
  return [(px - (width() / 2 + panX)) / zoom, (py - (height() / 2 + panY)) / zoom];
}
function toScreen(wx, wy) {
  return [width() / 2 + panX + wx * zoom, height() / 2 + panY + wy * zoom];
}
function nodeAt(px, py) {
  const [tx, ty] = toTree(px, py);
  const tolerance = mode === 'overview' ? Math.max(HALF, 10 / zoom) : HALF;
  for (let i = 0; i < nodes.length; i++) {
    if (!layout.visible.has(nodes[i].path)) continue;
    if (Math.abs(tx - nodeX(nodes[i])) <= tolerance
        && Math.abs(ty - nodeY(nodes[i])) <= tolerance) return i;
  }
  return -1;
}
function frameAt(px, py) {
  if (mode !== 'overview') return null;
  const [tx, ty] = toTree(px, py);
  return layout.frames.find(fr => tx >= fr.x && tx <= fr.x + fr.w
    && ty >= fr.y && ty <= fr.y + fr.h) || null;
}
function occupied(x, y, except) {
  const branch = (except >= 0 && nodes[except]) ? nodes[except].branch : (mode === 'board' ? board : null);
  return nodes.some((n, i) => i !== except && n.x === x && n.y === y && (!branch || n.branch === branch));
}
function centreOn(n) {
  panX = -nodeX(n) * zoom;
  panY = -nodeY(n) * zoom;
}
function fit() {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  if (mode === 'overview') {
    layout.frames.forEach(fr => {
      x0 = Math.min(x0, fr.x); y0 = Math.min(y0, fr.y);
      x1 = Math.max(x1, fr.x + fr.w); y1 = Math.max(y1, fr.y + fr.h);
    });
  } else {
    nodes.forEach(n => {
      if (n.branch !== board) return;
      x0 = Math.min(x0, n.x * GRID_X); y0 = Math.min(y0, n.y * GRID_Y);
      x1 = Math.max(x1, n.x * GRID_X); y1 = Math.max(y1, n.y * GRID_Y);
    });
  }
  // Centre inside the space the fixed panels leave free: the tab bar owns the top, the board facts
  // own the right edge and the HUD the bottom, so nothing important hides under them.
  const left = 10, right = 214, top = 146, bottom = 52;
  const availW = Math.max(200, width() - left - right);
  const availH = Math.max(200, height() - top - bottom);
  const screenX = left + availW / 2, screenY = top + availH / 2;
  if (!isFinite(x0)) {
    zoom = 0.8;
    panX = screenX - width() / 2;
    panY = screenY - height() / 2;
    return;
  }
  const pad = mode === 'overview' ? 30 : 70;
  zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(
    availW / (x1 - x0 + pad * 2), availH / (y1 - y0 + pad * 2))));
  panX = screenX - width() / 2 - (x0 + x1) / 2 * zoom;
  panY = screenY - height() / 2 - (y0 + y1) / 2 * zoom;
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
function drawLine(x1, y1, x2, y2, lineWidth, colour) {
  const steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
  const cells = Math.max(1, Math.ceil(steps / lineWidth));
  ctx.fillStyle = colour;
  for (let i = 0; i <= cells; i++) {
    const t = i / cells;
    const x = Math.round(x1 + (x2 - x1) * t);
    const y = Math.round(y1 + (y2 - y1) * t);
    ctx.fillRect(x - lineWidth / 2, y - lineWidth / 2, lineWidth, lineWidth);
  }
}
function rgba(colour, alpha) {
  const a = ((colour >>> 24) & 255) / 255 * alpha;
  const r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
  return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
}
function clipText(text, maxWidth) {
  if (ctx.measureText(text).width <= maxWidth) return text;
  let out = text;
  while (out.length > 1 && ctx.measureText(out + '…').width > maxWidth) out = out.slice(0, -1);
  return out + '…';
}
function wrapText(text, maxWidth) {
  const words = String(text).split(/\s+/);
  const lines = [];
  let line = '';
  words.forEach(word => {
    const test = line ? line + ' ' + word : word;
    if (ctx.measureText(test).width > maxWidth && line) { lines.push(line); line = word; }
    else line = test;
  });
  if (line) lines.push(line);
  return lines;
}

function lint() {
  const out = [], grey = [];
  const byPath = new Map(nodes.map(n => [n.path, n]));
  const cells = new Map(), keys = new Map();
  // Each board is its own coordinate space, so a cell is unique only inside one branch, and every
  // populated branch has exactly one centre - the node with no prerequisites.
  const centres = new Map();
  nodes.forEach(n => {
    const cell = n.branch + '/' + n.x + ',' + n.y;
    if (cells.has(cell)) out.push({text: 'клетка ' + cell + ' занята дважды: ' + cells.get(cell) + ' и ' + n.path, path: n.path});
    else cells.set(cell, n.path);
    if (!branches[n.branch]) out.push({text: 'неизвестная ветка ' + n.branch + ' у ' + n.path, path: n.path});
    if (!n.name) out.push({text: 'нет EN-названия: ' + n.path, path: n.path});
    if (!n.name_ru) out.push({text: 'нет RU-названия: ' + n.path, path: n.path});
    if (!n.requires.length) {
      if (centres.has(n.branch)) out.push({text: 'два центра в ' + n.branch + ': ' + centres.get(n.branch) + ' и ' + n.path, path: n.path});
      else centres.set(n.branch, n.path);
    }
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
      // Rinnegan is deliberately gated on Sharingan mastery (cross-tab by design).
      const isRinneganSharingan = (n.path === 'eye_rinnegan' && o.branch === 'SHARINGAN');
      if (o.branch !== n.branch && !isRinneganSharingan) {
        out.push({text: 'межветковая связь: ' + n.path + ' (' + n.branch + ') -> ' + r + ' (' + o.branch + ')', path: n.path});
        return;
      }
      if (isRinneganSharingan) return;
      const step = Math.max(Math.abs(n.x - o.x), Math.abs(n.y - o.y));
      const limit = o.path === centres.get(n.branch) ? 4 : 2;
      if (step > limit) out.push({text: 'длинная связь (' + step + ' шага): ' + n.path + ' -> ' + r, path: n.path});
      else if (step > 1) grey.push({text: step + '-шаговая связь: ' + n.path + ' -> ' + r});
    });
  });
  // Reachability is per board: every node must be walkable from its own centre.
  const children = new Map();
  nodes.forEach(n => n.requires.forEach(r => children.set(r, (children.get(r) || []).concat(n.path))));
  centres.forEach((centre, branch) => {
    const seen = new Set(), stack = [centre];
    while (stack.length) {
      const p = stack.pop();
      if (seen.has(p)) continue;
      seen.add(p);
      (children.get(p) || []).forEach(c => stack.push(c));
    }
    nodes.forEach(n => {
      if (n.branch === branch && !seen.has(n.path)) {
        out.push({text: 'недостижим из центра ' + branch + ': ' + n.path, path: n.path});
      }
    });
  });
  // Boards are independent (each branch is its own tab in game), so overlapping branch boxes are
  // not a problem any more - only cells inside one board have to stay unique.
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

// Places the nodes for the current mode. A board is the same grid the game uses; the overview is a
// shelf packing of all the boards, so nothing overlaps even with sixteen of them.
function recomputeLayout() {
  layout = {pos: new Map(), visible: new Set(), frames: []};
  if (mode === 'board') {
    nodes.forEach(n => {
      if (n.branch !== board) return;
      layout.visible.add(n.path);
      layout.pos.set(n.path, {x: n.x * GRID_X, y: n.y * GRID_Y});
    });
    return;
  }
  const CAP_MIN = 1600, GAP = 60, TITLE = 34, PAD = 34;
  // Two passes: measure every board first, then pick a shelf width that balances the overview
  // against the window. A fixed cap left one tall column with half the screen empty.
  const boards = branchNames.map(name => {
    const list = nodes.filter(n => n.branch === name);
    let minX = 0, maxX = 0, minY = 0, maxY = 0;
    list.forEach(n => {
      minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
      minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
    });
    const w = list.length ? (maxX - minX) * GRID_X + PAD * 2 : 320;
    const h = list.length ? (maxY - minY) * GRID_Y + PAD * 2 + TITLE : 92;
    return {name, list, minX, minY, w, h};
  });
  const area = boards.reduce((sum, b) => sum + (b.w + GAP) * (b.h + GAP), 0);
  const aspect = Math.max(0.6, width() / Math.max(1, height()));
  // 0.72 is a rough shelf-packing efficiency; the exact number only changes how square the map is.
  const cap = Math.max(CAP_MIN, Math.sqrt(area * aspect / 0.72));

  let cursorX = 0, cursorY = 0, rowHeight = 0;
  boards.forEach(b => {
    const {name, list, minX, minY, w, h} = b;
    if (cursorX > 0 && cursorX + w > cap) { cursorX = 0; cursorY += rowHeight + GAP; rowHeight = 0; }
    const frame = {branch: name, x: cursorX, y: cursorY, w: w, h: h};
    const ox = cursorX + PAD - minX * GRID_X;
    const oy = cursorY + TITLE + PAD - minY * GRID_Y;
    list.forEach(n => {
      layout.visible.add(n.path);
      layout.pos.set(n.path, {x: ox + n.x * GRID_X, y: oy + n.y * GRID_Y});
    });
    layout.frames.push(frame);
    cursorX += w + GAP;
    rowHeight = Math.max(rowHeight, h);
  });
  layout.width = Math.max(cap, cursorX);
  layout.height = cursorY + rowHeight;
}

function saveView() {
  if (mode === 'overview') overviewView = {panX: panX, panY: panY, zoom: zoom};
  else if (board) views.set(board, {panX: panX, panY: panY, zoom: zoom});
}
function restoreView() {
  const view = mode === 'overview' ? overviewView : (board ? views.get(board) : null);
  if (view) { panX = view.panX; panY = view.panY; zoom = view.zoom; }
  else fit();
}

function switchBoard(name) {
  if (!branches[name]) return;
  saveView();
  linkFrom = -1;
  mode = 'board';
  board = name;
  activeSection = branches[name].section;
  if (selected >= 0 && nodes[selected] && nodes[selected].branch !== board) { selected = -1; syncCard(); }
  recomputeLayout();
  buildTabs();
  buildInfo();
  syncTools();
  restoreView();
  draw();
}

function showOverview() {
  saveView();
  linkFrom = -1;
  mode = 'overview';
  recomputeLayout();
  buildTabs();
  buildInfo();
  restoreView();
  syncTools();
  draw();
}

function switchSection(name) {
  if (!sections[name] && name !== '—') return;
  activeSection = name;
  const first = branchNames.find(n => branches[n].section === name);
  if (first) switchBoard(first);
  else { buildTabs(); buildInfo(); draw(); }
}

function cycleBoard(step) {
  const list = branchNames;
  if (!list.length) return;
  const at = list.indexOf(board);
  const next = list[((at < 0 ? 0 : at) + step + list.length) % list.length];
  switchBoard(next);
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

  if (showGrid && mode === 'board') {
    let gx0 = -6, gx1 = 6, gy0 = -6, gy1 = 6;
    if (layout.visible.size) {
      gx0 = Infinity; gy0 = Infinity; gx1 = -Infinity; gy1 = -Infinity;
      nodes.forEach(n => {
        if (n.branch !== board) return;
        gx0 = Math.min(gx0, n.x); gy0 = Math.min(gy0, n.y);
        gx1 = Math.max(gx1, n.x); gy1 = Math.max(gy1, n.y);
      });
      gx0 -= 1; gy0 -= 1; gx1 += 1; gy1 += 1;
    }
    ctx.strokeStyle = '#ffffff10';
    ctx.lineWidth = 1 / zoom;
    for (let gx = gx0; gx <= gx1; gx++) {
      ctx.beginPath(); ctx.moveTo(gx * GRID_X, gy0 * GRID_Y);
      ctx.lineTo(gx * GRID_X, gy1 * GRID_Y); ctx.stroke();
    }
    for (let gy = gy0; gy <= gy1; gy++) {
      ctx.beginPath(); ctx.moveTo(gx0 * GRID_X, gy * GRID_Y);
      ctx.lineTo(gx1 * GRID_X, gy * GRID_Y); ctx.stroke();
    }
  }

  for (let pass = 0; pass < 2; pass++) {
    nodes.forEach((n, i) => {
      if (!layout.visible.has(n.path)) return;
      const onRoute = lit.size > 1 && lit.has(i);
      if ((pass === 1) !== onRoute) return;
      n.req.forEach(j => {
        if (j === undefined) return;
        const other = nodes[j];
        if (!other || !layout.visible.has(other.path)) return;
        const colour = onRoute ? rgba(HIGHLIGHT, 1)
          : (colourByBranch ? rgba((branches[n.branch] || {colour: 0x888888}).colour, .55) : '#3a3a3a');
        drawLine(nodeX(other), nodeY(other), nodeX(n), nodeY(n),
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
    if (!layout.visible.has(n.path)) return;
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
    if (mode === 'board' && (alwaysNames || zoom >= 0.65 || isHover || isSel)) {
      ctx.font = '8px Consolas, monospace';
      ctx.fillStyle = (isHover || isSel) ? '#ffffff' : '#d8d8d8';
      ctx.fillText(n.name, cx, cy + HALF + 7);
      ctx.font = '7px Consolas, monospace';
      ctx.fillStyle = '#9a9a9a';
      ctx.fillText(n.branch.slice(0, 4), cx, cy + HALF + 16);
    }
  });
  ctx.restore();

  // Board frames and empty-board messages are drawn in screen space, so the titles stay readable
  // whatever the zoom is.
  if (mode === 'overview') {
    layout.frames.forEach(fr => {
      const [sx, sy] = toScreen(fr.x, fr.y);
      const sw = fr.w * zoom, sh = fr.h * zoom;
      const active = fr.branch === board;
      ctx.fillStyle = active ? '#ffffff0a' : '#ffffff05';
      ctx.fillRect(sx, sy, sw, sh);
      ctx.strokeStyle = rgba((branches[fr.branch] || {colour: 0x888888}).colour, active ? .85 : .45);
      ctx.lineWidth = 1;
      ctx.strokeRect(Math.round(sx) + .5, Math.round(sy) + .5, Math.round(sw), Math.round(sh));
      const bar = Math.min(sh, 22);
      ctx.fillStyle = '#0007';
      ctx.fillRect(sx, sy, sw, bar);
      const count = nodes.filter(n => n.branch === fr.branch).length;
      const titleRoom = Math.max(18, sw - (count ? 34 : 12));
      ctx.fillStyle = '#e8e8e8';
      ctx.font = 'bold 11px Consolas, monospace';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      ctx.fillText(clipText(branchLabel(fr.branch), titleRoom), sx + 6, sy + bar / 2);
      if (count) {
        ctx.fillStyle = '#8fa3b8';
        ctx.textAlign = 'right';
        ctx.fillText(String(count), sx + sw - 6, sy + bar / 2);
      }
      if (!count && sh > 50) {
        ctx.fillStyle = '#8a8a8a';
        ctx.font = '11px Consolas, monospace';
        ctx.textAlign = 'center';
        ctx.fillText('в разработке', sx + sw / 2, sy + bar + (sh - bar) / 2);
      }
    });
  } else if (!layout.visible.size) {
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#b9b9b9';
    ctx.font = '14px Consolas, monospace';
    const lines = wrapText(emptyHint, Math.min(560, w - 80));
    lines.forEach((line, k) => ctx.fillText(line, w / 2, h / 2 - 20 + k * 20));
    ctx.fillStyle = '#7a8794';
    ctx.font = '12px Consolas, monospace';
    ctx.fillText('нажмите «+ узел», чтобы начать', w / 2, h / 2 + 18 + lines.length * 20);
  }

  const bits = [];
  bits.push(mode === 'overview' ? 'режим: обзор' : 'ветка: ' + branchLabel(board) + ' · ' + board);
  bits.push('узлов ' + nodes.length);
  if (mode === 'board') bits.push('на доске ' + layout.visible.size);
  bits.push('зум ' + zoom.toFixed(2));
  bits.push('наведение: ' + (hovered >= 0 ? nodes[hovered].path : '-'));
  hud.textContent = bits.join('   ') + (dirty ? '   ● не сохранено' : '') + '\n'
    + 'колесо = зум · ЛКМ по пустому = панорама · тянуть узел = переставить · клик = свойства · '
    + '[ ] = ветки · O = обзор · Ctrl+F = поиск · F = вписать · N = имена · G = сетка · '
    + 'Ctrl+Z = отмена · Ctrl+S = сохранить';
}

function tabButton(label, opts) {
  const button = document.createElement('button');
  button.textContent = label;
  if (opts.title) button.title = opts.title;
  if (opts.cls) button.className = opts.cls;
  if (opts.on) button.classList.add('on');
  button.onclick = opts.onclick;
  return button;
}

function buildTabs() {
  const sectionRow = f('rowSections');
  const branchRow = f('rowBranches');
  sectionRow.innerHTML = '';
  branchRow.innerHTML = '';

  sectionRow.appendChild(tabButton('обзор', {
    on: mode === 'overview', title: 'Все доски сразу (O)', onclick: showOverview,
  }));
  const current = mode === 'board' && branches[board] ? branches[board].section : activeSection;
  sectionNames.forEach(name => {
    const count = nodes.filter(n => (branches[n.branch] || {}).section === name).length;
    sectionRow.appendChild(tabButton(sectionLabel(name) + ' · ' + count, {
      on: mode === 'board' && current === name,
      title: ((sections[name] || {}).labelEn || name) + ' — ' + count + ' узлов',
      cls: count ? '' : 'empty',
      onclick: () => switchSection(name),
    }));
  });

  const visible = branchNames.filter(name => !current || (branches[name].section || null) === current);
  visible.forEach(name => {
    const count = nodes.filter(n => n.branch === name).length;
    const warn = nodes.some(n => n.branch === name && problemPaths.has(n.path));
    const b = branches[name] || {};
    branchRow.appendChild(tabButton(branchLabel(name) + ' · ' + count, {
      on: mode === 'board' && board === name,
      title: name + (b.labelEn ? ' · ' + b.labelEn : '') + ' — ' + count + ' узлов'
        + (count ? '' : ' (ветка пуста)'),
      cls: (count ? '' : 'empty') + (warn ? ' warn' : ''),
      onclick: () => switchBoard(name),
    }));
  });
}

function infoRow(text, cls) {
  const row = document.createElement('div');
  row.className = 'row' + (cls ? ' ' + cls : '');
  row.textContent = text;
  infoBox.appendChild(row);
  return row;
}

function buildInfo() {
  infoBox.innerHTML = '';
  const head = text => {
    const row = document.createElement('div');
    row.className = 'head';
    row.textContent = text;
    infoBox.appendChild(row);
  };
  const total = nodes.reduce((sum, n) => sum + (n.cost || 0), 0);
  head('дерево');
  infoRow('узлов ' + nodes.length + ' · ' + total.toLocaleString('ru-RU') + ' очк.');
  infoRow('проблем: ' + problems.length, problems.length ? 'bad' : 'ok');

  if (mode === 'board' && branches[board]) {
    const list = nodes.filter(n => n.branch === board);
    const branchXp = list.reduce((sum, n) => sum + (n.cost || 0), 0);
    head(sectionLabel(branches[board].section) + ' / ' + branchLabel(board));
    const b = branches[board];
    infoRow(board + (b.labelEn ? ' · ' + b.labelEn : ''), 'muted');
    infoRow('узлов ' + list.length + ' · ' + branchXp.toLocaleString('ru-RU') + ' очк.');
    const centre = list.find(n => !n.requires.length);
    infoRow('центр: ' + (centre ? centre.path : '—'));
    head('узлы доски');
    if (!list.length) {
      infoRow('ветка пуста — задел на будущее', 'muted');
    } else {
      list.slice().sort((a, b2) => (a.y - b2.y) || (a.x - b2.x) || a.path.localeCompare(b2.path))
        .forEach(n => {
          const row = infoRow(n.path, 'node' + (selected >= 0 && nodes[selected] === n ? ' sel' : '')
            + (problemPaths.has(n.path) ? ' bad' : ''));
          row.title = n.name + (n.name_ru ? ' / ' + n.name_ru : '') + ' · ' + n.cost + ' очк.';
          row.onclick = () => { select(index.get(n.path)); centreOn(n); draw(); };
        });
    }
  } else {
    head('обзор');
    infoRow('досок: ' + branchNames.length + ' · в разработке: '
      + branchNames.filter(name => !nodes.some(n => n.branch === name)).length);
    sectionNames.forEach(name => {
      const count = nodes.filter(n => (branches[n.branch] || {}).section === name).length;
      infoRow(sectionLabel(name) + ' · ' + count);
    });
    infoRow('клик по доске — открыть её', 'muted');
  }
}

function buildTools() {
  toolsBox.innerHTML =
    '<button id="bSave">сохранить</button>' +
    '<button id="bAdd">+ узел</button>' +
    '<button id="bLink">связать</button>' +
    '<button id="bUndo">отмена</button>' +
    '<button id="bOverview">обзор</button>' +
    '<button id="bGrid">сетка</button>' +
    '<button id="bFit">вписать</button>' +
    '<div class="search"><input id="q" type="search" placeholder="поиск: путь или имя (Ctrl+F)">' +
    '<div id="hits"></div></div>' +
    '<div class="line" id="lProblems"></div>' +
    '<div class="line" id="lHint"></div>';
  f('bSave').onclick = save;
  f('bAdd').onclick = addNode;
  f('bLink').onclick = () => startLink();
  f('bUndo').onclick = undo;
  f('bOverview').onclick = showOverview;
  f('bGrid').onclick = () => { showGrid = !showGrid; syncTools(); draw(); };
  f('bFit').onclick = () => { fit(); draw(); };
  f('lProblems').onclick = toggleProblems;

  const query = f('q');
  query.oninput = () => renderHits();
  query.onkeydown = event => {
    if (event.key === 'Enter') {
      const first = f('hits').querySelector('.hit');
      if (first) first.click();
      event.preventDefault();
    } else if (event.key === 'Escape') {
      query.value = '';
      renderHits();
      query.blur();
    }
  };
  document.addEventListener('mousedown', event => {
    if (!toolsBox.contains(event.target)) hideHits();
  });
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
  f('bOverview').className = mode === 'overview' ? 'on' : '';
}

function toggleProblems() {
  if (problemsBox.style.display === 'block') {
    problemsBox.style.display = 'none';
    return;
  }
  problemsBox.innerHTML = '';
  const close = document.createElement('div');
  close.textContent = '×';
  close.style.cssText = 'position:absolute;top:6px;right:10px;cursor:pointer;font-size:18px;color:#aaa;';
  close.onclick = () => { problemsBox.style.display = 'none'; };
  problemsBox.appendChild(close);
  const add = (item, cls) => {
    const row = document.createElement('div');
    row.textContent = item.text;
    if (cls) row.className = cls;
    if (item.path) row.onclick = () => gotoNode(item.path);
    problemsBox.appendChild(row);
  };
  if (!problems.length) add({text: 'проблем нет'});
  problems.forEach(item => add(item));
  notes.forEach(item => add(item, 'note'));
  problemsBox.style.display = 'block';
}

function renamePath(oldPath, newPath) {
  nodes.forEach(n => { n.requires = n.requires.map(r => r === oldPath ? newPath : r); });
  const view = views.get(oldPath);
  if (view) { views.delete(oldPath); views.set(newPath, view); }
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
  f('fBranch').onchange = () => {
    const value = f('fBranch').value;
    if (value === nodes[selected].branch) return;
    pushUndo();
    nodes[selected].branch = value;
    const keep = selected;
    touch();
    switchBoard(value);
    selected = keep;
    syncCard();
    buildInfo();
    centreOn(nodes[selected]);
    draw();
  };
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
    renamePath(old, value);
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
  buildInfo();
  draw();
}

function touch() {
  dirty = true;
  reindex();
  recomputeLayout();
  syncTools();
  buildTabs();
  buildInfo();
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

function freeCell(branch) {
  const [tx, ty] = toTree(width() / 2, height() / 2);
  const cx = Math.round(tx / GRID_X), cy = Math.round(ty / GRID_Y);
  const taken = (x, y) => nodes.some(n => n.branch === branch && n.x === x && n.y === y);
  for (let r = 0; r < 30; r++) {
    for (let dx = -r; dx <= r; dx++) {
      for (let dy = -r; dy <= r; dy++) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
        if (!taken(cx + dx, cy + dy)) return [cx + dx, cy + dy];
      }
    }
  }
  return [cx, cy];
}

function addNode() {
  let branch = mode === 'board' ? board
    : ((selected >= 0 && nodes[selected]) ? nodes[selected].branch
      : branchNames.find(name => (branches[name].section || null) === activeSection) || branchNames[0]);
  if (!branch) { flash('сначала выберите ветку'); return; }
  if (mode !== 'board' || board !== branch) switchBoard(branch);
  let path = 'new_node';
  let i = 1;
  while (index.has(path)) path = 'new_node_' + (++i);
  const cell = freeCell(branch);
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
  views = new Map();
  overviewView = null;
  mode = 'board';
  const first = branchNames.find(name => nodes.some(n => n.branch === name)) || branchNames[0];
  board = first;
  activeSection = branches[first] ? branches[first].section : activeSection;
  touch(); syncCard();
  fit(); draw();
  flash('загружено ' + nodes.length + ' узлов из файла правок');
}

function gotoNode(path) {
  const i = index.get(path);
  if (i === undefined) return;
  const n = nodes[i];
  hideHits();
  hideTip();
  if (mode !== 'board' || board !== n.branch) switchBoard(n.branch);
  select(i);
  centreOn(n);
  draw();
}

function searchHits(query) {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const scored = [];
  nodes.forEach((n, i) => {
    const where = branchLabel(n.branch) + ' ' + n.branch;
    const hay = (n.path + ' ' + (n.name || '') + ' ' + (n.name_ru || '') + ' '
      + (n.unlock || '') + ' ' + where).toLowerCase();
    if (!hay.includes(q)) return;
    let score = 0;
    if (n.path.toLowerCase() === q) score += 8;
    else if (n.path.toLowerCase().startsWith(q)) score += 4;
    if ((n.name || '').toLowerCase().startsWith(q)) score += 3;
    if (n.branch.toLowerCase() === q) score += 2;
    scored.push({i: i, score: score});
  });
  scored.sort((a, b) => (b.score - a.score) || (a.i - b.i));
  return scored.slice(0, 12).map(s => nodes[s.i]);
}

function renderHits() {
  const box = f('hits');
  if (!box) return;
  const query = f('q').value;
  box.innerHTML = '';
  if (!query.trim()) { box.style.display = 'none'; return; }
  const found = searchHits(query);
  if (!found.length) {
    const row = document.createElement('div');
    row.className = 'none';
    row.textContent = 'ничего не найдено';
    box.appendChild(row);
  } else {
    found.forEach(n => {
      const row = document.createElement('div');
      row.className = 'hit';
      row.textContent = n.path + ' — ' + (n.name || '');
      const where = document.createElement('span');
      where.className = 'where';
      where.textContent = '  ' + branchLabel(n.branch);
      row.appendChild(where);
      row.title = (n.name || '') + (n.name_ru ? ' / ' + n.name_ru : '');
      row.onclick = () => { gotoNode(n.path); f('q').value = ''; renderHits(); };
      box.appendChild(row);
    });
  }
  box.style.display = 'block';
}

function hideHits() {
  const box = f('hits');
  if (box) box.style.display = 'none';
}

function showTip(i, event) {
  const n = nodes[i];
  tip.style.display = 'block';
  tip.style.left = Math.min(event.clientX + 14, innerWidth - 490) + 'px';
  tip.style.top = (event.clientY + 14) + 'px';
  tip.textContent = n.name + (n.name_ru ? '  /  ' + n.name_ru : '') + '\n'
    + branchLabel(n.branch) + ' (' + n.branch + ')   '
    + (n.unlock ? 'техника: ' + n.unlock : 'пассивка: ' + n.effect + ' ' + n.amount) + '\n'
    + (n.desc || '') + (n.desc_ru ? '\n' + n.desc_ru : '') + '\n'
    + 'цена ' + n.cost + ' очк.'
    + (n.requires.length ? '   предки: ' + n.requires.join(', ') : '   предков нет');
}
function hideTip() { tip.style.display = 'none'; }

window.addEventListener('resize', () => {
  resize();
  recomputeLayout();
  if (mode === 'overview') fit();
  draw();
});
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
  if (linkFrom >= 0 && mode === 'board') {
    if (i >= 0) tryLink(linkFrom, i); else { linkFrom = -1; syncTools(); draw(); }
    return;
  }
  if (mode === 'overview') {
    if (i >= 0) {
      const n = nodes[i];
      switchBoard(n.branch);
      select(i);
      centreOn(n);
      draw();
      return;
    }
    const frame = frameAt(event.offsetX, event.offsetY);
    if (frame) { switchBoard(frame.branch); return; }
    pan = {x: event.clientX, y: event.clientY};
    return;
  }
  if (i < 0) { pan = {x: event.clientX, y: event.clientY}; return; }
  nodeDrag = {index: i, moved: false, snapshot: snapshot()};
});
canvas.addEventListener('mousemove', event => {
  if (pan) { panX += event.movementX; panY += event.movementY; draw(); return; }
  if (nodeDrag) {
    if (mode !== 'board') { nodeDrag = null; return; }
    const n = nodes[nodeDrag.index];
    const [tx, ty] = toTree(event.offsetX, event.offsetY);
    const cx = Math.round(tx / GRID_X), cy = Math.round(ty / GRID_Y);
    const ok = !occupied(cx, cy, nodeDrag.index);
    dragTarget = {x: cx, y: cy, ok: ok};
    if (ok && (cx !== n.x || cy !== n.y)) {
      n.x = cx; n.y = cy;
      nodeDrag.moved = true;
      dirty = true;
      recomputeLayout();
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
    reindex();
    buildTabs();
    buildInfo();
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
  if (event.ctrlKey && (event.key === 'f' || event.key === 'F')) {
    f('q').focus();
    f('q').select();
    event.preventDefault();
    return;
  }
  if (event.target !== document.body) return;
  if (event.key === 'Escape') {
    if (problemsBox.style.display === 'block') { problemsBox.style.display = 'none'; return; }
    if (linkFrom >= 0) { linkFrom = -1; syncTools(); draw(); } else select(-1);
    return;
  }
  if (event.key === '[' || event.key === ']') {
    cycleBoard(event.key === ']' ? 1 : -1);
    return;
  }
  if (event.key === 'o' || event.key === 'O') { showOverview(); return; }
  if (event.key === 'f' || event.key === 'F') { fit(); draw(); }
  if (event.key === 'n' || event.key === 'N') { alwaysNames = !alwaysNames; draw(); }
  if (event.key === 'g' || event.key === 'G') { showGrid = !showGrid; syncTools(); draw(); }
  if (event.key === 'c' || event.key === 'C') { colourByBranch = !colourByBranch; draw(); }
  if (event.key === '/') { f('q').focus(); event.preventDefault(); }
});
window.addEventListener('beforeunload', event => {
  if (!dirty) return;
  event.preventDefault();
  event.returnValue = '';
});

// Hooks for screenshots, automated checks and the agent's own runs.
window.previewHover = path => { hovered = index.has(path) ? index.get(path) : -1; hideTip(); draw(); };
window.previewBranch = name => { if (branches[name]) switchBoard(name); };
window.previewSection = name => switchSection(name);
window.previewOverview = () => showOverview();
window.previewFit = () => { fit(); draw(); };
window.previewSelect = path => { if (index.has(path)) { gotoNode(path); } };
window.previewGoto = path => gotoNode(path);
window.previewSearch = query => { f('q').value = query; renderHits(); return searchHits(query).map(n => n.path); };
window.previewMove = (path, x, y) => {
  if (!index.has(path)) return 'no such node';
  if (mode !== 'board' || board !== nodes[index.get(path)].branch) switchBoard(nodes[index.get(path)].branch);
  selected = index.get(path);
  moveTo(x, y);
  return nodes[selected].x + ',' + nodes[selected].y;
};
window.previewData = () => nodes;
window.previewCollect = () => collect();
window.previewSave = () => save();
window.previewProblems = () => problems.map(p => p.text);
window.previewState = () => ({
  mode, board, section: activeSection, zoom, panX, panY, dirty,
  selected: (nodes[selected] || {}).path || null, problems: problems.length,
  frames: layout.frames.map(fr => ({branch: fr.branch, x: fr.x, y: fr.y, w: fr.w, h: fr.h})),
  boxes: nodes.reduce((acc, n) => {
    const b = acc[n.branch] = acc[n.branch] || [1e9, 1e9, -1e9, -1e9];
    b[0] = Math.min(b[0], n.x); b[1] = Math.min(b[1], n.y);
    b[2] = Math.max(b[2], n.x); b[3] = Math.max(b[3], n.y);
    return acc;
  }, {}),
});

resize();
buildTools();
buildCard();
buildTabs();
buildInfo();
reindex();
recomputeLayout();
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


def write_html(
    out: Path, nodes, branches, order, effects, sections, section_order, empty_hint
) -> None:
    payload = {
        "nodes": nodes,
        "branches": {
            name: {
                "colour": data["colour"],
                "section": data["section"],
                "label": data.get("label", name),
                "labelEn": data.get("label_en", name),
            }
            for name, data in branches.items()
        },
        "branchList": order,
        "sections": {
            name: {
                "colour": data["colour"],
                "label": data.get("label", name),
                "labelEn": data.get("label_en", name),
            }
            for name, data in sections.items()
        },
        "sectionList": section_order,
        "effects": effects,
        "emptyHint": empty_hint,
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
    grouped = {}
    for name in order:
        grouped.setdefault(branches[name]["section"], []).append(name)
    for section, names in grouped.items():
        section_nodes = sum(1 for node in nodes if node["branch"] in names)
        print("  %s (%d nodes)" % (section, section_nodes))
        for name in names:
            count = sum(1 for node in nodes if node["branch"] == name)
            label = branches[name].get("label", name)
            box = boxes.get(name)
            print(
                "    %-10s %-16s %3d  %s"
                % (name, label, count, box if box else "empty board")
            )
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
    sections, section_order = parse_sections()
    effects = parse_effects()
    load_lang(nodes)
    empty_hint = load_tab_lang(branches, sections)
    problems, notes, boxes = lint(nodes, branches)
    report(nodes, branches, order, boxes, problems, notes)

    if args.check:
        return 1 if problems else 0

    write_html(
        out, nodes, branches, order, effects, sections, section_order, empty_hint
    )
    if args.json:
        payload = {
            "nodes": nodes,
            "branches": {
                name: {
                    "colour": data["colour"],
                    "section": data["section"],
                    "label": data.get("label", name),
                    "labelEn": data.get("label_en", name),
                }
                for name, data in branches.items()
            },
            "sections": {
                name: {
                    "colour": data["colour"],
                    "label": data.get("label", name),
                    "labelEn": data.get("label_en", name),
                }
                for name, data in sections.items()
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
