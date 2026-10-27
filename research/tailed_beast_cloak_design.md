# Tailed Beast Cloak Modes — Design Doc

> Naruto Mod (MC 1.20.1 / Forge). Branch: tailed-beast cloak modes.  
> Covers: **Chakra Cloak**, **Tailed Cloak**, **Divided Cloak**, **Perfect Cloak**, **Seal Break**, **Seal Vessel**.  
> Goal: each form feels distinct, powerful, and progression-gated; no two modes are interchangeable.

---

## 1. Canon grounding

- **Chakra Cloak** (Kyūbi Chakra Mode): golden/orange shell over the body. Speed, strength, sensing, chakra arms. Requires beast cooperation.
- **Tailed Cloak** (Version 1 tail drawn out): chakra cloak thickened into a tail; bulk, sweeping tail, beast roar that marks targets.
- **Divided Cloak** (chakra sharing): thin cloak spread over a squad; weak alone, strong together.
- **Perfect Cloak** (Bijū Mode, settled): full agreement; beast chakra arms strike on their own, high stats, drains vessel.
- **Seal Break** (cracked seal): beast drives for ~8s; AoE sweeps, no friendly-fire sparing, backlash + vessel loss.
- **Seal Vessel** (Eight Trigrams): ritual foundation; integrity resource + tails + duration.

Key canon tradeoffs: agreement vs cage (Seal Vessel integrity), solo power vs squad support, frenzy risk.

---

## 2. Progression ladder

```
Seal Vessel (ritual)  ──►  Divided Cloak (squad, cheap)
                      └──►  Perfect Cloak (solo, strong, drains vessel)
Chakra Cloak ──► Tailed Cloak (requires Chakra Cloak)
Seal Vessel (any integrity) ──► Seal Break (frenzy, backlash)
```

- **ModeBus exclusivity**: only one major body mode at a time. Tailed Cloak keeps Chakra Cloak; Seal Break II keeps Seal I. Eyes (dojutsu) burn alongside.
- **Seal Vessel is the gate** for Divided/Perfect/Seal Break. Chakra Cloak is standalone (no vessel needed) — entry-level.

---

## 3. Shared resource: Vessel (integrity)

| Field | Value |
|---|---|
| Max integrity | 100 (mastery: 120) |
| Tails | 3 |
| Duration | 6000 ticks (5 min; mastery 7 min) |
| Decay | 1 integrity / 60 ticks (3s) → runs out with duration |
| Ritual | 100 ticks (5s) standing still, chakra 190 (100 + 30×3) |
| Ritual drift tolerance | 1.0 block |
| Cooldown | 6000 ticks |

Integrity is spent by: Perfect Cloak (1/100 ticks), Seal Break (20 flat), time decay.  
When integrity ≤ 0 or time expires → vessel closes, all dependent forms drop.

---

## 4. Mode specs

### 4.1 Chakra Cloak — entry shell

| Stat | Base | Mastery (max) |
|---|---|---|
| Open cost | 20 chakra | 16 |
| Upkeep | 0.3/tick | 0.24 |
| Chakra regen delay | 10 ticks | — |
| Armour | +4 | +6 |
| Attack damage | +2 | +4 |
| Speed | none (mod) → **Speed I proposed** | Speed I |
| Effects | removes Glowing | — |
| Mark grace | 10 ticks | — |

Feel: "armour that is not armour". Quiet (noise 0). Full-body commitment: blocks other casts except utility/tailed/sage.  
Visual: thin orange-red flame shell, particles `FLAME`, sound `BLAZE_SHOOT` on open.

### 4.2 Tailed Cloak — bulk + tail (requires Chakra Cloak)

| Stat | Base | Mastery |
|---|---|---|
| Open cost | 40 | 32 |
| Upkeep | 0.6 | 0.48 |
| Armour | +12 | +16 |
| Boons (refreshed) | Strength I, Speed I, Jump I, Haste I (40 ticks) | II at max mastery |
| Sweep | every 20 ticks, r=5, dmg=6, knockback 0.4 horizontal + 0.6 vertical | dmg 9, r 6, kb 0.6/0.8 |
| Roar | every 100 ticks, r=8, Glowing 40 ticks | r 10, glow 60 |
| On-hit (melee) | knockback 1.6 + 0.6y, wall damage 50% HP | kb 2.0, wall 60% |
| Mark grace | 10 | — |

Feel: "bulk rather than finesse". Tail answers aggressors only (Friends.isAggressor). Roar marks mobs so nothing hides.  
Visual: thicker deep-red flame, tail sweep particle `SWEEP_ATTACK`, roar `RAVAGER_ROAR` + soul fire.

### 4.3 Divided Cloak — squad support (requires Vessel)

| Stat | Base | Mastery |
|---|---|---|
| Open cost | 20 | 16 |
| Upkeep | 0.2 | 0.16 |
| Armour | +2 | +3 |
| Damage | +1 | +2 |
| Speed | Speed I (self) | Speed I |
| Share radius | 6 | 8 |
| Ally buffs (60 ticks) | Resistance I, Strength I, Speed I | Resistance II, Strength I, Speed I |
| Threads | every 4 ticks, 8 points | every 3 ticks |
| Vessel drain | none (only time decay) | — |

Feel: "covers the caster's side, not the caster alone". Cheapest form, long-fight friendly. Threads visible hand→ally.  
Visual: soul motes, thin pale gold film (vessel). Sound `SOUL_ESCAPE`.

### 4.4 Perfect Cloak — settled beast (requires Vessel ≥ 40)

| Stat | Base | Mastery |
|---|---|---|
| Min integrity | 40 | 30 |
| Open cost | 80 | 64 |
| Upkeep | 1.0 | 0.8 |
| Damage | +10 | +14 |
| Boons (200 ticks) | Speed II, Resistance III, Jump II, Haste II | +Strength I |
| Arms | every 20 ticks, r=5, 2 closest aggressors, 8 dmg each | dmg 12, r 6 |
| Arm knockback | 1.2 + 0.5y | 1.5 |
| On-hit | kb 1.6 + 0.6y, wall 50% | kb 2.0, wall 60% |
| Vessel drain | 1 / 100 ticks (5s) | 1 / 150 ticks |
| Mark grace | 10 | — |

Feel: "agreement rather than cage". Beast's own arms strike whether caster swings. Fast, heavy-handed, hard to cut.  
Visual: soul fire flame, `BEACON_POWER_SELECT` on open, arms `SWEEP_ATTACK` + `SOUL`.

### 4.5 Seal Break — frenzy (requires Vessel, any integrity)

| Stat | Value |
|---|---|
| Duration | 160 ticks (8s; mastery 10s) |
| Cooldown | 1200 ticks (60s; mastery 45s) |
| Cost | free (vessel + body pay) |
| Boons | Speed II, Strength II, Resistance I (160 ticks) |
| Sweep | every 20 ticks, r=2, dmg=8 to ALL (allies/pets/clones included), kb 0.3 + 0.4y |
| Friendly fire | protection disabled for caster while active |
| Backlash | 6 HP magic damage (mastery: 4) |
| Vessel cost | 20 integrity (mastery: 15) |
| Noise | 24 (roar carries) |

Feel: "eight seconds of the beast driving". High risk/reward burst.  
Visual: red flame, `RAVAGER_ROAR`, particles `FLAME` dense.

### 4.6 Seal Vessel — foundation (ritual)

See §3. Visual: enchantment ring 16 points, `AMETHYST_BLOCK_CHIME` → `BEACON_ACTIVATE`.

---

## 5. Knockback / blast scaling table

| Mode | Auto AoE | Radius | Dmg | KB (horiz/vert) | On-hit KB | Wall bonus | Blast feel |
|---|---|---|---|---|---|---|---|
| Chakra Cloak | none | — | — | — | — | — | none |
| Tailed Cloak | sweep 1s | 5→6 | 6→9 | 0.4/0.6 | 1.6/0.6 | 50% HP | tail sweep + crater |
| Divided Cloak | none | — | — | — | — | — | none (buffs) |
| Perfect Cloak | arms 1s (2 targets) | 5→6 | 8→12 | 1.2/0.5 | 1.6/0.6 | 50% HP | beast arms crater |
| Seal Break | sweep 1s (all) | 2 | 8→12 | 0.3/0.4 | natural | none | frenzy burst |
| Seal Vessel | none | — | — | — | — | — | ritual ring |

Blasts are **cosmetic** (particles + sound) by default to avoid block destruction; optional config `tailedBlastDestroysBlocks` could enable `level.explode` with small radius 1.5–2.5 at perfect/seal-break tier only.

---

## 6. Progressive improvements (mastery)

Mastery is per-ability credit via `recordMasteryCredit` (per tick while worn). Discount via `masteryCost`. Proposed mastery tiers (0/25/50/75/100%):

- **Costs** drop 20% at full mastery (open + upkeep).
- **Stats** scale linearly +20% at full mastery (armour/damage bonuses).
- **AoE** radius +1 block at ≥50% mastery.
- **Damage** +50% at full mastery.
- **Knockback** +25% at full mastery.
- **Vessel drain** slows 33% at full mastery (perfect).
- **Seal Break** duration +2s, backlash -2 HP, integrity cost -5, cooldown -15s at full mastery.
- **Seal Vessel** integrity +20, duration +2 min, ritual -1s at full mastery.

---

## 7. Visual distinction matrix

| Mode | Colour | Particle | Sound (open) | Layer thickness | Unique cue |
|---|---|---|---|---|---|
| Chakra Cloak | orange-red | FLAME thin | BLAZE_SHOOT 1.4 pitch | thin shell | removes glow |
| Tailed Cloak | deep red | FLAME thick + SWEEP_ATTACK | BLAZE_SHOOT 1.0 | thicker + tail | roar marks mobs |
| Divided Cloak | pale gold/soul | SOUL motes + threads | SOUL_ESCAPE | thin film | hand→ally threads |
| Perfect Cloak | gold (vessel) | SOUL_FIRE_FLAME | BEACON_POWER_SELECT | full tail + arms | arms strike on own |
| Seal Break | red | FLAME dense | RAVAGER_ROAR | jagged | red chakra flicker 4 ticks |
| Seal Vessel | gold ring | ENCHANT ring | AMETHYST → BEACON | — | 16-point ring, 5s |

ChakraCloakLayer already branches: vessel forms burn gold (divided thin pale film, perfect whole tail).

---

## 8. Tradeoffs summary

| Mode | Power | Cost | Risk | Best for |
|---|---|---|---|---|
| Chakra Cloak | low-mid | low upkeep | blocks casts | skirmish entry |
| Tailed Cloak | mid-high | mid upkeep | none | solo duels, crowd control |
| Divided Cloak | low self / high squad | lowest upkeep | needs allies in range | team fights |
| Perfect Cloak | highest sustained | high upkeep + vessel drain | requires vessel ≥40, drains fast | boss fights |
| Seal Break | highest burst | free cast | backlash 6HP, -20 integrity, 60s CD, hits allies | desperate clutch |
| Seal Vessel | enabler | 190 chakra + 5s still | interruptible, wasted chakra | setup |

---

## 9. Minecraft implementation notes

- **State**: persistent data tags (`UNTIL_TAG`, `INTEGRITY_TAG`, etc.) + `@Sync` for HUD (NinjaData.sealVesselIntegrity).
- **Attributes**: transient modifiers with stable UUIDs; re-apply every tick; remove on end/death/relog (ModeSealEvents retireExpiredMarks).
- **Boons**: short MobEffectInstance refreshed each tick (auto-expire if stance drops).
- **AoE**: `level.getEntitiesOfClass` with `Friends.isAggressor` / `isOwnSide` filters; bounding box inflate. Sort by distance for arms.
- **Knockback**: `entity.knockback(strength, dx, dz)` or `push` + `hurtMarked = true`; wall check via `level.noCollision` + extra magic damage.
- **Friendly fire override**: `LivingHurtEvent` cancels protection while Seal Break active (ModeSealEvents.onLivingHurt).
- **Tick consequences**: TailedBeastEvents (ritual, decay, sweep, arms, drain). Intervals: sweep 20, roar 100, arms 20, drain 100, decay 60.
- **Mastery credit**: `recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK)` per tick while worn; instant forms credit on complete.
- **Noise**: `noiseRadius()` for chakra sensing / sound propagation (0 for cloaks, 16/24 for vessel/break).
- **Client**: performToggleClient no-op (server syncs); HUD labels in ModeHudOverlay.

---

## 10. Open questions / future work

- Add Speed I to Chakra Cloak (canon) — currently absent; would improve entry feel but risk overlap with Tailed. Proposed: keep Speed I only at mastery or move Tailed to Speed II.
- Blast block destruction toggle (config) for Perfect/Seal Break.
- Tail count (3) → could scale arms count? Canon beast arms are 2; keep 2 fixed.
- Divided Cloak could grant caster Resistance I too (currently only allies) for consistency.
- Visual: ChakraCloakLayer gold branch already exists; ensure divided/perfect textures differ in thickness.

---

## 11. File map

| File | Role |
|---|---|
| `abilities/jutsus/ChakraCloakAbility.java` | base shell |
| `abilities/jutsus/TailedCloakAbility.java` | tail + sweep + roar + onHit |
| `abilities/jutsus/DividedCloakAbility.java` | squad share + threads |
| `abilities/jutsus/PerfectCloakAbility.java` | arms + boons + onHit |
| `abilities/jutsus/SealBreakAbility.java` | frenzy mark + backlash |
| `abilities/jutsus/SealVesselAbility.java` | ritual + vessel resource |
| `events/TailedBeastEvents.java` | ritual, decay, sweeps, arms, drain, clear |
| `events/ModeSealEvents.java` | friendly fire override, retire marks |
| `abilities/ModeBus.java` | exclusivity |
| `client/renderer/ChakraCloakLayer.java` | visuals |
| `client/ModeHudOverlay.java` | HUD labels |
