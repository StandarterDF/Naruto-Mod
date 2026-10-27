package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
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

/**
 * The eight inner gates, opened one at a time and sealed again with the same bind.
 *
 * <p>Every tap opens the next gate, and by the time all eight are open the caster is fast, strong
 * and hard to hurt in a way nothing else in the mod can match. Keeping the last key of the combo
 * held instead seals every gate the caster still can, which is why the two halves share one
 * technique rather than two combos to remember: the bind only ever does the thing the caster means
 * by how long they press it.</p>
 *
 * <p>The catch is that the body is not built for it: the gates grind the caster down the whole time
 * they are open, and the drain grows with the square of how many are open, so two gates are
 * something a caster can keep up for ten minutes while the last two are a countdown.</p>
 *
 * <p><b>The eighth gate cannot be sealed.</b> Up to the seventh the caster can still close them with
 * a hold, but once the last gate is open no hold shuts it and eight gates drain a full health bar in
 * a little over half a minute. Death is the only way out, which is the point: the eighth gate is
 * meant to be spent, not used. The gates fall away with death and are gone when the caster comes
 * back.</p>
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
    public static final long COMBO = 332L;

    public static final int MAX_GATES = 8;

    /** Chakra and stamina each gate costs to force open. Sealing is free. */
    private static final float CHAKRA_PER_GATE = 25.0f;
    private static final float STAMINA_PER_GATE = 15.0f;

    /** How often, in ticks, the open gates take their toll on the caster. */
    private static final int DRAIN_INTERVAL = 20;

    /**
     * How long the last key has to stay down, counted from the moment the channel starts, before the
     * hold is read as "seal" rather than "open". The client only starts the channel after its own
     * hold threshold, so this is the extra bit that separates a quick tap from a deliberate hold.
     */
    private static final int RELEASE_HOLD_TICKS = 10;

    /**
     * Health lost each tick of drain, multiplied by the square of the open gates.
     *
     * <p>Anchored on two numbers rather than picked out of the air: the second gate costs a tenth of
     * a heart a second, so a full health bar lasts ten minutes, and the eighth costs a bit over half
     * a heart a second, which is a little over half a minute to live. Everything between follows
     * from the square, so the early gates are a slow tax and the last two are a countdown.</p>
     *
     * <p>Fractions are not thrown away, they are carried: the caster is charged whole points of
     * health so that the health bar actually moves, and the remainder waits for the next tick.</p>
     */
    private static final float DRAIN_COEFFICIENT = 0.00833f;

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
            if (ninjaData.getGateCount() >= MAX_GATES) {
                fail(player, "jutsu.fail.gates_open");
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

        if (ninjaData.getGateCount() >= MAX_GATES) {
            fail(player, "jutsu.fail.gates_open");
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
        // Gate of Pain: muscle tissue tears on opening.
        if (gates == 4) {
            player.hurt(player.damageSources().magic(), 1.0f);
        }
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
                Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.RED), true);
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
        int gates = Math.max(1, player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .resolve().map(com.sekwah.narutomod.capabilities.INinjaData::getGateCount).orElse(1));
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
        // Speed and strength rise with every gate; the defensive ones only every other gate, so the
        // last few gates are about hitting harder rather than becoming untouchable.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, shortDuration, gates - 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, shortDuration, (gates - 1) / 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, shortDuration, (gates - 1) / 3, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, shortDuration, Math.min(gates - 1, 3), true, false));

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

        if (player.tickCount % DRAIN_INTERVAL == 0) {
            // Charged in whole points of health, because the health bar is drawn in half hearts and a
            // drain that always arrived as a fraction of one would never visibly move it. The unpaid
            // remainder is carried into the next tick, so the long run cost is unchanged.
            float owed = ninjaData.getGateBacklashCarry() + DRAIN_COEFFICIENT * gates * gates;
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
     * own resistance buff and even creative mode all leave it alone, which is the whole point of an
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
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
    }
}
