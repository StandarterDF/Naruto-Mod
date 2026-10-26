package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Palm Lance: the gentle fist thrown as a thrust instead of a strike.
 *
 * <p>A Hyuga who has read the channels still has to get within arm's reach to close them, and
 * everything with a longer weapon knows it. This is the answer: the caster drives a palm down the
 * line they are already looking along, and the body is carried with it, so the gap the enemy was
 * keeping is spent by the technique rather than by walking. The thrust itself is shallow - a palm,
 * not a blade - but it lands hard enough to throw the target back and close the channel it hit, and
 * the caster may keep their eyes on the next one.</p>
 *
 * <p>With nothing to thrust into, the lunge still happens: a miss should cost the chakra and the
 * cooldown, but it should not leave the caster standing exactly where they were, because the point
 * of the technique is the step as much as the palm.</p>
 *
 * <p>The eye has to be open, see {@link ByakuganAbility}; without it the channels cannot be seen and
 * the thrust is an empty hand.</p>
 */
public class PalmLanceAbility extends Ability implements Ability.Cooldown {

    /** A palm into a body is heard about as far as a body being hit. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** V B V V B B - the stance's seal, narrowed to a single thrust. */
    public static final long COMBO = 232232L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "palm_lance");

    /** How far down the look ray the thrust can find a body to follow. */
    private static final double RANGE = 6.0d;

    /** How far off the line of sight a body may stand and still be on the end of the thrust. */
    private static final double RAY_RADIUS = 3.0d;

    /** What the thrust costs and how long the eye needs before it can be made again. */
    private static final float COST = 25.0f;
    private static final int COOLDOWN = 200;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far the palm carries the caster, and the little lift that keeps it a step rather than a slide. */
    private static final double LUNGE = 2.5d;
    private static final double LUNGE_LIFT = 0.05d;

    /** The strike itself: the damage, the shove down the line of the palm, and the closed channel. */
    private static final float DAMAGE = 6.0f;
    private static final double KNOCKBACK = 4.0d;
    private static final double KNOCKBACK_LIFT = 0.1d;
    private static final int SLOW_TICKS = 60;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                    true);
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
        LivingEntity target = findTarget(player);
        // The thrust follows the body if there is one, and the gaze if there is not; either way the
        // step comes first, because the strike is made from where the palm arrives.
        Vec3 thrust = directionTo(player, target);
        lunge(player, thrust);

        if (target == null) {
            return;
        }
        strike(level, player, target, thrust);
    }

    /**
     * The body on the end of the thrust: the nearest living thing that is close enough to the line of
     * sight, and not behind a wall. A palm does not reach through one, so the block the caster is
     * looking at is where the search stops.
     */
    @Nullable
    private static LivingEntity findTarget(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = RANGE;
        BlockHitResult wall = Targeting.lookedAtBlock(player, RANGE);
        if (wall != null) {
            reach = Math.min(RANGE, wall.getLocation().distanceTo(eye));
        }

        LivingEntity nearest = null;
        double nearestAlong = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE), entity -> canStrike(player, entity))) {
            Vec3 toTarget = centreOf(candidate).subtract(eye);
            double along = toTarget.dot(look);
            if (along <= 0.0d || along > reach) {
                continue;
            }
            if (toTarget.subtract(look.scale(along)).length() > RAY_RADIUS) {
                continue;
            }
            // The closest body along the ray is the one in front, and the one the thrust is for.
            if (along < nearestAlong) {
                nearestAlong = along;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** The flat direction the palm travels: at the target if there is one, otherwise along the gaze. */
    private static Vec3 directionTo(Player player, @Nullable LivingEntity target) {
        if (target != null) {
            Vec3 toTarget = target.position().subtract(player.position());
            Vec3 flatTarget = new Vec3(toTarget.x, 0.0d, toTarget.z);
            if (flatTarget.lengthSqr() > 1.0E-4d) {
                return flatTarget.normalize();
            }
        }
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0.0d, look.z);
        return flatLook.lengthSqr() < 1.0E-4d ? Vec3.ZERO : flatLook.normalize();
    }

    /**
     * Carries the caster a step down the line of the palm. The lift is kept to a token: a lunge is a
     * way of crossing ground, and a leap of it would leave the caster in the air with nothing to
     * strike with. The fall is forgotten for the same reason.
     */
    private static void lunge(Player player, Vec3 thrust) {
        player.setDeltaMovement(thrust.x * LUNGE, LUNGE_LIFT, thrust.z * LUNGE);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
    }

    /** The palm landing: the damage, the shove, the closed channel and the crack of the impact. */
    private static void strike(ServerLevel level, Player caster, LivingEntity target, Vec3 thrust) {
        target.hurt(caster.damageSources().magic(), DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SLOW_TICKS, 0, false, true));
        target.push(thrust.x * KNOCKBACK, KNOCKBACK_LIFT, thrust.z * KNOCKBACK);
        target.hurtMarked = true;

        Vec3 palm = JutsuEffects.handPosition(caster);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, palm.x, palm.y, palm.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static Vec3 centreOf(LivingEntity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
