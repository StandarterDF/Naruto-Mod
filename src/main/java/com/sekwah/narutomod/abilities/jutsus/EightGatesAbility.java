package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * The eight inner gates, opened one at a time and sealed again with the same bind.
 *
 * <p>Every tap opens the next gate, adding speed, damage reduction and a melee damage bonus that scales with
 * mastery and the number of open gates (7 gates on full mastery give +24 attack damage, enough to
 * one-shot a zombie or skeleton). Keeping the last key of the combo held instead seals every gate the
 * caster still can, which is why the two halves share one technique rather than two combos to remember:
 * the bind only ever does the thing the caster means by how long they press it.</p>
 *
 * <p>The catch is that the body is not built for it: the gates drain health while open, from nothing
 * on gates 1-2 up to 0.90 HP/s on the eighth. The drain is reduced by mastery on gates 3-7 and is
 * fixed and lethal on gate 8 (~22 s from a full health bar). Healing is allowed on gates 1-5, forbidden
 * on gates 6-8. The eighth gate is slightly weaker overall than Six Paths Mode (no flight, no regen,
 * no spheres) but carries a very high physical damage bonus and a deadly timer.</p>
 *
 * <p><b>The eighth gate cannot be sealed.</b> Up to the seventh the caster can still close them with
 * a hold, but once the last gate is open no hold shuts it and death is the only way out, which is the
 * point: the eighth gate is meant to be spent, not used. The gates fall away with death and are gone
 * when the caster comes back.</p>
 *
 * <p>The effects are kept alive from {@code PlayerEvents}, not here, since they have to be refreshed
 * every tick while the gates are open.</p>
 */
public class EightGatesAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. A blast, heard a long way off.
     */
    @Override
    public int noiseRadius() {
        return 48;
    }


    /** V B B B - tap to open the next gate, hold the last key to seal them again. */
    public static final long COMBO = 1233L;

    public static final int MAX_GATES = 8;

    /** Chakra and stamina each gate costs to force open. Sealing is free. */
    private static final float CHAKRA_PER_GATE = 25.0f;
    private static final float STAMINA_PER_GATE = 15.0f;

    /** Stamina every landed blow costs on top of the opening price, growing with the open gates. */
    private static final float STAMINA_PER_HIT_BASE = 1.0f;
    private static final float STAMINA_PER_HIT_PER_GATE = 0.5f;

    /** How often, in ticks, the open gates take their toll on the caster. */
    private static final int DRAIN_INTERVAL = 20;

    /**
     * How long the last key has to stay down, counted from the moment the channel starts, before the
     * hold is read as "seal" rather than "open". The client only starts the channel after its own
     * hold threshold, so this is the extra bit that separates a quick tap from a deliberate hold.
     */
    private static final int RELEASE_HOLD_TICKS = 10;

    /** Fixed UUID/name of the transient attack-damage modifier the open gates apply. */
    public static final UUID GATE_ATTACK_DAMAGE_MODIFIER_UUID =
            UUID.fromString("3a7c1e2f-9b4d-4c5a-8f1e-2b3c4d5e6f7a");
    public static final String GATE_ATTACK_DAMAGE_MODIFIER_NAME = "narutomod:eight_gates";

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The opening and sealing messages are written by hand; the generic channel chatter ("channel
     * started", "channel stopped") would only repeat them, so it is turned off.
     */
    @Override
    public boolean logInChat() {
        return false;
    }

    @Override
    public boolean hideChannelMessages() {
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // A tap is unambiguously an attempt to open the next gate, so it is checked and priced up
        // front. A held key may still turn into a seal, which costs nothing, so the channel itself
        // is allowed through and performServer decides what the hold meant.
        if (chargeAmount == -1) {
            if (ninjaData.getGateCount() >= maxGates(ninjaData)) {
                if (ninjaData.getGateCount() >= MAX_GATES) {
                    fail(player, "jutsu.fail.gates_open");
                } else {
                    fail(player, "jutsu.fail.gates_locked");
                }
                return false;
            }
            return canPay(player, ninjaData);
        }
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (ticksChanneled < RELEASE_HOLD_TICKS) {
            return;
        }
        // Held long enough: seal whatever is open. The channel is ended here rather than waited out
        // so the effects drop the moment the hold is recognised, not when the key finally comes up.
        seal(player, ninjaData);
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // A channel that got at least as far as the release threshold is a seal. Anything shorter -
        // including the -1 a tap arrives with - opens the next gate.
        if (ticksActive >= RELEASE_HOLD_TICKS) {
            seal(player, ninjaData);
            return;
        }

        if (ninjaData.getGateCount() >= maxGates(ninjaData)) {
            if (ninjaData.getGateCount() >= MAX_GATES) {
                fail(player, "jutsu.fail.gates_open");
            } else {
                fail(player, "jutsu.fail.gates_locked");
            }
            return;
        }
        if (!canPay(player, ninjaData)) {
            return;
        }

        float chakraCost = this.masteryCost(ninjaData, CHAKRA_PER_GATE);
        ninjaData.useChakra(chakraCost, 20 * 3);
        ninjaData.useStamina(STAMINA_PER_GATE, 20 * 3);

        int gates = ninjaData.getGateCount() + 1;
        ninjaData.setGateCount(gates);

        // Gate of Healing: the body re-energises, so a quarter of the missing stamina comes back.
        if (gates == 2) {
            ninjaData.addStamina(ninjaData.getMaxStamina() * 0.25f);
        }
        // Gate of Pain: muscle tissue tears on opening - but the one-shot damage on opening is
        // dropped; the ongoing drain carries the price instead.
        // Dojutsu cannot burn alongside the gates: the eye is closed for the duration.
        if (gates == 1) {
            for (ResourceLocation eyeId : new ResourceLocation[] {
                    com.sekwah.narutomod.abilities.NarutoAbilities.SHARINGAN.getId(),
                    com.sekwah.narutomod.abilities.NarutoAbilities.BYAKUGAN.getId(),
                    com.sekwah.narutomod.abilities.NarutoAbilities.JOUGAN.getId(),
                    com.sekwah.narutomod.abilities.NarutoAbilities.RINNEGAN.getId(),
                    com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN.getId()}) {
                Ability eye = NarutoRegistries.ABILITIES.getValue(eyeId);
                if (eye != null && ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(eyeId)) {
                    ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, eye);
                }
            }
        }

        // A burst coloured by how far the caster has pushed it.
        level.sendParticles(ParticleTypes.FLAME, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                10 + gates * 6, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                0.7f, 0.6f + gates * 0.05f);
        player.displayClientMessage(Component.translatable("jutsu.eight_gates.open",
                gateName(gates), Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Shuts every gate the caster can still shut and says why when they cannot. Sealing costs
     * nothing; the only price was opening them in the first place.
     */
    private static void seal(Player player, INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates <= 0) {
            fail(player, "jutsu.fail.gates_none");
            return;
        }
        if (gates >= MAX_GATES) {
            fail(player, "jutsu.fail.gates_open");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        close(player, ninjaData);

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                20 + gates * 4, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
        player.displayClientMessage(Component.translatable("jutsu.eight_gates.close",
                Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY), true);
    }

    private boolean canPay(Player player, INinjaData ninjaData) {
        float chakraCost = this.masteryCost(ninjaData, CHAKRA_PER_GATE);
        if (ninjaData.getChakra() < chakraCost || ninjaData.getStamina() < STAMINA_PER_GATE) {
            fail(player, "jutsu.fail.notenoughchakra");
            return false;
        }
        return true;
    }

    private static void fail(Player player, String key) {
        player.displayClientMessage(Component.translatable(key,
                Component.translatable("narutomod:eight_gates").withStyle(ChatFormatting.YELLOW)), true);
    }

    /**
     * How many gates the caster may have open at once. Grows with mastery of the technique: an
     * untrained caster can only force the first gate, and the eighth opens only once the technique
     * has been lived in. The absolute ceiling stays {@link #MAX_GATES}.
     */
    public static int maxGates(INinjaData ninjaData) {
        float level = Mastery.level(ninjaData, NarutoAbilities.EIGHT_GATES.getId());
        int gates = 1 + (int) (level * (MAX_GATES - 1));
        return Math.min(MAX_GATES, Math.max(1, gates));
    }

    /**
     * The lore name of a gate, for the chat line and the HUD: 1 Opening, 2 Healing, 3 Life,
     * 4 Pain, 5 Limit, 6 View, 7 Wonder, 8 Death.
     */
    public static Component gateName(int gate) {
        int clamped = Math.min(MAX_GATES, Math.max(1, gate));
        return Component.translatable("narutomod:gate." + clamped);
    }

    /** Bonus added to the caster's melee attack damage while the gates are open. */
    public static float meleeBonus(INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates <= 0) {
            return 0.0f;
        }
        float base;
        switch (gates) {
            case 1: base = 2.0f; break;
            case 2: base = 3.0f; break;
            case 3: base = 5.0f; break;
            case 4: base = 7.0f; break;
            case 5: base = 10.0f; break;
            case 6: base = 14.0f; break;
            case 7: base = 24.0f; break;
            default: base = 32.0f; break;
        }
        float mastery = Mastery.level(ninjaData, NarutoAbilities.EIGHT_GATES.getId());
        return base * (0.55f + 0.45f * mastery);
    }

    /** Damage multiplier for the gates' finishing techniques. */
    public static float techniqueScale(INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates < 1) {
            gates = 1;
        }
        float mastery = Mastery.level(ninjaData, NarutoAbilities.EIGHT_GATES.getId());
        return (0.65f + 0.35f * mastery) * (1.0f + 0.10f * (gates - 1));
    }

    /** Health drained per second for the given number of open gates and mastery. */
    private static float drainPerSecond(int gates, float mastery) {
        switch (gates) {
            case 1:
            case 2:
                return 0.0f;
            case 3:
                return 0.05f * (1.0f - mastery);
            case 4:
                return 0.15f * (1.0f - 0.9f * mastery);
            case 5:
                return 0.30f * (1.0f - 0.9f * mastery);
            case 6:
                return 0.55f * (1.0f - 0.7f * mastery);
            case 7:
                return 0.80f * (1.0f - 0.5f * mastery);
            default:
                return 0.90f;
        }
    }

    /**
     * Extra damage multiplier applied to incoming damage while the gates are open, on top of the
     * vanilla {@code DAMAGE_RESISTANCE} effect. Gates 1-5 rely on the effect alone (20/40/60/80%
     * reduction); on gates 6-8 the effect is already capped at 80%, so the remainder is cut further
     * here, for a combined reduction of 90% / 92% / 95% respectively.
     *
     * @param gates number of open gates (0 or less yields no reduction)
     * @return factor to multiply incoming damage by
     */
    public static float extraDamageFactor(int gates) {
        if (gates <= 5) return 1.0f;
        if (gates == 6) return 0.5f;
        if (gates == 7) return 0.4f;
        return 0.25f;
    }

    /** How hard the shockwave throws the struck body, per open gate (push grows with the gate). */
    public static double knockbackFor(int gates) {
        if (gates <= 1) return 1.0d;
        if (gates == 2) return 1.2d;
        if (gates == 3) return 1.4d;
        if (gates == 4) return 1.65d;
        if (gates == 5) return 1.9d;
        if (gates == 6) return 2.2d;
        if (gates == 7) return 2.5d;
        return 2.9d;
    }

    /** Vertical lift of the shockwave, per open gate. */
    public static double liftFor(int gates) {
        if (gates <= 1) return 0.35d;
        if (gates == 2) return 0.40d;
        if (gates == 3) return 0.45d;
        if (gates == 4) return 0.50d;
        if (gates == 5) return 0.55d;
        if (gates == 6) return 0.60d;
        if (gates == 7) return 0.65d;
        return 0.75d;
    }

    /** Particle count of the burst, per open gate. */
    public static int burstParticlesFor(int gates) {
        if (gates <= 1) return 8;
        if (gates == 2) return 10;
        if (gates == 3) return 12;
        if (gates == 4) return 15;
        if (gates == 5) return 18;
        if (gates == 6) return 22;
        if (gates == 7) return 26;
        return 32;
    }

    /** Wall check distance, per open gate. */
    public static double wallCheckFor(int gates) {
        return 1.0d + 0.05d * (gates - 1);
    }

    /** Wall bonus damage (flat), per open gate, capped at a fraction of max health. */
    public static float wallBonusFor(int gates) {
        if (gates <= 1) return 2.0f;
        if (gates == 2) return 3.0f;
        if (gates == 3) return 4.0f;
        if (gates == 4) return 5.0f;
        if (gates == 5) return 6.0f;
        if (gates == 6) return 8.0f;
        if (gates == 7) return 10.0f;
        return 14.0f;
    }

    /** Cap on wall bonus as fraction of max health, per open gate. */
    public static float wallCapFor(int gates) {
        return 0.20f + 0.01875f * (gates - 1);
    }

    /** Every blow the eighth-gate caster lands comes with a burst, so the power reads as one, and the
     *  body is thrown hard enough to leave a crater. The strength scales with the open gate. */
    public static void onHit(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        INinjaData ninjaData = player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .resolve().orElse(null);
        if (ninjaData == null) {
            return;
        }
        int gates = Math.max(1, ninjaData.getGateCount());

        // Every blow is paid for in stamina. Running dry slams the gates shut instead of letting the
        // caster keep hitting for free, and the blow itself is then dropped.
        float cost = STAMINA_PER_HIT_BASE + STAMINA_PER_HIT_PER_GATE * gates;
        if (ninjaData.getStamina() < cost) {
            close(player, ninjaData);
            player.displayClientMessage(Component.translatable("jutsu.eight_gates.out_of_stamina")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ninjaData.useStamina(cost, 20);

        double push = knockbackFor(gates);
        double lift = liftFor(gates);
        int particles = burstParticlesFor(gates);

        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                particles, 0.3d, 0.4d, 0.3d, 0.05d);
        level.sendParticles(ParticleTypes.FLAME, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                particles / 2, 0.2d, 0.3d, 0.2d, 0.02d);
        float volume = 0.5f + 0.05f * gates;
        float pitch = 0.9f + 0.05f * gates;
        SoundEvent sound = gates >= 4 ? SoundEvents.GENERIC_EXPLODE : SoundEvents.PLAYER_ATTACK_SWEEP;
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
        if (gates >= 8) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.0f);
        }

        Vec3 swing = target.position().subtract(player.position());
        if (swing.lengthSqr() < 0.0001d) {
            swing = new Vec3(0.0d, 0.0d, 1.0d);
        }
        Vec3 pushVec = swing.normalize().scale(push);
        target.push(pushVec.x, lift, pushVec.z);
        target.hurtMarked = true;
        extraDamageOnWall(level, target, pushVec, gates);
    }

    /** The rest of the blow, delivered if the thrown body meets a wall on the way out. */
    private static void extraDamageOnWall(ServerLevel level, LivingEntity target, Vec3 push, int gates) {
        double check = wallCheckFor(gates);
        double dx = push.x * check;
        double dz = push.z * check;
        if (level.noCollision(target, target.getBoundingBox().move(dx, 0.0d, dz))) {
            return;
        }
        float cap = wallCapFor(gates) * target.getMaxHealth();
        float damage = Math.min(wallBonusFor(gates), cap);
        if (damage < 1.0f) {
            damage = 1.0f;
        }
        target.hurt(target.damageSources().magic(), damage);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                12, 0.3d, 0.3d, 0.3d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    /**
     * Keeps the effects of however many gates are open running, and takes the health they cost.
     * Called every tick from {@code PlayerEvents} for every player, so it has to be cheap when there
     * is nothing to do.
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates <= 0) {
            return;
        }

        int shortDuration = DRAIN_INTERVAL + 20;
        // Speed rises with every gate; strength is carried by a transient attribute modifier
        // (ATTACK_DAMAGE) applied below, so technique damage is not doubled by it. Damage from
        // outside is soaked through vanilla DAMAGE_RESISTANCE, growing with the open gates:
        // 1 gate = I (20%), 2 = II (40%), 3 = III (60%), 4-5 = IV (80%). On gates 6-8 the effect
        // is already capped at 80%, so an extra multiplier (see extraDamageFactor) cuts the
        // remainder further: 6 = 0.5 (90% total), 7 = 0.4 (92% total), 8 = 0.25 (95% total).
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, shortDuration, gates - 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, shortDuration, Math.min(gates - 1, 3), true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, shortDuration, Math.min(3, gates - 1), true, false));

        // Constant melee bonus for as long as the gates are open: goes only onto melee attacks, never
        // onto technique damage. Applied only when the value actually changes, so the attribute is not
        // dirtied (and re-synced) every tick.
        if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            float bonus = meleeBonus(ninjaData);
            float applied = player.getPersistentData().getFloat("narutomod:eight_gates_attack_bonus");
            if (Math.abs(applied - bonus) > 0.01f) {
                attr.removeModifier(GATE_ATTACK_DAMAGE_MODIFIER_UUID);
                if (bonus > 0.0f) {
                    attr.addTransientModifier(new AttributeModifier(
                            GATE_ATTACK_DAMAGE_MODIFIER_UUID, GATE_ATTACK_DAMAGE_MODIFIER_NAME,
                            bonus, AttributeModifier.Operation.ADDITION));
                }
                player.getPersistentData().putFloat("narutomod:eight_gates_attack_bonus", bonus);
            }
        }

        // Visual progression: a small dust burst every 40 ticks once the skin starts to redden, coloured
        // by the gate tier. Gates 1-2 are invisible by canon - only the user notices.
        if (gates >= 3 && player.tickCount % 40 == 0 && player.level() instanceof ServerLevel level) {
            double r, g, b;
            net.minecraft.core.particles.ParticleOptions chosen;
            if (gates <= 5) {
                if (gates <= 3) { r = 0.85; g = 0.15; b = 0.1; }
                else { r = 0.6; g = 0.05; b = 0.02; }
                chosen = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((float) r, (float) g, (float) b), 1.0f);
            } else if (gates == 6) {
                chosen = ParticleTypes.HAPPY_VILLAGER;
            } else if (gates == 7) {
                chosen = ParticleTypes.BUBBLE;
            } else {
                chosen = ParticleTypes.FLAME;
            }
            level.sendParticles(chosen, player.getX(), player.getY() + 0.1d, player.getZ(),
                    4 + gates, 0.6d, 0.2d, 0.6d, 0.02d);
            if (gates >= 8) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(),
                        player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                        6, 0.4d, 0.6d, 0.4d, 0.02d);
            }
        }

        // Heartbeat on gate 7+: a quiet drum to the caster only.
        if (gates >= 7 && player.tickCount % 20 == 0 && player instanceof ServerPlayer sp) {
            sp.playNotifySound(SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.2f, 0.6f);
        }

        // Fast stamina regeneration while the gates are open.
        if (player.tickCount % 20 == 0) {
            ninjaData.addStamina(2.0f);
        }
        if (player.tickCount % DRAIN_INTERVAL == 0) {
            float mastery = Mastery.level(ninjaData, NarutoAbilities.EIGHT_GATES.getId());
            float rate = drainPerSecond(gates, mastery);
            if (rate <= 0.0f) {
                ninjaData.setGateBacklashCarry(0.0f);
                return;
            }
            // Charged in whole points of health, because the health bar is drawn in half hearts and a
            // drain that always arrived as a fraction of one would never visibly move it. The unpaid
            // remainder is carried into the next tick, so the long run cost is unchanged.
            float owed = ninjaData.getGateBacklashCarry() + rate;
            int points = (int) owed;
            ninjaData.setGateBacklashCarry(owed - points);
            if (points > 0) {
                backlash(player, points);
            }
        }
    }

    /**
     * Takes the health the open gates are owed, in whole points.
     *
     * <p>This is deliberately not damage in the usual sense: it goes straight onto the health bar
     * instead of through the damage pipeline, because the backlash is the caster's own body giving
     * out and nothing may soak it up. Armour, resistance, absorption, a chakra shield, the gates'
     * own damage reduction and even creative mode all leave it alone, which is the whole point of an
     * irreversible technique. The feedback vanilla would have given is replayed by hand, and dying
     * is done by hand too so that no last-second save can cancel it.</p>
     */
    private static void backlash(Player player, int points) {
        float remaining = player.getHealth() - points;
        if (remaining > 0.0f) {
            player.setHealth(remaining);
            if (player.level() instanceof ServerLevel level) {
                // The red flash and the grunt a normal hit would have caused, since this is not one.
                level.broadcastEntityEvent(player, (byte) 2);
                level.playSound(null, player, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5f, 1.0f);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(),
                        player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                        4, 0.3d, 0.3d, 0.3d, 0.0d);
            }
            return;
        }

        player.setHealth(0.0f);
        player.die(NarutoDamageTypes.getDamageSource(player.level(), NarutoDamageTypes.GATE_BACKLASH));
    }

    /**
     * Shuts every gate: no effects, no drain. Called by the hold, and by death.
     */
    public static void close(Player player, INinjaData ninjaData) {
        ninjaData.setGateCount(0);
        ninjaData.setGateBacklashCarry(0.0f);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            player.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(GATE_ATTACK_DAMAGE_MODIFIER_UUID);
        }
        player.getPersistentData().remove("narutomod:eight_gates_attack_bonus");
    }

    /** The ground takes the landing: a ring of force that throws everything nearby and hurts it. */
    public static void landingShockwave(Player player, int gates) {
        if (gates < 5 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = 1.5d + 0.5d * (gates - 4);       // 5 gates -> 2.0, 8 gates -> 3.5
        float damage = 3.0f + 2.0f * (gates - 4);        // 5 gates -> 5, 8 gates -> 11
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (e == player || !e.isAlive() || e.isSpectator() || com.sekwah.narutomod.friends.Friends.isOwnSide(player, e)) {
                continue;
            }
            e.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 away = e.position().subtract(player.position());
            if (away.lengthSqr() < 1.0e-4d) {
                away = new Vec3(0.0d, 0.0d, 1.0d);
            }
            away = away.normalize().scale(0.6d * (gates - 3));
            e.push(away.x, 0.45d, away.z);
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 0.1d, player.getZ(), 1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f + 0.05f * gates, 1.0f);
    }
}
