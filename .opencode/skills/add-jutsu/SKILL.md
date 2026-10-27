---
name: Add Jutsu
description: Add a new technique (jutsu) to Naruto Mod without duplicating an existing one. Use when the user asks to add/create/implement a new technique, jutsu, ability, or clone variant.
---

# Add a new jutsu

The mod already ships ~190 techniques. Most "new clone" / "new fireball" requests are a reskin of
something that exists. **Do the duplicate check first; do not write code before it passes.**

## Step 0 - duplicate / similarity check (mandatory)

1. Run `python tools/jutsu_index.py --similar "<Name>"` and read the top results.
2. If a result shares the same base class (e.g. `ShadowCloneJutsuAbility`) **and** the same
   activation type **and** a similar cost, the idea is almost certainly a duplicate. Stop and tell
   the user what already exists and how it differs; ask whether they still want a new one.
3. Run `python tools/jutsu_index.py --dup` - it must stay empty. Pick a combo not present in the
   index (combos are `long` literals like `11221L`).
4. Grep the planned effect in `abilities/jutsus/` (e.g. "clone", "fireball", "shield") for
   near-matches by hand.

Only proceed when there is a clear **mechanical** difference: different target, different cost
model, different side effect, different gating. "Same thing but stronger" is not enough - fold that
into mastery or a skill node instead.

## Fast path (implementation)

1. Create `src/main/java/com/sekwah/narutomod/abilities/jutsus/<Name>Ability.java`
   (or `abilities/utility/`). Extend `Ability` or a shared base
   (`AbstractBreathAbility`, `AbstractDojutsuAbility`, `ShadowCloneJutsuAbility` settings).
   - Unique `COMBO`, costs, `activationType()`, `defaultCombo()`, `handleCost`, `performServer`.
   - Price via `masteryCost(...)`; cooldown via `Ability.Cooldown#registerCooldown`.
2. Register in `NarutoAbilities.java`: import + `ABILITY.register("snake_case", ...::new)`.
3. Gate: basics in `AbilityUnlocks.BASIC` / `SCROLL_ONLY`, otherwise add skill node
   `ability:<path>` through the skill-tree flow (see skilltree-apply) - never hand-edit `Skill.java`.
4. Lang: add `"narutomod:<path>": "..."` to both `en_us.json` and `ru_ru.json` (key = resource
   location). Add fail messages if needed.
5. Config: toggle in `NarutoConfig` if the technique can be disabled.
6. Damage type: new damage => JSON in `data/narutomod/damage_type/` + entry in `NarutoDamageTypes`.
7. Build: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat compileJava`.

## Rules

- Server is source of truth: cost in `handleCost`, effect in `performServer`; client only sends a
  packet and the server re-checks.
- Strings only via `lang/*.json`; no hardcode. Comments/Javadoc in English.
- Do not copy an existing technique's body verbatim. If the difference is only numbers, reuse the
  existing one and add mastery / a skill node.

## After

- `python tools/jutsu_index.py --json` to refresh the index.
- Skill-tree changed => run the skilltree-apply pipeline.
- Report: class, combo, cost, base class, and whether anything close already existed.