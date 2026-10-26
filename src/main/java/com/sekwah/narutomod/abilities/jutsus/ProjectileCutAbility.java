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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.Vec3;

/**
 * A scythe of wind drawn across the space in front of the caster.
 *
 * <p>It is the answer to being shot at from close range: arrows, fireballs and thrown things caught
 * in the arc are cut apart where they hang, and bodies caught in it are opened a little without
 * being pushed anywhere. That last part is the point - a technique that scattered everything would
 * put the archer back at a comfortable distance, and this one is meant to leave them standing inside
 * the caster's reach with nothing left to shoot.</p>
 *
 * <p>It is silent enough to be lost in a fight and cheap enough to be thrown between steps, but it
 * does not stop a blade, only what flies.</p>
 */
public class ProjectileCutAbility extends Ability implements Ability.Cooldown {

    /** A cut through the air travels no further than a raised voice. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** B C B B A D - the taijutsu branch's cutting seal. */
    public static final long COMBO = 132123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "projectile_cut");

    /** How far the scythe reaches. */
    private static final double RANGE = 6.0d;

    /** Stamina paid, and the wait before the arm can cut again. */
    private static final float COST = 18.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 120;

    /** Half of the hundred-and-twenty degree arc, as a dot product: cos(60 degrees). */
    private static final double ARC_DOT = 0.5d;

    /** What a body caught in the cut takes. It is a cut, not a blow, so nothing is pushed. */
    private static final float DAMAGE = 3.0f;

    /** How the arc is drawn: this many motes, spread across the full hundred and twenty degrees. */
    private static final int ARC_MOTES = 9;
    private static final double ARC_DEGREES = 120.0d;

    /** How far out along the arc the motes are drawn, as a fraction of the reach. */
    private static final double ARC_REACH_FRACTION = 0.6d;

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
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);

        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            Vec3 to = centreOf(entity).subtract(eye);
            if (to.lengthSqr() > RANGE * RANGE || to.lengthSqr() < 1.0E-4d) {
                continue;
            }
            if (to.normalize().dot(look) < ARC_DOT) {
                continue;
            }
            if (isCuttable(entity)) {
                level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(), entity.getZ(),
                        6, 0.15d, 0.15d, 0.15d, 0.05d);
                entity.discard();
                continue;
            }
            if (entity instanceof LivingEntity living && canHit(player, living)) {
                cutBody(player, living);
            }
        }

        drawArc(level, player, eye, look);
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /**
     * Cuts a body without moving it. The game's own damage path applies knockback as part of the hit,
     * so the momentum is taken before the blow and put back afterwards; the hit is still marked for
     * sync, so the client ends up with the body where the server left it rather than where the
     * knockback would have thrown it.
     */
    private static void cutBody(Player caster, LivingEntity target) {
        Vec3 momentum = target.getDeltaMovement();
        target.hurt(caster.damageSources().playerAttack(caster), DAMAGE);
        target.setDeltaMovement(momentum);
    }

    /** Whether the thing is one of the flying kinds the scythe is meant to answer. */
    private static boolean isCuttable(Entity entity) {
        return entity instanceof AbstractArrow
                || entity instanceof AbstractHurtingProjectile
                || entity instanceof ThrowableItemProjectile;
    }

    /** A fan of sweep motes across the arc, so the space the cut took is visible. */
    private static void drawArc(ServerLevel level, Player player, Vec3 eye, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        if (flat.lengthSqr() < 1.0E-4d) {
            return;
        }
        flat = flat.normalize();
        for (int i = 0; i < ARC_MOTES; i++) {
            double share = ARC_MOTES == 1 ? 0.5d : i / (double) (ARC_MOTES - 1);
            double angle = Math.toRadians((share - 0.5d) * ARC_DEGREES);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 direction = new Vec3(flat.x * cos - flat.z * sin, 0.0d, flat.x * sin + flat.z * cos);
            Vec3 at = eye.add(direction.scale(RANGE * ARC_REACH_FRACTION)).add(0.0d, -0.4d, 0.0d);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
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

    private static Vec3 centreOf(Entity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }
}
