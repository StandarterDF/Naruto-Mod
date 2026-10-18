#!/usr/bin/env python3
"""Regenerates the skill-tree tables in ``docs/skills.html`` from the mod itself.

The wiki has to agree with the game, and the game's whole skill table is one static block in
``skills/Skill.java`` plus two language files. This script reads them, checks that every node has a
name, and rewrites everything between the ``<!-- SKILLS:START -->`` and ``<!-- SKILLS:END -->``
markers on the page: the totals of the whole tree and one table per branch. Nothing outside the
markers is touched, so the hand-written part of the page stays where it is.

Usage:
    python tools/build_docs_skills.py            # regenerate docs/skills.html
    python tools/build_docs_skills.py --check    # fail (exit 1) if the page is out of date
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SKILL_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/Skill.java"
BRANCH_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillBranch.java"
LANG_EN = ROOT / "src/main/resources/assets/narutomod/lang/en_us.json"
LANG_RU = ROOT / "src/main/resources/assets/narutomod/lang/ru_ru.json"
PAGE = ROOT / "docs/skills.html"

MARK_START = "<!-- SKILLS:START -->"
MARK_END = "<!-- SKILLS:END -->"

DECL = re.compile(
    r'(?s)(node|unlock)\(\s*"([a-z_]+)"\s*,\s*SkillBranch\.([A-Z_]+)\s*,\s*'
    r"(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,(.*?)\)\s*;"
)
DECL_EFFECT = re.compile(r"SkillEffect\.([A-Z_]+)\s*,\s*(-?\d+(?:\.\d+)?)f")
QUOTED = re.compile(r'"([^"]+)"')
BRANCH = re.compile(r'([A-Z_]+)\("([^"]+)",\s*(0x[0-9A-Fa-f]+)\)')

# The order effects are listed in, both per branch and in the totals table.
EFFECT_ORDER = [
    "MAX_CHAKRA",
    "CHAKRA_REGEN",
    "MAX_STAMINA",
    "STAMINA_REGEN",
    "MAX_HEALTH",
    "ARMOR",
    "ATTACK_DAMAGE",
    "MOVEMENT_SPEED",
    "COST_REDUCTION",
    "COOLDOWN_REDUCTION",
]

# What each branch is, in one line. Kept here so the generated block stays self-contained.
FLAVOUR = {
    "ROOT": "Центр доски: отсюда растут все ветки, и покупать его принято первым — при базе 25 чакры "
    "корень заметно расширяет запас.",
    "CHAKRA": "Север: больше чакры и быстрее её восстановление. Это топливо, на которое опирается "
    "всё остальное дерево.",
    "BODY": "Юг: здоровье и броня, а за ними телесные техники — кулак чакры, регенерация, второе "
    "дыхание, восемь врат и режим Мудреца.",
    "CONTROL": "Восток: ни одной техники — только экономика. Скидки к цене и откату работают на все "
    "техники сразу.",
    "AGILITY": "Запад: скорость и урон, стамина и свобода движения — двойной прыжок, рывок, "
    "сцепление чакрой и хождение по воде.",
    "CLONES": "Северо-восток: все виды клонов, медитация чакры, возврат клонов и походный сундук.",
    "ELEMENTAL": "Юго-восток: стихии и дыхания, копание (рудокоп, прокоп, чутьё руды) и "
    "расэн-семейство.",
    "LABOUR": "Северо-восток, сразу за походным сундуком: рабочие смены — земледелец, шахтёр, "
    "лесоруб и охотник.",
    "SPACE": "Юго-запад: бревно-метка, чакра-нить, адаптация тела и Камуи с фазовым телом.",
    "MIND": "Северо-запад: чувство чакры, гендзюцу, хенге, дымовая завеса и подавление звука.",
}

# Bases the notes in the totals table refer to; the config defaults they mirror are checked by eye.
BASE_CHAKRA = 25
BASE_STAMINA = 100
BASE_HEALTH = 20
BASE_SPEED = 0.1


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def esc(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def clean(value: float) -> str:
    """A float as a Russian decimal: 0.03 -> ``0,03``, 2.0 -> ``2``."""
    text = ("%.3f" % value).rstrip("0").rstrip(".")
    return text.replace(".", ",") if text else "0"


def whole(value: float) -> str:
    """A whole number with thin-ish thousands separators: 1610 -> ``1 610``."""
    return format(int(round(value)), ",d").replace(",", " ")


def plural(count, one: str, few: str, many: str) -> str:
    """Russian plural form for a count: 1 узел, 2 узла, 5 узлов."""
    n = abs(int(count))
    if n % 10 == 1 and n % 100 != 11:
        return one
    if 2 <= n % 10 <= 4 and not 12 <= n % 100 <= 14:
        return few
    return many


def parse_skills() -> list[dict]:
    src = read(SKILL_JAVA)
    nodes = []
    for match in DECL.finditer(src):
        kind, path, branch, x, y, cost, tail = match.groups()
        if kind == "unlock":
            quoted = QUOTED.findall(tail)
            unlock, requires = quoted[0], quoted[1:]
            effect, amount = "UNLOCK", 0.0
        else:
            unlock, requires = None, QUOTED.findall(tail)
            found = DECL_EFFECT.search(tail)
            if not found:
                raise SystemExit("cannot read the effect of node %s" % path)
            effect, amount = found.group(1), float(found.group(2))
        if effect != "UNLOCK" and effect not in EFFECT_ORDER:
            raise SystemExit("unknown effect %s on node %s" % (effect, path))
        nodes.append(
            {
                "kind": kind,
                "path": path,
                "branch": branch,
                "x": int(x),
                "y": int(y),
                "cost": int(cost),
                "effect": effect,
                "amount": amount,
                "unlock": unlock,
                "requires": requires,
            }
        )
    if not nodes:
        raise SystemExit("no nodes parsed out of Skill.java")
    return nodes


def parse_branches() -> list[dict]:
    branches = []
    for match in BRANCH.finditer(read(BRANCH_JAVA)):
        name, key, colour = match.groups()
        branches.append(
            {
                "name": name,
                "lang": key,
                "colour": "#%06x" % (int(colour, 16) & 0xFFFFFF),
            }
        )
    return branches


def load_lang(nodes: list[dict], stats: list[str]) -> tuple[dict, dict]:
    ru = json.loads(read(LANG_RU)) if LANG_RU.exists() else {}
    en = json.loads(read(LANG_EN)) if LANG_EN.exists() else {}
    for node in nodes:
        base = "naruto.skill." + node["path"]
        node["name"] = ru.get(base + ".name") or en.get(base + ".name") or node["path"]
        node["desc"] = ru.get(base + ".desc") or en.get(base + ".desc") or ""
        if not ru.get(base + ".name"):
            stats.append("no ru name: %s" % node["path"])
    return ru, en


def amount_text(effect: str, amount: float) -> str:
    """What one node shows in its branch table."""
    if effect == "MAX_CHAKRA":
        return "+%s к максимуму чакры" % whole(amount)
    if effect == "CHAKRA_REGEN":
        return "+%s чакры/с" % clean(amount)
    if effect == "MAX_STAMINA":
        return "+%s к максимуму стамины" % whole(amount)
    if effect == "STAMINA_REGEN":
        return "+%s стамины/с" % clean(amount)
    if effect == "MAX_HEALTH":
        return "+%s HP" % whole(amount)
    if effect == "ARMOR":
        return "+%s к броне" % whole(amount)
    if effect == "ATTACK_DAMAGE":
        return "+%s к урону атаки" % whole(amount)
    if effect == "MOVEMENT_SPEED":
        return "+%s %% к скорости" % clean(amount / BASE_SPEED * 100)
    if effect == "COST_REDUCTION":
        return "−%s %% к цене техник" % clean(amount * 100)
    if effect == "COOLDOWN_REDUCTION":
        return "−%s %% к откату техник" % clean(amount * 100)
    return esc(amount)


def short_text(effect: str, amount: float) -> str:
    """The same, trimmed down for the branch summary line."""
    if effect == "MAX_CHAKRA":
        return "+%s чакры" % whole(amount)
    if effect == "CHAKRA_REGEN":
        return "+%s чакры/с" % clean(amount)
    if effect == "MAX_STAMINA":
        return "+%s стамины" % whole(amount)
    if effect == "STAMINA_REGEN":
        return "+%s стамины/с" % clean(amount)
    if effect == "MAX_HEALTH":
        return "+%s HP" % whole(amount)
    if effect == "ARMOR":
        return "+%s брони" % whole(amount)
    if effect == "ATTACK_DAMAGE":
        return "+%s урона" % whole(amount)
    if effect == "MOVEMENT_SPEED":
        return "+%s %% скорости" % clean(amount / BASE_SPEED * 100)
    if effect == "COST_REDUCTION":
        return "−%s %% цены" % clean(amount * 100)
    if effect == "COOLDOWN_REDUCTION":
        return "−%s %% отката" % clean(amount * 100)
    return esc(amount)


def bare_text(effect: str, amount: float) -> str:
    """Just the number, for the "where does it come from" column."""
    if effect in ("CHAKRA_REGEN", "STAMINA_REGEN"):
        return "+%s" % clean(amount)
    if effect in ("COST_REDUCTION", "COOLDOWN_REDUCTION"):
        return "−%s %%" % clean(amount * 100)
    if effect == "MOVEMENT_SPEED":
        return "+%s %%" % clean(amount / BASE_SPEED * 100)
    return "+%s" % whole(amount)


def totals_value(effect: str, total: float) -> str:
    if effect in ("CHAKRA_REGEN", "STAMINA_REGEN"):
        return "+%s/с" % clean(total)
    if effect in ("COST_REDUCTION", "COOLDOWN_REDUCTION"):
        return "−%s %%" % clean(total * 100)
    if effect == "MOVEMENT_SPEED":
        return "+%s %%" % clean(total / BASE_SPEED * 100)
    if effect == "MAX_HEALTH":
        return "+%s HP" % whole(total)
    return "+%s" % whole(total)


def totals_note(effect: str, total: float) -> str:
    if effect == "MAX_CHAKRA":
        return "%s из конфига + %s с деревом = %s" % (
            whole(BASE_CHAKRA),
            whole(total),
            whole(BASE_CHAKRA + total),
        )
    if effect == "CHAKRA_REGEN":
        return "сверх базовых 0,05 за тик (1 в секунду)"
    if effect == "MAX_STAMINA":
        return "%s из конфига + %s с деревом = %s" % (
            whole(BASE_STAMINA),
            whole(total),
            whole(BASE_STAMINA + total),
        )
    if effect == "STAMINA_REGEN":
        return "сверх базовых 0,4 за тик (8 в секунду)"
    if effect == "MAX_HEALTH":
        return "базовые %s HP (10 сердец) + %s" % (whole(BASE_HEALTH), whole(total))
    if effect == "ARMOR":
        return "полоска брони, складывается с бронёй предметов"
    if effect == "ATTACK_DAMAGE":
        return "к удару рукой; урон оружия считается отдельно"
    if effect == "MOVEMENT_SPEED":
        return "к базовым %s скорости игрока" % clean(BASE_SPEED)
    if effect == "COST_REDUCTION":
        return "складывается с освоением, общий предел −90 %"
    if effect == "COOLDOWN_REDUCTION":
        return "складывается с освоением, общий предел −90 %"
    return ""


def build_block(nodes, branches, lang_ru, lang_en, stats) -> str:
    by_path = {node["path"]: node for node in nodes}
    branch_names = {b["name"]: lang_ru.get(b["lang"], b["name"]) for b in branches}

    # Per-branch aggregates.
    per_branch: dict[str, dict] = {
        b["name"]: {"nodes": [], "cost": 0, "effects": {}, "unlocks": 0}
        for b in branches
    }
    for node in nodes:
        entry = per_branch[node["branch"]]
        entry["nodes"].append(node)
        entry["cost"] += node["cost"]
        if node["kind"] == "unlock":
            entry["unlocks"] += 1
        else:
            entry["effects"][node["effect"]] = (
                entry["effects"].get(node["effect"], 0.0) + node["amount"]
            )

    total_cost = sum(node["cost"] for node in nodes)
    total_unlocks = sum(1 for node in nodes if node["kind"] == "unlock")
    total_passives = len(nodes) - total_unlocks
    worker_unlocks = sum(
        1
        for node in nodes
        if node["kind"] == "unlock" and not node["unlock"].startswith("ability:")
    )

    totals = {}
    for effect in EFFECT_ORDER:
        totals[effect] = sum(
            node["amount"] for node in nodes if node["effect"] == effect
        )

    pad = "    "
    out: list[str] = []

    def line(text: str = "") -> None:
        out.append(pad + text if text else "")

    # --- Totals -----------------------------------------------------------------------------
    line('<section id="totals">')
    line('  <h2 id="s-totals">Что даёт всё дерево</h2>')
    line(
        "  <p>Если выкупить дерево целиком: <strong>%s</strong> %s (из них %s %s и %s %s) "
        "за <strong>%s</strong> %s. Это верхняя граница билда — на практике ветки выбирают "
        "под стиль игры, а покупать их можно в любом порядке.</p>"
        % (
            whole(len(nodes)),
            plural(len(nodes), "узел", "узла", "узлов"),
            whole(total_unlocks),
            plural(total_unlocks, "открытие", "открытия", "открытий"),
            whole(total_passives),
            plural(total_passives, "пассивка", "пассивки", "пассивок"),
            whole(total_cost),
            plural(total_cost, "очко опыта", "очка опыта", "очков опыта"),
        )
    )
    line('  <div class="tbl-wrap">')
    line('    <table class="tbl">')
    line("      <thead>")
    line(
        '        <tr><th>Эффект</th><th class="num">Всего</th><th>Комментарий</th><th>Откуда</th></tr>'
    )
    line("      </thead>")
    line("      <tbody>")
    for effect in EFFECT_ORDER:
        contribution = " · ".join(
            "%s %s"
            % (
                branch_names[b["name"]],
                bare_text(effect, per_branch[b["name"]]["effects"][effect]),
            )
            for b in branches
            if per_branch[b["name"]]["effects"].get(effect, 0.0) > 0
        )
        line("        <tr>")
        line("          <td>%s</td>" % esc(EFFECT_TITLES[effect]))
        line('          <td class="num">%s</td>' % totals_value(effect, totals[effect]))
        line(
            '          <td class="small muted">%s</td>'
            % esc(totals_note(effect, totals[effect]))
        )
        line('          <td class="small">%s</td>' % esc(contribution))
        line("        </tr>")
    line("        <tr>")
    line("          <td>%s</td>" % esc(EFFECT_TITLES["UNLOCK"]))
    line('          <td class="num">%s</td>' % whole(total_unlocks))
    line(
        '          <td class="small muted">в том числе %s %s рабочих; закрытые техники видны в меню '
        "<kbd>J</kbd></td>"
        % (whole(worker_unlocks), plural(worker_unlocks, "смена", "смены", "смен"))
    )
    line('          <td class="small">%s</td>' % esc("корень и все 9 веток"))
    line("        </tr>")
    line("      </tbody>")
    line("    </table>")
    line("  </div>")
    line(
        '  <p class="small muted">Скидки цены и отката складываются с освоением техник и вместе '
        "упираются в −90 %: дешевле или быстрее порога техника не станет. Стоимость узла в опыте от "
        "порядка покупки не зависит — сумма одинаковая при любом маршруте.</p>"
    )
    line("</section>")
    line()

    # --- Branches ---------------------------------------------------------------------------
    for b in branches:
        name = b["name"]
        entry = per_branch[name]
        title = (
            "Корень «%s»" % branch_names[name]
            if name == "ROOT"
            else "Ветка «%s»" % branch_names[name]
        )
        facts = [
            "%s %s"
            % (
                whole(len(entry["nodes"])),
                plural(len(entry["nodes"]), "узел", "узла", "узлов"),
            ),
            "%s %s"
            % (
                whole(entry["cost"]),
                plural(entry["cost"], "очко опыта", "очка опыта", "очков опыта"),
            ),
        ]
        short = [
            short_text(effect, entry["effects"][effect])
            for effect in EFFECT_ORDER
            if entry["effects"].get(effect, 0.0) > 0
        ]
        if short:
            facts.append(", ".join(short))
        line('<section id="b-%s">' % name.lower())
        line(
            '  <h2 id="s-b-%s"><span class="bcol" style="background:%s"></span>%s</h2>'
            % (name.lower(), b["colour"], title)
        )
        line('  <p class="branch-meta">%s</p>' % esc(" · ".join(facts)))
        line("  <p>%s</p>" % esc(FLAVOUR.get(name, "")))
        line('  <div class="tbl-wrap">')
        line('    <table class="tbl tbl--compact">')
        line("      <thead>")
        line(
            '        <tr><th>Узел</th><th class="num">Цена</th><th>Что даёт</th><th>Нужны</th></tr>'
        )
        line("      </thead>")
        line("      <tbody>")
        for node in entry["nodes"]:
            if node["kind"] == "unlock":
                tag = (
                    "рабочий"
                    if not node["unlock"].startswith("ability:")
                    else "техника"
                )
                desc = node["desc"] or lang_en.get(
                    "naruto.skill.%s.desc" % node["path"], ""
                )
                if not desc:
                    stats.append("no desc: %s" % node["path"])
                    desc = "Открывает: %s" % node["unlock"]
                effect_cell = '<span class="tag tag--unlock">%s</span> %s' % (
                    tag,
                    esc(desc),
                )
            else:
                effect_cell = esc(amount_text(node["effect"], node["amount"]))
            requires = ", ".join(
                by_path[req]["name"] for req in node["requires"] if req in by_path
            )
            for req in node["requires"]:
                if req not in by_path:
                    stats.append("unknown prerequisite %s -> %s" % (node["path"], req))
            line("        <tr>")
            line("          <td>%s</td>" % esc(node["name"]))
            line('          <td class="num">%s</td>' % whole(node["cost"]))
            line("          <td>%s</td>" % effect_cell)
            line(
                '          <td class="muted small">%s</td>'
                % (esc(requires) if requires else "—")
            )
            line("        </tr>")
        line("      </tbody>")
        line("    </table>")
        line("  </div>")
        line("</section>")
        line()

    while out and not out[-1]:
        out.pop()
    return "\n".join(out)


EFFECT_TITLES = {
    "MAX_CHAKRA": "Максимум чакры",
    "CHAKRA_REGEN": "Регенерация чакры",
    "MAX_STAMINA": "Максимум стамины",
    "STAMINA_REGEN": "Регенерация стамины",
    "MAX_HEALTH": "Здоровье",
    "ARMOR": "Броня",
    "ATTACK_DAMAGE": "Урон атаки",
    "MOVEMENT_SPEED": "Скорость передвижения",
    "COST_REDUCTION": "Цена техник",
    "COOLDOWN_REDUCTION": "Откат техник",
    "UNLOCK": "Техники и механики",
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check", action="store_true", help="fail if the page is already up to date"
    )
    args = parser.parse_args()

    stats: list[str] = []
    nodes = parse_skills()
    branches = parse_branches()
    lang_ru, lang_en = load_lang(nodes, stats)
    block = build_block(nodes, branches, lang_ru, lang_en, stats)

    page = read(PAGE)
    if MARK_START not in page or MARK_END not in page:
        raise SystemExit(
            "docs/skills.html has no %s / %s markers" % (MARK_START, MARK_END)
        )

    start = page.index(MARK_START) + len(MARK_START)
    end = page.index(MARK_END)
    updated = page[:start] + "\n\n" + block + "\n\n" + page[end:]

    worker = sum(
        1
        for n in nodes
        if n["kind"] == "unlock" and not n["unlock"].startswith("ability:")
    )
    print(
        "nodes: %d   unlocks: %d (%d worker)   passives: %d"
        % (
            len(nodes),
            sum(1 for n in nodes if n["kind"] == "unlock"),
            worker,
            sum(1 for n in nodes if n["kind"] != "unlock"),
        )
    )
    for b in branches:
        subset = [n for n in nodes if n["branch"] == b["name"]]
        print(
            "  %-10s %3d nodes  %6d xp   %s"
            % (
                b["name"],
                len(subset),
                sum(n["cost"] for n in subset),
                lang_ru.get(b["lang"], ""),
            )
        )
    print("total: %d xp" % sum(n["cost"] for n in nodes))
    for problem in stats:
        print("  warning: " + problem)

    if updated == page:
        print("docs/skills.html is already up to date")
        return 0
    if args.check:
        print("docs/skills.html is OUT OF DATE - run tools/build_docs_skills.py")
        return 1
    PAGE.write_text(updated, encoding="utf-8", newline="\n")
    print("wrote %s" % PAGE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
