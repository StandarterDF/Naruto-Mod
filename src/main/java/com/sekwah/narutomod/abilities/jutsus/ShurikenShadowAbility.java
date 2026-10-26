package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shadow shuriken: one thrown blade becomes two.
 *
 * <p>The caster never lets go of anything - the copies are chakra shaped like a shuriken, thrown
 * along the same line their hand would have taken. Each one lands for a little less than a real
 * blade, so the technique is not a way to throw twice as hard; it is a way to make a single opening
 * count, because a target cannot see which of the two is worth blocking.</p>
 *
 * <p>The copies follow the crosshair rather than the physical world: a target the caster can see is
 * hit twice, and an empty line of air gets two lines of sparks so it is plain where the blades went.
 * The cost is deliberately small and the cooldown short - this is the cheap tool of the throwing
 * branch, not a finishing move.</p>
 */
public class ShurikenShadowAbility extends Ability implements Ability.Cooldown {

    /** B A A V - the throwing branch's blend of the clone and weapon seals. */
    public static final long COMBO = 2112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "shuriken_shadow");

    /** As far as a thrown blade carries, so the technique never outranges the real thing. */
    private static final double RANGE = 20.0d;

    /**
     * What a thrown shuriken deals, see {@code ShurikenItem}. Kept here rather than taken from the
     * item so the two copies can be tuned without the projectile following them.
     */
    private static final double SHURIKEN_DAMAGE = 2.5d;

    /** A shadow blade is thinner than the steel it copies, hence a fraction of the damage. */
    private static final double DAMAGE_FRACTION = 0.6d;

    /** One blade in the hand, two in the air. */
    private static final int COPIES = 2;

    private static final float COST = 12.0f;
    private static final int COOLDOWN = 80;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far and how finely the empty-air sparks are laid out, in blocks per point. */
    private static final double LINE_STEP = 0.9d;
    private static final int LINE_STEPS = 12;

    @Override
    public int noiseRadius() {
        return 16;
    }

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
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        level.playSound(null, player, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9f, 1.2f);

        Entity looked = Targeting.lookedAtEntity(player, RANGE);
        if (looked instanceof LivingEntity target && isValidTarget(player, target)) {
            strike(level, player, target);
        } else {
            traceBlades(level, player);
        }
    }

    /** Both copies land, one after the other on the same body. */
    private static void strike(ServerLevel level, Player player, LivingEntity target) {
        float damage = (float) (SHURIKEN_DAMAGE * DAMAGE_FRACTION);
        for (int i = 0; i < COPIES; i++) {
            target.hurt(player.damageSources().thrown(player, player), damage);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6d), target.getZ(),
                    10, 0.3d, 0.3d, 0.3d, 0.05d);
        }
    }

    /**
     * Nothing to hit: the blade lines are drawn so the caster can see where the throw went, which is
     * also the only feedback a miss gets.
     */
    private static void traceBlades(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);

        // A horizontal side vector to split the two lines with; a straight-down look has no
        // horizontal facing, so that case falls back to a fixed axis rather than a zero vector.
        Vec3 forward = new Vec3(look.x, 0.0d, look.z);
        forward = forward.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0d, forward.x);

        for (int copy = 0; copy < COPIES; copy++) {
            double offset = copy == 0 ? 0.25d : -0.25d;
            Vec3 shift = side.scale(offset);
            for (int step = 1; step <= LINE_STEPS; step++) {
                Vec3 point = eye.add(shift).add(look.scale(step * LINE_STEP));
                level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
    }

    /** A technique, not a betrayal: the caster and their friends are never the target. */
    private static boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
