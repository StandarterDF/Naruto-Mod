# Eight Inner Gates — Design Document

**Mod:** Sekwah Naruto Mod (Minecraft 1.20.1 / Forge)  
**Ability:** `eight_gates` (`abilities/jutsus/EightGatesAbility.java`)  
**Status:** Core loop (open/seal, drain, buffs) implemented. This doc specifies the *per-gate* progression, impact scaling, wall-slam, visual feedback and recovery rules that make each gate feel like a distinct step up rather than a flat "more speed" buff.

---

## 1. Canon grounding (what we are porting)

The Eight Inner Gates are eight tenketsu that cap the body at ~20 % of its potential. Forcing them open lifts the cap, one gate at a time, at the cost of destroying the body. Key beats:

| # | Gate (jp) | Location | Unlocks | Visual |
|---|-----------|----------|---------|--------|
| 1 | Opening (Kaimon) | brain (left) | Front Lotus | — |
| 2 | Healing (Kyūmon) | brain (right) | re-energises body | — |
| 3 | Life (Seimon) | spinal cord | Reverse Lotus | skin turns red |
| 4 | Pain (Shōmon) | spinal cord | — | muscles tear, nosebleed |
| 5 | Limit (Tomon) | abdomen | — | compound fractures |
| 6 | View (Keimon) | stomach | Morning Peacock | green aura, water displaced |
| 7 | Wonder (Kyōmon) | below stomach | Daytime Tiger | blue sweat → aura, fibres ripped |
| 8 | Death (Shimon) | heart | Evening Elephant, Night Guy | red "steam of blood", cooked from inside |

Rules we must respect:

- Gates open **in order**; opening gate *n* implies 1..*n* are open.
- The 8th gate **cannot be sealed** — death is the only exit.
- Higher gates hurt the caster both *while open* (drain) and *after* (weakened/defenceless).
- Dojutsu should not be active while gates are active (canon).
- The 8th gate grants power far beyond the Five Kage but kills the user.

---

## 2. Core loop (already in code — kept)

- **Combo:** `V B B B` (`332L`). Tap opens the next gate; hold ≥ 10 ticks seals all (1–7). 8th cannot be sealed.
- **Cost to open one gate:** 25 chakra (mastery-scaled) + 15 stamina. Sealing is free.
- **Drain:** every 20 ticks, direct HP loss `0.00833 × gates²` (bypasses armour, resistance, absorption, creative). Remainder carried in `gateBacklashCarry`.
- **Death:** at 8th gate the drain kills via `NarutoDamageTypes.GATE_BACKLASH`; `close()` resets on death/clone/disconnect.

Drain curve (full 20 HP bar):

| Gates | HP/s | Time to die |
|-------|------|-------------|
| 1 | 0.0083 | ~40 min |
| 2 | 0.0333 | 10 min |
| 3 | 0.0750 | 4 min 27 s |
| 4 | 0.1333 | 2 min 30 s |
| 5 | 0.2083 | 1 min 36 s |
| 6 | 0.3000 | 66.7 s |
| 7 | 0.4083 | 49.0 s |
| 8 | 0.5333 | 37.5 s |

This curve is good and stays. **Do not change** — it is the spine of the risk/reward.

---

## 3. Per-gate progression (buffs)

Buff refresh: `maintain()` re-applies every tick with a 40-tick duration, ambient + invisible, so nothing else looks like a gate buff.

### 3.1 Effect table (current → proposed)

Amplifiers are 0-based (amp 0 = level I). `g` = open gate count.

| Gate | Speed amp | Strength amp | Resistance amp | Jump amp | New additions |
|------|-----------|--------------|----------------|----------|---------------|
| 1 | 0 | 0 | 0 | 0 | (baseline unlock; no stat change beyond "100 % of muscles") |
| 2 | 1 | 0 | 0 | 0 | On open: restore 25 % of missing stamina (canon re-energise) |
| 3 | 2 | 1 | 0 | 1 | Fall damage immunity while open; red dust aura |
| 4 | 3 | 1 | 0 | 2 | +0.15 knockback resistance; opening deals 1 HP self (muscle tear) |
| 5 | 4 | 2 | 1 | 3 | +0.30 knockback resistance; Strength amp bump |
| 6 | 5 | 2 | 1 | 3 | Green aura particles; water-displacement burst on open |
| 7 | 6 | 3 | 2 | 3 | Blue sweat aura; any incoming hit +1 HP extra (pain feedback) |
| 8 | 7 | 3 | 2 | 3 | Red steam aura; +0.50 knockback resistance; disables other jutsu |

Notes on the deltas vs. current code:

- Current code: `Speed = g-1`, `Strength = (g-1)/2`, `Resistance = (g-1)/3`, `Jump = min(g-1,3)`. The proposed table **keeps the same formulas** for 1–8 (they already produce the amps above) and only adds the "New additions" column. No need to rewrite the effect ladder — the *feel* gap is in impact, particles and drawbacks, not in the vanilla effect numbers.
- Resistance stays capped at amp 2 (gates 7–8) on purpose: the last gates are about **hitting**, not becoming untouchable. This is a deliberate tradeoff vs. tailed-beast cloaks, which scale defence harder.

### 3.2 Dojutsu mutual exclusion

When a gate opens, if the caster has Sharingan/Byakugan/Jōgan active, deactivate it (set the toggle off, remove effects). Canon: dojutsu cannot be active while gates are. Cost: a dojutsu user loses their eye perks for the duration. Tradeoff: gates are a *taijutsu* commitment.

### 3.3 Regen suppression while open

While `gateCount > 0`:

- Chakra regen: disabled (canon: chakra cannot be replenished past the 3rd gate; we disable all for simplicity and to force the cost).
- Stamina regen: disabled (canon: stamina cannot be replenished while gates are open).
- Natural health regen from food/saturation: disabled (vanilla regen would out-heal the drain at low gates and trivialise them). Implement via `LivingHealEvent` cancel when `gateCount > 0`.

This is the single biggest balance lever: it turns the early gates from "free speed" into a real budget.

---

## 4. Knockback / impact scaling per gate

Currently `onHit()` fires for *any* open gate but uses fixed values (push 2.2, wall damage 50 % of current health). This makes gate 1 and gate 8 feel identical on hit. Proposed: scale everything by `g`.

### 4.1 Impact table

| Gate | Horizontal push | Vertical lift | Particle count | Sound |
|------|-----------------|---------------|----------------|-------|
| 1 | 1.0 | 0.35 | 8 | generic hit 0.7 |
| 2 | 1.2 | 0.40 | 10 | generic hit 0.75 |
| 3 | 1.4 | 0.45 | 12 | generic hit 0.8 |
| 4 | 1.65 | 0.50 | 15 | explode 0.8 |
| 5 | 1.9 | 0.55 | 18 | explode 0.85 |
| 6 | 2.2 | 0.60 | 22 | explode 0.9 |
| 7 | 2.5 | 0.65 | 26 | explode 0.95 |
| 8 | 2.9 | 0.75 | 32 | explode 1.0 + ravager roar |

Formula (for implementation): `push = 0.75 + 0.25*g + (g>=6 ? 0.15*(g-5) : 0)`; `lift = 0.30 + 0.05*g + (g>=7 ? 0.05 : 0)`. Values above are the rounded targets; use the table as the source of truth, not the formula, so designers can tune without re-deriving.

### 4.2 Wall-slam bonus

When the thrown body meets a block within `WALL_CHECK` distance (scaled 1.0 → 1.35 by gate), deal bonus damage:

| Gate | Wall check dist | Bonus damage (flat) | Cap |
|------|-----------------|---------------------|-----|
| 1 | 1.00 | 2 | min(flat, 20 % max HP) |
| 2 | 1.05 | 3 | min(flat, 22 %) |
| 3 | 1.10 | 4 | min(flat, 24 %) |
| 4 | 1.15 | 5 | min(flat, 26 %) |
| 5 | 1.20 | 6 | min(flat, 28 %) |
| 6 | 1.25 | 8 | min(flat, 30 %) |
| 7 | 1.30 | 10 | min(flat, 32 %) |
| 8 | 1.35 | 14 | min(flat, 35 %) |

- Damage type: `magic()` (current). Consider switching to a dedicated `NarutoDamageTypes.GATE_IMPACT` for kill messages and resistances. Tradeoff: more lang/JSON files; benefit: clearer death messages.
- Cap at % of **max** health (not current) so a low-HP target is not deleted by the wall bonus on top of the melee hit, but a healthy target still feels the slam.
- Current code uses 50 % of *current* health — too lethal and scales with damage already dealt. Replace.

### 4.3 Stacking with tailed-beast cloaks

`TailedBeastEvents` currently calls `PerfectCloakAbility.onHit` / `TailedCloakAbility.onHit` **and** `EightGatesAbility.onHit` when `gateCount > 0`. That double-pushes. Proposed:

- If a cloak is active, compute both knockback vectors and take the **longer** one (do not sum).
- Particles/sounds from both still fire (they are distinct visual identities), but only one push.
- Rationale: stacking two knockbacks into a 5-block launch is funny but makes gates + cloak an automatic combo that invalidates both. Taking the max keeps each system meaningful.

---

## 5. Visual feedback spec

### 5.1 Gate counter overlay (existing, `GateCounterOverlay`)

Keep the Roman-numeral line, but colour by tier:

| Gates | Colour | Meaning |
|-------|--------|---------|
| 1–3 | white `0xFFF2F2F2` | sustainable |
| 4–5 | gold `0xFFFFAA00` | risky |
| 6–7 | orange `0xFFFF6600` | committed |
| 8 | red, blinking (toggle every 10 ticks) | countdown to death |

### 5.2 Particles

In `maintain()`, every 40 ticks, spawn a small burst (count `4 + g`) of coloured dust at the player's feet, radius 0.6, only if `g >= 3` (gates 1–2 are invisible by canon — only the user notices). Colour by gate:

| Gate tier | Particle | Colour |
|-----------|----------|--------|
| 3 | `DUST` | red (skin turns red) |
| 4–5 | `DUST` | dark red |
| 6 | `HAPPY_VILLAGER` (green) + `DUST` green | green aura |
| 7 | `BUBBLE`/`SPLASH` blue + `DUST` blue | blue sweat |
| 8 | `FLAME` + `SMOKE` (large) + `DUST` red | red steam |

Cost: one burst per 2 s per gated player. Acceptable on a small server; if needed, gate the 8th-gate steam to every 10 ticks instead of every tick (maintain is already per-tick for effects; particles are the only per-tick concern).

### 5.3 On open / on seal

- **Open:** flame burst `10 + 6*g`, ravager roar pitch `0.6 + 0.05*g`, plus a tier-coloured dust ring. At g=8 also play `GENERIC_EXPLODE` at volume 1.0.
- **Seal:** cloud burst `20 + 4*g`, fire extinguish. At g≥6 add a short "exhale" sound (`PLAYER_BREATH`).
- **8th gate open:** one-time screen shake is not available client-side cheaply; instead spawn a shockwave ring of `SWEEP_ATTACK` particles and a chat title (not action bar) reading the gate number in red. The action bar already shows "Gate VIII opens".

### 5.4 Per-gate chat flavour (optional, low priority)

Current message: "Gate %s opens". Optional upgrade: per-gate subtitle in lang (`jutsu.eight_gates.open.3` = "The Gate of Life opens — skin turns red"). Adds 8 lang entries × 2 languages. Nice-to-have, not required for feel; the particles and counter do the work.

### 5.5 Health-bar feedback

The drain already plays hurt flash, grunt and `DAMAGE_INDICATOR` particles manually (because it bypasses the damage pipeline). Keep. Add: at g≥7, also play a heartbeat sound to the caster only every 20 ticks (subtle, volume 0.3). Reinforces the countdown without spamming chat.

---

## 6. Death and recovery mechanics

### 6.1 8th gate death

- Drain kills via `GATE_BACKLASH` damage source; `player.die()` is called by hand so nothing cancels it.
- On death, `close()` runs (via `PlayerEvents#onPlayerClone` / death handling): `gateCount = 0`, carry = 0, effects removed. Gates do not persist into the next life. Canon: Guy survived only via Naruto's Six Paths chakra; normal players die.
- No item loss change: vanilla keep-inventory rules apply. Mod does not alter.

### 6.2 Fatigue after sealing (1–7)

When `seal()` runs for g ≤ 7, apply a fatigue debuff so high-gate use leaves the caster defenceless (canon: "severely weakened and very defenceless to a counter attack"):

| Sealed at gate | Effect | Duration |
|----------------|--------|----------|
| 1–2 | none | — |
| 3 | Weakness I | 10 s |
| 4 | Weakness I | 15 s |
| 5 | Weakness II | 15 s |
| 6 | Weakness II | 20 s |
| 7 | Weakness II + Slowness I | 30 s |

- Fatigue is applied server-side via `MobEffectInstance`; client sees it normally.
- During fatigue, the caster cannot reopen gates (cooldown `20 + 10*g` seconds after sealing). Implement as a `gateCooldownUntil` long in `NinjaData` (server-only, not synced; client just sees the failure message).

### 6.3 8th-gate survival (optional, off by default)

Canon Guy survived with permanent leg damage. A faithful port could apply, on respawn after an 8th-gate death, a 5-minute "Shattered Body" debuff: −2 max health (via attribute modifier) + Slowness I. **Recommendation: ship disabled by default** (`NarutoConfig` toggle) and document it. Reason: in SMP, a single mistake deleting 2 HP for 5 minutes breeds resentment; the death itself is already the cost. Leave the hook in code behind a config flag.

### 6.4 Chakra/stamina floor

Opening a gate requires 25 chakra + 15 stamina *per gate*. With regen suppressed while open, a caster who opens 5 gates and fights for a minute is dry. This is intended: gates are a budget, not a toggle.

---

## 7. Tradeoffs and balance

### 7.1 vs. other endgame modes

| Mode | Defence | Offence | Mobility | Cost | Reversibility |
|------|---------|---------|----------|------|---------------|
| Sage mode | medium | medium | medium | senjutsu pool | reversible |
| Tailed cloak | high | high | medium | chakra | reversible |
| **Eight Gates (1–7)** | low–medium | medium–high | high | HP drain + fatigue | reversible but costly |
| **Eight Gates (8)** | low | extreme | extreme | life | none |

Gates win on mobility and burst impact; lose on defence and sustainability. The wall-slam bonus is the differentiator: gates are the only mode that physically throws targets, which matters in PvP (environmental kills, fall damage) and PvE (knocking mobs off cliffs).

### 7.2 Gate 4 self-damage

Opening the 4th gate deals 1 HP to the caster (muscle tear). This prevents "open to 4 for free resistance" and forces a real decision at the mid tier. Tradeoff: low-skill players may feel punished; mitigate with a clear chat message and the overlay turning gold.

### 7.3 Gate 7 incoming-hit penalty

At gate 7, every hit the caster *takes* deals +1 HP extra (muscle fibres ripped; "intense pain if touched"). This makes gate 7 a glass-cannon tier: you hit like a truck but flinch harder. Tradeoff: could feel unfair in PvE swarms; cap the extra damage at 1 HP per hit (not per damage point) so a creeper explosion does not stack.

### 7.4 Regen suppression

Disabling food regen while gated is the biggest power reduction. Without it, a player with golden apples out-heals gate 1–3 indefinitely. With it, even gate 2 has a clock. Tradeoff: players must eat *before* opening, which adds prep time — acceptable, and canon-adjacent (Lee eats soldier pills).

### 7.5 Dojutsu exclusion

Costs dojutsu users their eye. Benefit: prevents gates from being a pure stat layer on top of Sharingan's already-strong kit. Tradeoff: a Sharingan + gates combo (canon Kakashi/Guy? no) is blocked; some players will want it. Keep the exclusion but make it a config toggle for servers that want chaos.

---

## 8. Implementation notes (for the developer)

Files to touch:

1. `EightGatesAbility.onHit(Player, LivingEntity)` — read `g` from `ninjaData.getGateCount()`, scale push/lift/particles/sound/wall damage per §4.1–4.2. Change signature to `onHit(Player, LivingEntity, int gates)` or read internally (preferred, keeps call site stable).
2. `TailedBeastEvents` (line ~289) — pass gate count through; implement max-of-two knockback (§4.3).
3. `EightGatesAbility.seal()` — apply fatigue effects (§6.2) and set `gateCooldownUntil`.
4. `EightGatesAbility.performServer()` open path — gate-4 self-damage; gate-2 stamina restore; dojutsu deactivation; water-displacement burst at g=6.
5. `NinjaData` / `INinjaData` — add `gateCooldownUntil` (long, server-only) + `gateFatigue`? Fatigue is just effects; cooldown needs storage. Optional: `isGatesOpen()` helper.
6. `PlayerEvents` — regen suppression hooks (`LivingHealEvent` cancel if `gateCount > 0`); chakra/stamina regen skip; gate-7 incoming-hit +1 HP in `LivingHurtEvent`.
7. `NarutoConfig` — toggles: `gateDojutsuExclusion`, `gateRegenSuppression`, `gateSurvivalDebuff`, `gateFatigueEnabled`.
8. `GateCounterOverlay` — colour tiers + blink at 8 (§5.1).
9. `maintain()` — particle bursts + heartbeat sound at g≥7 (§5.2, §5.5).
10. New damage type JSON `gate_impact` if switching wall-slam damage type (§4.2) — plus lang key.

Sync/performance:

- `gateCount` stays server-only (client infers via effects, as today). New `gateCooldownUntil` is server-only too.
- Particles: keep bursts on open/seal/hit; the only per-tick addition is a small dust burst every 40 ticks at g≥3. Budget ~10 particles/player/2s. Fine.
- Do not spawn particles in `maintain()` every tick — only every 40 ticks, guarded by `player.tickCount % 40 == 0`.

Edge cases:

- Player in creative: drain still bypasses creative (current behaviour, keep — gates are irreversible).
- Death while gated: ensure `close()` runs and `gateCooldownUntil` resets.
- Clone (`ShadowCloneEntity`): gates do not copy (gateCount not synced/persisted); clones never have gates. Good, keep.
- Disconnect/reconnect: gateCount resets (not persisted), so player logs back ungated. Keep.

---

## 9. Open questions for the maintainer

1. Should gate 8's on-hit push (2.9) also apply to the caster (recoil)? Canon Guy is stationary when punching; recoil would look odd. Recommend **no**.
2. Should the wall-slam bonus damage the caster if *they* hit a wall (e.g., thrown back by a mob)? No — only targets.
3. Fatigue cooldown: is 30 s at gate 7 too long for SMP? Suggest playtesting; make it a config value.
4. Keep the 50 %→35 % wall-damage nerf, or keep 50 % for gate 8 only? Recommend 35 % cap for all gates for consistency; gate 8's 14 flat + 35 % cap still chunks.

---

## 10. Summary of concrete numbers (quick reference)

- Drain: `0.00833 × g²` HP/s (unchanged).
- Impact push: 1.0 → 2.9 across gates; lift: 0.35 → 0.75.
- Wall check: 1.00 → 1.35; wall bonus: 2 → 14 flat, capped 20 % → 35 % max HP.
- Fatigue after seal: none (1–2), Weakness I 10–15 s (3–4), Weakness II 15–20 s (5–6), Weakness II + Slowness I 30 s (7).
- Reopen cooldown after seal: `20 + 10*g` s.
- Gate 4 open: −1 HP self. Gate 7: incoming hits +1 HP. Gate 2 open: +25 % missing stamina.
- Particles: dust every 40 ticks at g≥3; colour by tier.
- Counter colour: white (1–3), gold (4–5), orange (6–7), blinking red (8).
