# Sage Mode & Six Paths Sage Mode — Design Doc

> Naruto Mod (MC 1.20.1 / Forge). Branch: sage + six paths.  
> Covers: **Senjutsu Charging**, **Sage Mode (toad)**, **Six Paths Mode (Rinnegan)**, **Truth-Seeking Orbs** (launch / guard / form), and their integration with **tailed beast chakra**.  
> Goal: sage mode feels earned and time-limited; Six Paths feels like a divine minute that costs everything; orbs feel like a halo of annihilating nothing; tailed beast chakra and sage chakra stack instead of competing.

---

## 1. Canon grounding

- **Sage Mode (Senjutsu)**: blend natural energy with own chakra. Requires standing still to gather; cannot replenish while moving. Enhances physical stats, senses, ninjutsu. Imperfect form (Jiraiya) mutates; perfect form (Naruto) keeps human shape with toad eyes. Weakness: limited duration, vulnerable while gathering. Bypassed by shadow clones gathering for you (max 5) or by tailed beast mode (gather in seconds).
- **Six Paths Sage Mode (Rikudō Senjutsu)**: divine state gifted by Hagoromo. Flight, fundamental chakra manipulation, sensing across dimensions, immunity to ordinary ninjutsu. Carries **Truth-Seeking Orbs** (9 for Naruto): black spheres of all five natures + Yin-Yang, turn touched things to dust, nullify ninjutsu, cannot negate senjutsu. Orbs are finite, physical-ish, controllable within ~70 m, lost permanently if separated/destroyed. Only Six Paths (or Ten-Tails jinchūriki) can touch them.
- **Progression**: Sage Mode first (Mount Myōboku). Then, with Rinnegan + all tailed beast chakra (or Hagoromo's gift), Six Paths Sage Mode. In Boruto, Naruto can hold sage mode far longer and without Kurama's help; Six Paths chakra persists but orbs lost after war.
- **Tailed beast synergy**: jinchūriki in tailed beast mode gathers natural energy in seconds; sage chakra + beast chakra = Six Paths cloak. Tailed Beast Ball Rasenshuriken uses orbs as base. Kurama's chakra is the binding agent for all nine beasts' chakra.

---

## 2. Progression ladder

```
Senjutsu Charging (gather nature energy)
        │
        ▼
Sage Mode (toad)  ──►  mastery: longer hold, perks unlock one at a time
        │
        ├── needs Rinnegan ──► Six Paths Mode (ritual: 100 senin, chakra upkeep)
        │                            │
        │                            ├── Truth-Seeking Orbs (9) ──► launch / guard / form
        │                            └── flight, regen, technique damage halved
        │
        └── needs tailed beast chakra ──► Chakra Cloak can stay up alongside sage
                                              (leavesRoomFor: SageMode + ChakraCloak)
                                              └── Tailed Ball (beast bomb) usable in Six Paths
```

- **ModeBus**: Six Paths Mode is a major body mode; Sage Mode is NOT in ModeBus (it is a buff stance that can layer with chakra cloak). Chakra Cloak explicitly allows Sage Mode (`leavesRoomFor`). Six Paths Mode closes other major modes on open.
- **Gates**: Sage Mode gated by `NarutoConfig.sageModeEnabled` + senin threshold (50, skill-reducible). Six Paths gated by `rinneganEnabled` + active Rinnegan + 100 senin + cooldown 5 min.
- **Tailed beast gate for orbs**: orb *count* and orb *damage* scale with tailed beast chakra available (see §7). Without beast chakra, Six Paths still opens (flight/regen/immunity) but orbs are inert (count 0) — keeps it useful but not broken.

---

## 3. Resources

| Resource | Owner | Cap | Regen | Spent by |
|---|---|---|---|---|
| Senin mana (nature energy) | NinjaData | maxSeninMana (skill) | Senjutsu Charging (still, env multiplier, sacred tree ×2) | Sage Mode drain, Six Paths ritual (100), Six Paths chakra? |
| Chakra | NinjaData | maxChakra | regen delay | Six Paths upkeep (1.0/tick), orb techniques, tailed ball |
| Truth-Seeking Orbs | SixPathsModeAbility (persistent tag) | 9 | none (finite) | launch (1), guard (1 per swallowed shot), form (1/5s) |
| Mastery (per ability) | MasteryData | 0..1 | credit per tick held | discounts costs, scales stats/duration/damage/knockback |

- Senin mana persists across logout (saved). Sage Mode state does NOT persist (intentional). Six Paths mark persists in persistent data but is cleaned by tick if stale.
- Mastery is per-ability (`SAGE_MODE`, `SIX_PATHS_MODE`, `GUDODAMA_*`, `SENJUTSU_CHARGING`).

---

## 4. Mode specs

### 4.1 Sage Mode (toad) — `SageModeAbility`

| Stat | Base | Mastery (max) |
|---|---|---|
| Entry senin | 50 (skill: down to 0) | — |
| Drain | 0.02/tick × (12→1) by mastery | ~4 min for 100 senin at max |
| Chakra regen | 0.15→0.5/tick | 0.5 |
| Buffs (refreshed) | Speed I always; Strength I at 0.15; Resistance I at 0.35; Jump I at 0.5; Regen I at 0.7; Speed II at 0.6 | — |
| Duration | pool-based (no timer) | — |
| Cooldown | none (pool-gated) | — |

Feel: "earned by standing still". Beginner hold ~10–20s; master holds minutes. Perks unlock one at a time so first use is not overwhelming.  
Visual: toad eyes (client), `HAPPY_VILLAGER` particles, `CONDUIT_ACTIVATE` sound.  
Key code: `SageModeAbility.maintain` called from `PlayerEvents.onEntityUpdate`.

### 4.2 Six Paths Mode — `SixPathsModeAbility`

| Stat | Value |
|---|---|
| Ritual senin | 100 (flat) |
| Duration | 1200 ticks (60s) |
| Chakra upkeep | 1.0/tick (masteryCost) |
| Cooldown | 6000 ticks (5 min) |
| Bonus chakra pool | +100 (max with Rinnegan's own) |
| Orbs granted | 9 (base) → scales with beast chakra (§7) |
| Regen | 2 HP / 20 ticks; clears poison/wither/slow/blindness/darkness/fire/chill |
| Damage taken | technique damage ×0.5; form damage ×0.8 |
| Immunity | blindness, darkness (denied on apply) |
| Speed | +30% movement speed (transient modifier) |
| Flight | no gravity while not sneaking; sneak to descend |

Feel: "a minute of being more than a body". Costs 100 senin + chakra upkeep; running dry (orbs=0) closes it.  
Visual: `END_ROD` + `SQUID_INK` burst, `BEACON_ACTIVATE` (0.6 pitch).  
Key code: `SixPathsEvents.onPlayerTick` enforces clock/orbs/flight/regen.

### 4.3 Truth-Seeking Orb techniques

**Launch** (`GudodamaLaunchAbility`, combo `CCCCBC`? actually `2112` = C C C C B C? check combos): instant, 40 chakra, 20 tick CD. Bolt along look, 24 range, 4 block AoE, 20 damage, erases (no crater/fire). Spends 1 orb. Trail of `SQUID_INK` + `END_ROD`; impact `SQUID_INK`/`END_ROD`; sound `WITHER_SHOOT`.

**Guard** (`GudodamaGuardAbility`, toggle, combo `2111`): 0.2 chakra/tick. Ring of orbs sweeps every 5 ticks within 3 blocks; swallows projectiles (1 orb per shot). `END_ROD` ring particles. Perfect vs volleys, cannot be held forever (orbs run out).

**Form** (`GudodamaFormAbility`, toggle, combo `2113`): 0.2 chakra/tick + 1 orb / 5s. +6 attack damage (transient), −20% incoming damage (via SixPathsEvents), `SQUID_INK` motes. Aggressive brawler stance.

---

## 5. Truth-Seeking Orbs halo — spec

Current renderer (`SixPathsOrbRenderer`) draws `count` black spheres on a ring behind the back, radius 0.9, height 0.72×bbHeight, behind 0.75, bob sin(time×0.05)×0.04, orbit speed 0.03 rad/tick. Texture: `minecraft:textures/block/black_concrete.png`. Sphere model in `TruthSeekerOrbModel`.

### Proposed improvements (feel + readability)

| Aspect | Current | Proposed |
|---|---|---|
| Count | 9 static | 9 base, scales with beast chakra (§7); ring redistributes |
| Size | 0.18 | 0.16 base; grows 0.02 per combined orb when few (≤3) for "charged" look |
| Orbit | single ring, flat behind back | **halo**: primary ring behind back (radius 0.9); when count ≥7, second inner ring (radius 0.5, height +0.15) so halo reads as 3D crown |
| Motion | uniform rotation 0.03 | 0.025 base + 0.005×(9−count) so fewer orbs orbit faster (urgency); gentle bob per-orb with phase offset (i/​count) |
| Visual | solid black concrete | black core + thin `END_ROD` rim glow (additive layer) + occasional `SQUID_INK` drip (1/tick per orb cluster) |
| Trail on spend | none | when an orb is spent, the spent slot emits a 6-particle `END_ROD` burst and the ring re-flows over 10 ticks (lerp count) |
| Knockback feedback | none | on launch impact, ring pulses (scale 1.0→1.15→1.0 over 6 ticks) on all remaining orbs |
| Owner-only vs global | rendered for all players | keep global (spectators see halo); cull when count=0 |
| Client sync | reads `getSixPathsOrbs()` + fallback `orbs()` | add `@Sync` field already exists; ensure partialTick interpolation for orbit angle (currently uses gameTime+partialTick ✓) |

### Knockback feel (launch)

- Direct hit + AoE 4 blocks. Add: `knockback(1.2, dx, dz)` to each caught entity (horizontal) + `push(0, 0.4, 0)`; if entity hits wall within 0.5 block, deal 25% of damage as bonus (wall splat). Sound: `WITHER_SHOOT` already; add `GENERIC_EXPLODE` (volume 0.6) at impact for weight. Particles: `EXPLOSION` (small) + `SQUID_INK` cloud.
- Guard: swallowed projectile's shooter gets a tiny knockback (0.3) toward caster? No — guard should feel like absorption, not retaliation. Keep neutral.
- Form: melee knockback already via vanilla; add +0.3 horizontal on each hit while form active (in TailedBeast-style onHit hook), so brawl feels heavy.

---

## 6. Progressive improvements (mastery)

Mastery per ability via `recordMasteryCredit` (per tick held / per senin gathered). Discounts via `masteryCost`. Proposed tiers:

**Sage Mode** (already has perk ladder; extend):
- Drain: 12× → 1× over mastery (done).
- Entry senin: 50 → 25 at ≥0.5 mastery (skill `SAGE_ENTRY` already reduces; add mastery floor).
- Buffs: Speed II at 0.6 (done), Strength II at 0.85, Resistance II at 0.9, Regen II at 1.0.
- New: at ≥0.7 mastery, **sage mode can be opened while moving** (no stillness penalty for opening; gathering still requires stillness) — mirrors canon "Naruto learned to enter mid-combat".

**Six Paths Mode**:
- Duration: 60s → 90s at full mastery.
- Upkeep: 1.0 → 0.7 chakra/tick.
- Cooldown: 5 min → 3.5 min.
- Regen: 2 HP/20t → 3 HP/20t; clear fire always (done).
- Orb count: 9 base; +1 at ≥0.5 mastery (max 10) only if beast chakra present.

**Orb techniques**:
- Launch: damage 20 → 32; CD 20 → 14 ticks; AoE 4 → 5; cost 40 → 30.
- Guard: radius 3 → 4; sweep 5 → 3 ticks; cost 0.2 → 0.14.
- Form: +6 → +10 damage; orb drain 5s → 7s; damage reduction 20% → 30%.

**Senjutsu Charging**:
- Gather: 0.5/tick → 0.75/tick at full mastery; sacred tree bonus 2× → 2.5×.
- Mastery credit: gathered/maxSenin (done).

---

## 7. Integration with tailed beast chakra

This is the core "deep and powerful" axis: sage and beast chakra should compound, not exclude.

### 7.1 Coexistence rules

- `ChakraCloakAbility.leavesRoomFor` already permits `SageModeAbility` and `SenjutsuChargingAbility` while cloak is up. **Extend**: also permit `SixPathsModeAbility` when cloak is up, so a caster can open Six Paths on top of a tailed cloak (canon: Six Paths Sage Mode = sage + beast chakra). ModeBus currently closes chakra cloak on Six Paths open — change to keep it (add `SixPathsModeAbility.ID → {ChakraCloakAbility.ID, TailedCloakAbility.ID}` to PREREQUISITES) when beast chakra is present.
- `hasBeastChakra` (TailedBallAbility) already checks tailed/perfect/divided/seal-break cloaks. Add: Six Paths Mode with ≥1 orb also counts as "beast chakra present" if the caster had beast chakra when opening (store a boolean tag `six_paths_beast_chakra`).

### 7.2 Orb scaling with beast chakra

| Beast chakra state | Orb count | Orb damage multiplier | Notes |
|---|---|---|---|
| None (pure sage + Rinnegan) | 0 (orbs inert) | — | Six Paths still gives flight/regen/immunity; orbs render as empty halo slots (ghost) |
| Chakra Cloak only | 3 | ×0.7 | entry-level Six Paths |
| Tailed Cloak | 5 | ×0.85 | |
| Divided Cloak | 5 | ×0.85 | squad shares orb count? no — caster's own |
| Perfect Cloak / Seal Break | 9 | ×1.0 | full canon |
| Full tailed beast (all 9 vessels, future) | 10 | ×1.2 | endgame |

- Store `beastChakraTier` (0..4) on open; `orbs()` reads it. Ghost slots (count 0) render as faint translucent spheres so the halo is always visible as "potential".
- Launch damage = base × multiplier; guard swallow count unchanged (1 orb/shot) but radius +0.5 when tier ≥3.

### 7.3 Tailed Beast Ball + orbs

- `TailedBallAbility` already requires beast chakra. Add synergy: if Six Paths Mode is active, the bomb's power multiplier +0.2 and the thrown entity applies a 2-block "orb dust" field on detonation (entities in radius take 8 magic damage + knockback 1.0) — mirrors canon Tailed Beast Ball Rasenshuriken using orbs as base.
- If orbs = 0, no dust field (just normal bomb).

### 7.4 Chakra pool synergy

- While both Chakra Cloak and Six Paths are up: chakra upkeep stacks (cloak 0.3 + six paths 1.0 = 1.3/tick) but chakra regen from sage mode (0.15–0.5) offsets. Net cost ~0.8–1.15/tick. This is the intended "powerful but expensive" feel.
- Senin mana: Six Paths ritual costs 100; if sage mode is also draining, senin runs out faster — caster must choose between sustained sage buffs and the Six Paths minute. This is the core tradeoff.

### 7.5 Visual layering

- `ChakraCloakLayer` already branches by vessel forms (gold). Add: when Six Paths active, overlay a thin black ring (orb halo) on top of cloak shell; when beast chakra tier ≥3, add soul-fire flicker to the cloak. Ensures the combined form reads as "sage + beast" not just one or the other.

---

## 8. Visual distinction matrix

| Form | Colour | Particle | Sound (open) | Unique cue |
|---|---|---|---|---|
| Senjutsu Charging | green | HAPPY_VILLAGER | none | stillness ring, progress message |
| Sage Mode | toad eyes, green motes | HAPPY_VILLAGER | CONDUIT_ACTIVATE | eye change, buff icons |
| Six Paths Mode | black halo + golden cloak | END_ROD + SQUID_INK | BEACON_ACTIVATE 0.6 | halo crown, flight, regen |
| Orbs (halo) | solid black + END_ROD rim | SQUID_INK drip | — | orbit speed scales with count |
| Launch | black bolt | SQUID_INK trail + END_ROD | WITHER_SHOOT + EXPLODE | 4-block dust cloud |
| Guard | black ring | END_ROD ring | none | projectile vanish |
| Form | black motes on arms | SQUID_INK | none | melee impact sparks |
| Combined (beast+sixpaths) | gold cloak + black halo + soul fire | SOUL_FIRE_FLAME | BEACON + BLAZE | layered shell |

---

## 9. Tradeoffs summary

| Form | Power | Cost | Risk | Best for |
|---|---|---|---|---|
| Senjutsu Charging | none (enabler) | time + stillness | interrupted by movement/env | setup |
| Sage Mode | mid (stats/senses) | senin pool (pool-based) | short hold for beginners | skirmish, Pain-tier fights |
| Six Paths Mode | high (flight/immunity/regen) | 100 senin + 1.0 chakra/tick + 5min CD | orbs run out → closes; no refresh | boss fights, Madara-tier |
| Launch | high burst (20 dmg, 4 AoE) | 40 chakra + 1 orb | orb finite | ranged finisher |
| Guard | perfect defence vs volleys | 0.2/tick + 1 orb/shot | drains orbs fast | projectile-heavy fights |
| Form | sustained melee (+6 dmg, −20% dmg) | 0.2/tick + 1 orb/5s | drains orbs over time | brawl |
| Combined (beast+sixpaths) | highest sustained | stacks both upkeeps | senin+chakra both drain | endgame boss |

Core tension: **senin mana is shared** between sage buffs and Six Paths ritual; **orbs are finite and not regenerable**; **beast chakra is the multiplier** that turns Six Paths from "useful" into "divine".

---

## 10. Minecraft implementation notes

- **State**: persistent tags (`UNTIL_TAG`, `STARTED_TAG`, `ORBS_TAG`, `NEXT_ORB_TAG`, `beastChakraTier`, `six_paths_beast_chakra`) + `@Sync` `sixPathsOrbs` in NinjaData. Sage mode state in NinjaData (`isSageMode`) not persisted.
- **Tick enforcement**: `SixPathsEvents.onPlayerTick` (clock, orbs, flight, regen, cleanup); `PlayerEvents.onEntityUpdate` calls `SageModeAbility.maintain`.
- **ModeBus**: add prerequisite mapping for Six Paths → chakra cloak when beast tier ≥1.
- **Attributes**: transient modifiers with stable UUIDs (speed, damage, armour); re-apply every tick; remove on end/death/relog.
- **Boons**: short `MobEffectInstance` refreshed each tick.
- **AoE/knockback**: `level.getEntitiesOfClass` + `Friends.isAggressor`/`areFriends` filters; `entity.knockback(str, dx, dz)` + vertical push; wall-splat bonus check via `level.noCollision`.
- **Mastery credit**: `recordMasteryCredit` per tick (sage, six paths, form, guard upkeep, charging); launch credits on cast via damage? Currently no — add `recordMastery` on successful hit.
- **Noise**: `noiseRadius()` — Six Paths 24, Launch 20, others 0.
- **Client**: `SixPathsOrbRenderer` after entities; `ChakraCloakLayer` for shell; `ModeHudOverlay` for orb count + timers.
- **Config**: `NarutoConfig.sageModeEnabled`, `rinneganEnabled`; add `sixPathsBeastChakraRequired` (default false, for balance testing) and `orbKnockbackEnabled` (default true).
- **Lang**: all messages via `lang/*.json` (en_us + ru_ru); new keys for ghost orbs, combined form, wall splat.

---

## 11. Open questions / future work

- Should Six Paths require Rinnegan + beast chakra, or just Rinnegan? Proposed: Rinnegan required to open; beast chakra multiplies orbs/damage. Keeps it accessible but rewards investment.
- Orb regen: canon says orbs are lost permanently. Mod already treats them as finite per mode (reset on reopen). Should reopen after cooldown grant fresh 9? Proposed: yes, but count scales with current beast tier at open time.
- Sage mode mid-combat entry at high mastery: needs anti-spam (cooldown 10s on failed entry?).
- Ghost orbs visual: may clutter screen; make toggleable in config.
- Combined form visual layering: `ChakraCloakLayer` needs refactor to stack layers (cloak + halo + soul fire) without z-fighting.
- Mastery for Launch: currently no credit on hit; add.
- Should Guard swallow Tailed Beast Balls? Proposed: no (they're too big) — guard radius 3 < bomb radius; but bomb passing through guard ring should detonate early (orb consumed, bomb discarded). Add interaction.

---

## 12. File map

| File | Role |
|---|---|
| `abilities/jutsus/SenjutsuChargingAbility.java` | gather nature energy |
| `abilities/jutsus/SageModeAbility.java` | toad sage mode + maintain + perks |
| `abilities/jutsus/SixPathsModeAbility.java` | ritual + clock + orbs + clear |
| `abilities/jutsus/GudodamaLaunchAbility.java` | orb bolt |
| `abilities/jutsus/GudodamaGuardAbility.java` | orb shield (swallow) |
| `abilities/jutsus/GudodamaFormAbility.java` | orb weapon form |
| `events/SixPathsEvents.java` | clock, orbs, flight, regen, damage factors, immunity |
| `events/PlayerEvents.java` | calls SageModeAbility.maintain |
| `client/renderer/SixPathsOrbRenderer.java` | halo render |
| `client/model/jutsu/TruthSeekerOrbModel.java` | sphere model |
| `client/renderer/ChakraCloakLayer.java` | shell + vessel + (proposed) six paths overlay |
| `abilities/ModeBus.java` | major mode exclusivity + prerequisites |
| `abilities/jutsus/ChakraCloakAbility.java` | leavesRoomFor sage/sixpaths |
| `abilities/jutsus/TailedBallAbility.java` | beast bomb + hasBeastChakra |
| `capabilities/NinjaData.java` | senin, sage, sixPathsOrbs, sync |
| `config/NarutoConfig.java` | sage/rinnegan toggles |
| `client/ModeHudOverlay.java` | orb count + timers |