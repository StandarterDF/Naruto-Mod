#!/usr/bin/env python3
"""Builds the crafting-recipe data used by the wiki (docs/recipes-data.js).

The wiki shows a Minecraft-style crafting grid when an item is clicked. Rather than hand-drawing
sixty-odd grids, this script reads the real recipe JSON from ``data/narutomod/recipes``, resolves
the item names from ``ru_ru.json`` and copies the icons the grids need into ``docs/assets``.

Usage:
    python tools/build_docs_recipes.py            # regenerate docs/recipes-data.js and the icons
    python tools/build_docs_recipes.py --check    # fail (exit 1) if anything is out of date
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPE_DIR = ROOT / "src/main/resources/data/narutomod/recipes"
MODELS = ROOT / "src/main/resources/assets/narutomod/models/item"
MOD_TEXTURES = ROOT / "src/main/resources/assets/narutomod/textures"
LANG_RU = ROOT / "src/main/resources/assets/narutomod/lang/ru_ru.json"
LANG_EN = ROOT / "src/main/resources/assets/narutomod/lang/en_us.json"
OUT_JS = ROOT / "docs/recipes-data.js"
DOC_TEXTURES = ROOT / "docs/assets/textures"
DOC_VANILLA = ROOT / "docs/assets/vanilla"
CLIENT_JAR = (
    Path.home()
    / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar"
)

# The language file leaves a few entries out (the wiki lists them by these names already).
MANUAL_RU = {
    "akatsuki_cloak": "Плащ Акацуки",
    "anbu_armor": "Броня АНБУ",
    "armor_plate": "Пластина брони",
    "armor_plate_green": "Зелёная пластина брони",
    "fabric": "Ткань",
    "fabric_reinforced": "Прочная ткань",
    "fabric_reinforced_black": "Чёрная прочная ткань",
    "fabric_reinforced_green": "Зелёная прочная ткань",
    "flak_jacket": "Флак-жилет",
    "flak_jacket_new": "Флак-жилет (новый)",
    "katana": "Катана",
    "lonely_march": "Пластинка «Lonely March»",
    "headband_black": "Чёрная повязка (пустая)",
    "headband_blue": "Синяя повязка (пустая)",
    "headband_red": "Красная повязка (пустая)",
    "headband_cloud": "Повязка Облака",
    "headband_cloud_scratched": "Повязка Облака (поцарапанная)",
    "headband_custard": "Повязка Пудинга",
    "headband_grass": "Повязка Скрытой Травы",
    "headband_grass_scratched": "Повязка Скрытой Травы (поцарапанная)",
    "headband_lava": "Повязка Лавы",
    "headband_leaf": "Повязка Листа",
    "headband_leaf_black": "Повязка Листа (чёрная)",
    "headband_leaf_black_scratched": "Повязка Листа (чёрная, поцарапанная)",
    "headband_leaf_scratched": "Повязка Листа (поцарапанная)",
    "headband_mist": "Повязка Тумана",
    "headband_mist_scratched": "Повязка Тумана (поцарапанная)",
    "headband_pride": "Повязка Гордости",
    "headband_rain": "Повязка Дождя",
    "headband_rain_scratched": "Повязка Дождя (поцарапанная)",
    "headband_rock": "Повязка Камня",
    "headband_rock_scratched": "Повязка Камня (поцарапанная)",
    "headband_sand": "Повязка Песка",
    "headband_sand_scratched": "Повязка Песка (поцарапанная)",
    "headband_sound": "Повязка Звука",
    "headband_sound_scratched": "Повязка Звука (поцарапанная)",
    "headband_waterfall": "Повязка Водопада",
    "headband_waterfall_scratched": "Повязка Водопада (поцарапанная)",
    "headband_youtube": "Повязка Мемов",
}

# Vanilla ingredients the mod's recipes use. The client jar only ships English names, and the wiki
# is Russian, so the handful of names that actually appear in a grid lives here.
VANILLA_RU = {
    "amethyst_shard": "аметист",
    "black_dye": "чёрный краситель",
    "blue_dye": "синий краситель",
    "blue_ice": "синий лёд",
    "bowl": "миска",
    "bread": "хлеб",
    "brown_mushroom": "коричневый гриб",
    "carrot": "морковь",
    "charcoal": "древесный уголь",
    "compass": "компас",
    "cooked_beef": "стейк",
    "cooked_cod": "жареная треска",
    "cooked_porkchop": "жареная свинина",
    "crafting_table": "верстак",
    "cyan_dye": "бирюзовый краситель",
    "diamond": "алмаз",
    "dried_kelp": "сушёная ламинария",
    "flint": "кремень",
    "flower_pot": "цветочный горшок",
    "glowstone_dust": "светопыль",
    "gold_ingot": "золотой слиток",
    "green_dye": "зелёный краситель",
    "gunpowder": "порох",
    "ink_sac": "чернильный мешок",
    "iron_ingot": "железный слиток",
    "iron_nugget": "железный самородок",
    "lapis_lazuli": "лазурит",
    "leather": "кожа",
    "light_blue_dye": "голубой краситель",
    "lime_dye": "лаймовый краситель",
    "magenta_dye": "пурпурный краситель",
    "magma_cream": "магмовая слизь",
    "oak_leaves": "дубовые листья",
    "oak_sapling": "дубовый саженец",
    "paper": "бумага",
    "potato": "картофель",
    "prismarine_shard": "призмариновый осколок",
    "red_dye": "красный краситель",
    "redstone": "красный камень",
    "sand": "песок",
    "spider_eye": "паучий глаз",
    "stick": "палка",
    "stone": "камень",
    "string": "нитка",
    "sugar": "сахар",
    "sweet_berries": "сладкие ягоды",
    "wheat": "пшеница",
    "white_wool": "белая шерсть",
    "yellow_dye": "жёлтый краситель",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load_lang(path: Path) -> dict:
    return json.loads(read(path)) if path.exists() else {}


def split_id(item: str) -> tuple[str, str]:
    if ":" in item:
        return tuple(item.split(":", 1))  # type: ignore[return-value]
    return "minecraft", item


class Icons:
    """Resolves an item id to a PNG copied under docs/assets, and remembers what it wrote."""

    def __init__(self) -> None:
        self.cache: dict[str, str | None] = {}
        self.files: dict[Path, bytes] = {}
        self.jar = zipfile.ZipFile(CLIENT_JAR) if CLIENT_JAR.exists() else None

    def close(self) -> None:
        if self.jar:
            self.jar.close()

    def _jar_read(self, name: str) -> bytes | None:
        if not self.jar:
            return None
        try:
            return self.jar.read(name)
        except KeyError:
            return None

    def _mod_texture_path(self, ref: str) -> Path | None:
        ns, path = split_id(ref)
        if ns != "narutomod":
            return None
        candidate = MOD_TEXTURES / (path + ".png")
        if candidate.exists():
            return candidate
        # Textures are split between the legacy plural folders and the newer ones.
        for folder in ("items", "item", "blocks", "block"):
            candidate = MOD_TEXTURES / folder / (path + ".png")
            if candidate.exists():
                return candidate
        return None

    def _mod_model_texture(self, item: str) -> str | None:
        assets = ROOT / "src/main/resources/assets"
        current = MODELS / (item + ".json")
        if not current.exists():
            return None
        seen = set()
        while current and current not in seen:
            seen.add(current)
            try:
                data = json.loads(read(current))
            except (OSError, ValueError):
                return None
            textures = data.get("textures") or {}
            for key in ("layer0", "texture", "all", "side", "top", "particle"):
                if key in textures:
                    return textures[key]
            parent = data.get("parent")
            # Forge's separate_transforms (the katana) keeps the GUI model under perspectives.gui.
            gui = (data.get("perspectives") or {}).get("gui") or {}
            base = data.get("base") or {}
            if gui.get("parent"):
                parent = gui["parent"]
            elif base.get("parent") and not parent:
                parent = base["parent"]
            if not parent:
                return None
            ns, path = split_id(parent)
            if ns != "narutomod":
                # Vanilla parents (item/generated, block/cube_all) carry no textures of ours.
                return None
            current = assets / "narutomod/models" / (path + ".json")
        return None

    def resolve(self, item: str) -> str | None:
        if item in self.cache:
            return self.cache[item]
        ns, path = split_id(item)
        data: bytes | None = None
        out: Path | None = None

        if ns == "narutomod":
            ref = self._mod_model_texture(path)
            if ref:
                tex = self._mod_texture_path(ref)
                if tex:
                    data = tex.read_bytes()
                else:
                    # The model may borrow a vanilla texture (the chakra compass uses the compass).
                    rns, rpath = split_id(ref)
                    if rns == "minecraft":
                        data = self._jar_read(
                            "assets/minecraft/textures/%s.png" % rpath
                        )
            if data is None:
                for folder in ("items", "item", "blocks", "block"):
                    tex = MOD_TEXTURES / folder / (path + ".png")
                    if tex.exists():
                        data = tex.read_bytes()
                        break
            if data is not None:
                name = path.replace("/", "_") + ".png"
                out = DOC_TEXTURES / name

        if data is None and self.jar:
            candidates = [
                "assets/minecraft/textures/item/%s.png" % path,
                "assets/minecraft/textures/block/%s.png" % path,
                "assets/minecraft/textures/block/%s_top.png" % path,
                "assets/minecraft/textures/block/%s_front.png" % path,
                "assets/minecraft/textures/block/%s_side.png" % path,
            ]
            # Animated compasses and clocks are a vertical strip; take a normal frame instead.
            if path in ("compass", "recovery_compass"):
                candidates.insert(0, "assets/minecraft/textures/item/%s_16.png" % path)
            if path == "clock":
                candidates.insert(0, "assets/minecraft/textures/item/clock_00.png")
            for entry in candidates:
                data = self._jar_read(entry)
                if data:
                    break
            if data is not None:
                out = DOC_VANILLA / (path.replace("/", "_") + ".png")

        if data is None or out is None:
            self.cache[item] = None
            return None
        self.files[out] = data
        rel = out.relative_to(ROOT / "docs").as_posix()
        self.cache[item] = rel
        return rel


def ingredient_id(entry) -> tuple[str, int]:
    if isinstance(entry, list):
        entry = entry[0] if entry else {}
    if not isinstance(entry, dict):
        return "minecraft:air", 1
    count = int(entry.get("count", 1))
    if "item" in entry:
        return entry["item"], count
    if "tag" in entry:
        # No tags are used by the shipped recipes; a fake id still renders a labelled slot.
        return "tag:" + entry["tag"], count
    return "minecraft:air", 1


def clean_name(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def build(results: dict, icons: Icons, ru: dict, en: dict) -> dict:
    items: dict[str, list] = {}
    all_names: dict[str, str] = {}
    all_icons: dict[str, str | None] = {}

    for path in sorted(RECIPE_DIR.glob("*.json")):
        recipe = json.loads(read(path))
        result = recipe.get("result")
        if not isinstance(result, dict) or "item" not in result:
            continue
        rid = result["item"]
        ns, short = split_id(rid)
        if ns == "narutomod":
            name = (
                MANUAL_RU.get(short)
                or ru.get("item.narutomod." + short)
                or ru.get("block.narutomod." + short)
                or en.get("item.narutomod." + short)
                or short.replace("_", " ").title()
            )
        else:
            name = VANILLA_RU.get(short, short.replace("_", " ").title())
        name = clean_name(name)
        all_names[rid] = name

        shaped = recipe.get("type", "").endswith("crafting_shaped")
        grid: list = [None] * 9
        ingredients: list[str] = []

        if shaped:
            pattern = recipe.get("pattern") or []
            key = recipe.get("key") or {}
            for row, line in enumerate(pattern[:3]):
                for col, char in enumerate(line[:3]):
                    if char == " " or char not in key:
                        continue
                    iid, _ = ingredient_id(key[char])
                    grid[row * 3 + col] = iid
                    ingredients.append(iid)
        else:
            slot = 0
            for entry in recipe.get("ingredients") or []:
                iid, count = ingredient_id(entry)
                if iid.endswith(":air"):
                    continue
                for _ in range(min(count, 9 - slot)):
                    ingredients.append(iid)

        # Pack shapeless ingredients into the grid in the order they are listed.
        if not shaped:
            slot = 0
            for iid in ingredients:
                if slot >= 9:
                    break
                while slot < 9 and grid[slot] is not None:
                    slot += 1
                if slot < 9:
                    grid[slot] = iid
                    slot += 1

        for iid in sorted(set(ingredients)):
            if iid not in all_names:
                ns, short = split_id(iid)
                all_names[iid] = (
                    MANUAL_RU.get(short)
                    or ru.get("item.narutomod." + short)
                    or ru.get("block.narutomod." + short)
                    or en.get("item.narutomod." + short)
                    or VANILLA_RU.get(short)
                    or short.replace("_", " ").title()
                )

        entry = {
            "id": rid,
            "name": name,
            "count": int(result.get("count", 1)),
            "icon": icons.resolve(rid),
            "shaped": shaped,
            "grid": grid,
            "ingredients": [
                {"id": iid, "name": all_names.get(iid, iid), "icon": icons.resolve(iid)}
                for iid in ingredients
            ],
            "source": path.stem,
        }
        all_icons[rid] = entry["icon"]
        for item in entry["ingredients"]:
            all_icons.setdefault(item["id"], item["icon"])
        items.setdefault(rid, []).append(entry)

    return {
        "recipes": items,
        "names": all_names,
        "icons": all_icons,
        "aliases": aliases(items, all_names),
    }


def aliases(items: dict, names: dict) -> dict:
    """Name -> item id, lowercase, only for names that are unambiguous on a wiki page."""
    generic = {
        "ткань",
        "броня",
        "пластина брони",
        "повязка",
        "маска анбу",
        "свиток",
        "печать",
        "компас",
        "батарея",
        "кристалл",
        "дерево",
        "картина",
        "рамен",
        "игла",
        "маска",
        "жилет",
        "плащ",
        "пилюли",
        "утяжелители",
        "бумажная бомба",
        "дымовая бомба",
        "катана",
        "кунай",
        "сюрикен",
    }
    out: dict[str, str] = {}
    for rid in items:
        name = names.get(rid, "")
        variants = {name}
        if "(" in name:
            variants.add(name.split("(")[0])
            variants.add(name.split("(")[1].rstrip(")"))
        variants.add(name.replace("ё", "е"))
        for variant in variants:
            variant = clean_name(variant).lower()
            if len(variant) < 5 or variant in generic:
                continue
            out.setdefault(variant, rid)
    # Explicitly useful shorter forms; full names above stay clickable on their own.
    extra = {
        "кунай": "narutomod:kunai",
        "куная": "narutomod:kunai",
        "кунаи": "narutomod:kunai",
        "катана": "narutomod:katana",
        "катаны": "narutomod:katana",
        "катаной": "narutomod:katana",
        "самехада": "narutomod:samehada",
        "самехады": "narutomod:samehada",
        "кубикирибочо": "narutomod:kubikiribocho",
        "игла": "narutomod:senbon",
        "иглы": "narutomod:senbon",
        "иглу": "narutomod:senbon",
        "сенбон": "narutomod:senbon",
        "сюрикен": "narutomod:shuriken",
        "сюрикена": "narutomod:shuriken",
        "сюрикены": "narutomod:shuriken",
        "бумажная бомба": "narutomod:paper_bomb",
        "бумажной бомбы": "narutomod:paper_bomb",
        "бумажную бомбу": "narutomod:paper_bomb",
        "бумажные бомбы": "narutomod:paper_bomb",
        "дымовая бомба": "narutomod:smoke_bomb",
        "дымовой бомбы": "narutomod:smoke_bomb",
        "дымовую бомбу": "narutomod:smoke_bomb",
        "дымовые бомбы": "narutomod:smoke_bomb",
        "рамен": "narutomod:ramen",
        "утяжелители": "narutomod:training_weights",
        "катаны": "narutomod:katana",
        "бонсай": "narutomod:bonsai_tree",
        "онигири": "narutomod:onigiri",
        "данго": "narutomod:dango",
    }
    out.update(extra)
    return out


def write_js(payload: dict) -> str:
    return (
        "// Generated by tools/build_docs_recipes.py - do not edit by hand.\n"
        "window.NARUTO_RECIPES = "
        + json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        + ";\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if out of date")
    args = parser.parse_args()

    ru = load_lang(LANG_RU)
    en = load_lang(LANG_EN)
    icons = Icons()
    try:
        payload = build({}, icons, ru, en)
    finally:
        icons.close()

    js = write_js(payload)
    old = read(OUT_JS) if OUT_JS.exists() else ""
    stale_icons = [
        (path, data)
        for path, data in icons.files.items()
        if not path.exists() or path.read_bytes() != data
    ]

    print(
        "recipes: %d items, %d aliases, %d icons"
        % (len(payload["recipes"]), len(payload["aliases"]), len(icons.files))
    )
    if js == old and not stale_icons:
        print("docs/recipes-data.js is already up to date")
        return 0
    if args.check:
        if js != old:
            print(
                "docs/recipes-data.js is OUT OF DATE - run tools/build_docs_recipes.py"
            )
        if stale_icons:
            print("%d icon(s) out of date" % len(stale_icons))
        return 1

    if js != old:
        OUT_JS.write_text(js, encoding="utf-8", newline="\n")
        print("wrote %s (%d KB)" % (OUT_JS, len(js.encode("utf-8")) // 1024))
    DOC_TEXTURES.mkdir(parents=True, exist_ok=True)
    DOC_VANILLA.mkdir(parents=True, exist_ok=True)
    for path, data in stale_icons:
        path.write_bytes(data)
    if stale_icons:
        print("wrote %d icon(s)" % len(stale_icons))
    return 0


if __name__ == "__main__":
    sys.exit(main())
