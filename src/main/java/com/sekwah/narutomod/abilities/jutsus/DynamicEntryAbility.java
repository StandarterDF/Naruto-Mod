package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The flying kick a taijutsu specialist opens a fight with: a low leap and a boot through the door.
 *
 * <p>The body is thrown forward and slightly up, and what the leap is aimed at is the first thing
 * standing where it comes down. A raised shield does not stop it - the guard is knocked aside before
 * the boot lands, which is the difference between a kick and a shove - and the caster is braced for
 * the whole flight so the exchange that follows does not catch them mid-air.</p>
 *
 * <p>The landing is watched for a second after the leap, not longer: a kick that never comes down is
 * just a jump, and the guard is there to carry the caster home.</p>
 */
public class DynamicEntryAbility extends Ability implements Ability.Cooldown {

    /** A body arriving in a cloud of dust is heard a long way off. */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** B C B B A C - the taijutsu branch's leaping seal. */
    public static final long COMBO = 132122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "dynamic_entry");

    /** Stamina paid, and the wait before the kick can be thrown again. */
    private static final float COST = 30.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 280;

    /** Forward speed and lift of the leap: a shade under five blocks of flight. */
    private static final double LAUNCH_FORWARD = 0.65d;
    private static final double LAUNCH_LIFT = 0.25d;

    /** What the boot deals to whatever it finds on landing, and how far it reaches for it. */
    private static final float DAMAGE = 6.0f;
    private static final double LANDING_REACH = 2.0d;

    /** Braced from the moment of the leap through the landing. */
    private static final int GUARD_TICKS = 60;
    private static final int GUARD_AMPLIFIER = 1;

    /** How long a shield stays down once the kick has gone through it. */
    private static final int SHIELD_COOLDOWN = 100;

    /**
     * How long the landing is watched for, in ticks. Every tick in the window is scheduled up front
     * because the capability's delayed-event list cannot be extended from inside a delayed event.
     */
    private static final int LANDING_WINDOW = 20;

    /**
     * The first few ticks are part of the leap, not the landing. The server still believes the caster
     * is on the ground until the client reports the jump, and resolving the kick then would hit
     * whatever was standing where the leap started.
     */
    private static final int MIN_FLIGHT_TICKS = 3;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = horizontalLook(player);
        player.setDeltaMovement(forward.x * LAUNCH_FORWARD, LAUNCH_LIFT, forward.z * LAUNCH_FORWARD);
        // The leap is deliberate travel: the drop at the end of it is not a fall.
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, GUARD_TICKS,
                GUARD_AMPLIFIER, false, false));

        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2d, player.getZ(),
                18, 0.35d, 0.1d, 0.35d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3f, 1.6f);
        watchLanding(player, ninjaData);
    }

    /**
     * Watches for the touchdown and resolves the kick once. The shared flag is what keeps a long
     * flight from scoring a hit on every tick of the window, and what stops the checks at the end of
     * the window from landing on somebody the caster only walked past.
     */
    private void watchLanding(Player player, INinjaData ninjaData) {
        boolean[] resolved = {false};
        for (int tick = 1; tick <= LANDING_WINDOW; tick++) {
            int delay = tick;
            ninjaData.scheduleDelayedTickEvent(next -> {
                if (resolved[0] || next.isRemoved() || !next.isAlive()) {
                    return;
                }
                if (delay < MIN_FLIGHT_TICKS) {
                    return;
                }
                if (!next.onGround() && delay < LANDING_WINDOW) {
                    return;
                }
                resolved[0] = true;
                impact(next);
            }, delay);
        }
    }

    /** The boot, once the caster is back on the ground. */
    private static void impact(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = nearestTarget(level, player);
        if (target == null) {
            return;
        }
        breakGuard(target);
        target.hurt(player.damageSources().playerAttack(player), DAMAGE);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.6d), target.getZ(),
                16, 0.4d, 0.2d, 0.4d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    /**
     * Knocks a raised shield aside before the boot lands. Setting the cooldown alone would leave the
     * shield still up for this blow and the kick would be absorbed by the guard it was meant to
     * break, so the block is stopped as well.
     */
    private static void breakGuard(LivingEntity target) {
        if (target instanceof Player other && other.isBlocking()) {
            other.getCooldowns().addCooldown(Items.SHIELD, SHIELD_COOLDOWN);
            other.stopUsingItem();
        }
    }

    /** The closest body the kick can land on, or null if the landing was on empty ground. */
    private static LivingEntity nearestTarget(ServerLevel level, Player caster) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(LANDING_REACH), entity -> canHit(caster, entity))) {
            double distance = candidate.distanceToSqr(caster);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canHit(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** The look vector flattened onto the ground, so a glance at the sky still leaps forward. */
    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        return flat.lengthSqr() < 1.0E-4d ? Vec3.ZERO : flat.normalize();
    }
}
