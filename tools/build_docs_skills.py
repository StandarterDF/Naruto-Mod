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
SECTION_JAVA = ROOT / "src/main/java/com/sekwah/narutomod/skills/SkillSection.java"
LANG_EN = ROOT / "src/main/resources/assets/narutomod/lang/en_us.json"
LANG_RU = ROOT / "src/main/resources/assets/narutomod/lang/ru_ru.json"
PAGE = ROOT / "docs/skills.html"

MARK_START = "<!-- SKILLS:START -->"
MARK_END = "<!-- SKILLS:END -->"

DECL = re.compile(
    r'(?s)(node|unlock)\(\s*"([a-z0-9_]+)"\s*,\s*SkillBranch\.([A-Z_]+)\s*,\s*'
    r"(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,(.*?)\)\s*;"
)
DECL_EFFECT = re.compile(r"SkillEffect\.([A-Z_]+)\s*,\s*(-?\d+(?:\.\d+)?)f")
QUOTED = re.compile(r'"([^"]+)"')
BRANCH = re.compile(
    r'([A-Z_]+)\(\s*(?:SkillSection\.([A-Z_]+)\s*,\s*)?"([^"]+)"\s*,\s*(0x[0-9A-Fa-f]+)\)'
)
SECTION = re.compile(r'([A-Z_]+)\(\s*"([^"]+)"\s*,\s*(0x[0-9A-Fa-f]+)\s*\)')

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
    "MAX_SUBSTITUTIONS",
    "ALL_ROUNDED",
    "CHAKRA_CHARGE_RATE",
    "SUBSTITUTION_REGEN",
    "SUBSTITUTION_INVISIBILITY",
    "DOUBLE_JUMP_POWER",
    "CLONE_LIFETIME",
    "WORKER_LIFETIME",
    "SOFT_LANDING",
    "SENIN_MANA",
    "SENIN_MANA_REGEN",
    "SAGE_ENTRY",
]

# What each tab is, in one line. Kept here so the generated block stays self-contained.
FLAVOUR = {
    "CORE": "Ядро: сырые запасы и статы. Входной узел даёт понемногу всего — чакры, стамины, "
    "здоровья, — а дальше каждая цепочка тянет ровно один стат: здоровье, броню, запас чакры, "
    "регенерацию, скорость, урон, цену и откат. Вся «арифметика» билда живёт здесь.",
    "ATHLETICS": "Атлетика: скорость, стамина, подмена, двойной прыжок, мягкая посадка, хождение "
    "по воде и сцепление чакрой. Сюда же относятся заряды подмены — без этой ветки их не поднять "
    "выше одного.",
    "TAIJUTSU": "Тайдзюцу: урон рукой, кулак чакры и Восемь врат. Жизнь за врата платится "
    "здоровьем, поэтому глубина ветки — это сколько врат кастер выдерживает.",
    "MEDICINE": "Медицина: регенерация чакрой, питание чакрой, чакра-щит и второе дыхание. Центр "
    "ветки даёт немного регенерации чакры, дальше — способы пережить то, что не переживается "
    "обычным здоровьем.",
    "FIRE": "Огонь: костёр, огненный шар и огненное дыхание. Ветка учит жечь — и предметы, и печку, "
    "и живую цель.",
    "WATER": "Вода: водяная пуля и водяное дыхание. Тушит огонь, мочит губку и отталкивает цель.",
    "EARTH": "Земля: рудокоп, широкий прокоп и чутьё руды. Вся ветка — про то, чтобы видеть и "
    "вынимать породу.",
    "WIND": "Ветер: ветряное дыхание и расенган с расенсюрикеном. Сдувает, режет и не оставляет "
    "огня.",
    "WOOD": "Дерево: дыхание роста и всё, что прорастает. Костная мука в конусе вместо урона.",
    "CLONES": "Клоны: теневой, чиби, мультиклон, медитация, возврат клонов, походный сундук и "
    "рабочие смены. Между техниками всегда стоит тренировочная пассивка, поэтому путь читается как "
    "«техника → практика → техника».",
    "SPACE": "Пространство: метка-бревно, чакра-нить, адаптация тела и Камуи. Центр ветки — "
    "якорь, без него не открывается ни один пространственный трюк.",
    "MIND": "Разум: чувство чакры и ночное зрение, гендзюцу, хенге, дымовая завеса и подавление "
    "звука.",
    "SAGE": "Сендзюцу: запас природной энергии, её сбор и вход в Режим Мудреца. Сначала копится "
    "энергия, потом открывается сам режим.",
    "SHARINGAN": "Шаринган: каркас ветки. Узлов пока нет — вкладка стоит пустой и ждёт своего "
    "набора техник.",
    "BYAKUGAN": "Бьякуган: каркас ветки. Узлов пока нет — вкладка стоит пустой и ждёт своего "
    "набора техник.",
    "RINNEGAN": "Риннеган: глаз мудреца, его запас чакры и сенин-маны, притяжение с отталкиванием "
    "и Чибаку Тенсэй. Техники висят на пассивках-подводках, а не друг на друге.",
}

# One line per section (the first row of tabs); a section is only a grouping of branches.
SECTION_FLAVOUR = {
    "PHYSICAL": "Физический путь: тело, движение, рукопашный бой и медицинское ниндзюцу. Всё, что "
    "растёт из самого кастера.",
    "NINJUTSU": "Ниндзюцу и стихии: по отдельной доске на каждую природу чакры, чтобы ветки не "
    "толкались на одной доске.",
    "SPECIAL": "Особые техники: клоны, пространство, разум и сендзюцу. То, что не сводится к одной "
    "стихии.",
    "DOJUTSU": "Додзюцу: глазные кланы. Риннеган открыт и покупается как стойка; Шаринган и "
    "Бьякуган пока размечены как каркас на будущее.",
}

# Bases the notes in the totals table refer to; the config defaults they mirror are checked by eye.
BASE_CHAKRA = 25
BASE_STAMINA = 100
BASE_HEALTH = 20
BASE_SPEED = 0.1
BASE_SUBSTITUTIONS = 1


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
        name, section, key, colour = match.groups()
        branches.append(
            {
                "name": name,
                "section": section,
                "lang": key,
                "colour": "#%06x" % (int(colour, 16) & 0xFFFFFF),
            }
        )
    return branches


def parse_sections() -> list[dict]:
    sections = []
    for match in SECTION.finditer(read(SECTION_JAVA)):
        name, key, colour = match.groups()
        sections.append(
            {
                "name": name,
                "lang": key,
                "colour": "#%06x" % (int(colour, 16) & 0xFFFFFF),
            }
        )
    return sections


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
    if effect == "MAX_SUBSTITUTIONS":
        return "+%s к максимуму зарядов подмены" % whole(amount)
    if effect == "ALL_ROUNDED":
        return "+%s чакры, стамины и здоровья" % whole(amount)
    if effect == "CHAKRA_CHARGE_RATE":
        return "+%s %% к накоплению чакры за тик" % clean(amount * 100)
    if effect == "SUBSTITUTION_REGEN":
        return "+%s %% к возврату зарядов подмены" % clean(amount * 100)
    if effect == "SUBSTITUTION_INVISIBILITY":
        return "+%s тиков невидимости после подмены" % whole(amount)
    if effect == "DOUBLE_JUMP_POWER":
        return "+%s %% к высоте двойного прыжка" % clean(amount * 100)
    if effect == "CLONE_LIFETIME":
        return "+%s %% к времени жизни боевых клонов" % clean(amount * 100)
    if effect == "WORKER_LIFETIME":
        return "+%s %% к времени жизни рабочих" % clean(amount * 100)
    if effect == "SOFT_LANDING":
        return "−%s %% стамины при мягкой посадке" % clean(amount * 100)
    if effect == "SENIN_MANA":
        return "+%s к максимуму сенин-маны" % whole(amount)
    if effect == "SENIN_MANA_REGEN":
        return "+%s %% к сбору природной энергии" % clean(amount * 100)
    if effect == "SAGE_ENTRY":
        return "−%s %% к порогу входа в Режим Мудреца" % clean(amount * 100)
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
    if effect == "MAX_SUBSTITUTIONS":
        return "+%s заряда подмены" % whole(amount)
    if effect == "ALL_ROUNDED":
        return "+%s ко всему" % whole(amount)
    if effect == "CHAKRA_CHARGE_RATE":
        return "+%s %% к накоплению" % clean(amount * 100)
    if effect == "SUBSTITUTION_REGEN":
        return "+%s %% возврата подмены" % clean(amount * 100)
    if effect == "SUBSTITUTION_INVISIBILITY":
        return "+%s тиков невидимости" % whole(amount)
    if effect == "DOUBLE_JUMP_POWER":
        return "+%s %% высоты прыжка" % clean(amount * 100)
    if effect == "CLONE_LIFETIME":
        return "+%s %% жизни клонов" % clean(amount * 100)
    if effect == "WORKER_LIFETIME":
        return "+%s %% жизни рабочих" % clean(amount * 100)
    if effect == "SOFT_LANDING":
        return "−%s %% стамины посадки" % clean(amount * 100)
    if effect == "SENIN_MANA":
        return "+%s сенин-маны" % whole(amount)
    if effect == "SENIN_MANA_REGEN":
        return "+%s %% сбора сенина" % clean(amount * 100)
    if effect == "SAGE_ENTRY":
        return "−%s %% порога входа" % clean(amount * 100)
    return esc(amount)


def bare_text(effect: str, amount: float) -> str:
    """Just the number, for the "where does it come from" column."""
    if effect in ("CHAKRA_REGEN", "STAMINA_REGEN"):
        return "+%s" % clean(amount)
    if effect in ("COST_REDUCTION", "COOLDOWN_REDUCTION", "SAGE_ENTRY"):
        return "−%s %%" % clean(amount * 100)
    if effect == "MOVEMENT_SPEED":
        return "+%s %%" % clean(amount / BASE_SPEED * 100)
    if effect in (
        "CHAKRA_CHARGE_RATE",
        "SUBSTITUTION_REGEN",
        "DOUBLE_JUMP_POWER",
        "CLONE_LIFETIME",
        "WORKER_LIFETIME",
        "SOFT_LANDING",
        "SENIN_MANA_REGEN",
    ):
        return "+%s %%" % clean(amount * 100)
    return "+%s" % whole(amount)


def totals_value(effect: str, total: float) -> str:
    if effect in ("CHAKRA_REGEN", "STAMINA_REGEN"):
        return "+%s/с" % clean(total)
    if effect in ("COST_REDUCTION", "COOLDOWN_REDUCTION", "SAGE_ENTRY"):
        return "−%s %%" % clean(total * 100)
    if effect == "MOVEMENT_SPEED":
        return "+%s %%" % clean(total / BASE_SPEED * 100)
    if effect in (
        "CHAKRA_CHARGE_RATE",
        "SUBSTITUTION_REGEN",
        "DOUBLE_JUMP_POWER",
        "CLONE_LIFETIME",
        "WORKER_LIFETIME",
        "SOFT_LANDING",
        "SENIN_MANA_REGEN",
    ):
        return "+%s %%" % clean(total * 100)
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
    if effect == "MAX_SUBSTITUTIONS":
        return "базовый %s заряд + %s с деревом = %s" % (
            whole(BASE_SUBSTITUTIONS),
            whole(total),
            whole(BASE_SUBSTITUTIONS + total),
        )
    if effect == "ALL_ROUNDED":
        return "входной узел; прибавляется к чакре, стамине и здоровью сразу"
    if effect == "CHAKRA_CHARGE_RATE":
        return "только техника накопления чакры"
    if effect == "SUBSTITUTION_REGEN":
        return "скорость возврата зарядов подмены"
    if effect == "SUBSTITUTION_INVISIBILITY":
        return "сверх базовой невидимости после подмены"
    if effect == "DOUBLE_JUMP_POWER":
        return "только двойной прыжок"
    if effect == "CLONE_LIFETIME":
        return "боевые клоны (теневой, чиби, мульти)"
    if effect == "WORKER_LIFETIME":
        return "рабочие клоны"
    if effect == "SOFT_LANDING":
        return "скидка к цене мягкой посадки"
    if effect == "SENIN_MANA":
        return "максимум природной энергии, задаётся конфигом"
    if effect == "SENIN_MANA_REGEN":
        return "скорость сбора природной энергии"
    if effect == "SAGE_ENTRY":
        return "порог входа в Режим Мудреца"
    return ""


def build_block(nodes, branches, sections, lang_ru, lang_en, stats) -> str:
    by_path = {node["path"]: node for node in nodes}
    branch_names = {b["name"]: lang_ru.get(b["lang"], b["name"]) for b in branches}
    section_names = {s["name"]: lang_ru.get(s["lang"], s["name"]) for s in sections}

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
            plural(total_cost, "очко навыка", "очка навыка", "очков навыка"),
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
    line('          <td class="small">%s</td>' % esc("центры всех веток"))
    line("        </tr>")
    line("      </tbody>")
    line("    </table>")
    line("  </div>")
    line(
        '  <p class="small muted">Очки навыка покупаются за опыт: <strong>10 опыта = 1 очко</strong> '
        "(кнопки <em>+1</em>, <em>+10</em> и <em>Все</em> внизу доски). Цены узлов — это прежние "
        "цены в опыте, поделённые на тот же курс, поэтому выкуп всего дерева стоит столько же "
        "опыта, сколько и раньше. Скидки цены и отката складываются с освоением техник и вместе "
        "упираются в −90 %: дешевле или быстрее порога техника не станет. Стоимость узла "
        "от порядка покупки не зависит — сумма одинаковая при любом маршруте.</p>"
    )
    line("</section>")
    line()

    # --- Branches, grouped by section --------------------------------------------------------
    current_section = None
    for b in branches:
        name = b["name"]
        entry = per_branch[name]
        if b["section"] != current_section:
            current_section = b["section"]
            meta = next((s for s in sections if s["name"] == current_section), None)
            if meta is not None:
                line('<section id="sec-%s">' % current_section.lower())
                line(
                    '  <h2 id="s-sec-%s"><span class="bcol" style="background:%s"></span>%s</h2>'
                    % (
                        current_section.lower(),
                        meta["colour"],
                        esc(section_names[current_section]),
                    )
                )
                line("  <p>%s</p>" % esc(SECTION_FLAVOUR.get(current_section, "")))
                line(
                    '  <p class="branch-meta">%s</p>'
                    % esc(
                        " · ".join(
                            branch_names[x["name"]]
                            for x in branches
                            if x["section"] == current_section
                        )
                    )
                )
                line("</section>")
                line()

        title = "Ветка «%s»" % branch_names[name]
        facts = [
            "%s %s"
            % (
                whole(len(entry["nodes"])),
                plural(len(entry["nodes"]), "узел", "узла", "узлов"),
            ),
            "%s %s"
            % (
                whole(entry["cost"]),
                plural(entry["cost"], "очко навыка", "очка навыка", "очков навыка"),
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
        if not entry["nodes"]:
            line(
                '  <div class="callout callout--note"><div class="callout__title">Ветка в '
                "разработке</div><p>Узлов здесь пока нет: вкладка существует как каркас, покупок "
                "в ней не делается. Как только у ветки появится свой набор техник, таблица "
                "заполнится сама.</p></div>"
            )
            line("</section>")
            line()
            continue
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
    "MAX_SUBSTITUTIONS": "Заряды подмены",
    "ALL_ROUNDED": "Понемногу всего",
    "CHAKRA_CHARGE_RATE": "Накопление чакры",
    "SUBSTITUTION_REGEN": "Возврат подмены",
    "SUBSTITUTION_INVISIBILITY": "Невидимость подмены",
    "DOUBLE_JUMP_POWER": "Высота двойного прыжка",
    "CLONE_LIFETIME": "Время жизни клонов",
    "WORKER_LIFETIME": "Время жизни рабочих",
    "SOFT_LANDING": "Мягкая посадка",
    "SENIN_MANA": "Сенин-мана",
    "SENIN_MANA_REGEN": "Сбор природной энергии",
    "SAGE_ENTRY": "Порог Режима Мудреца",
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
    sections = parse_sections()
    lang_ru, lang_en = load_lang(nodes, stats)
    block = build_block(nodes, branches, sections, lang_ru, lang_en, stats)

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
            "  %-10s %3d nodes  %6d очк.   %s"
            % (
                b["name"],
                len(subset),
                sum(n["cost"] for n in subset),
                lang_ru.get(b["lang"], ""),
            )
        )
    print("total: %d очк." % sum(n["cost"] for n in nodes))
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
