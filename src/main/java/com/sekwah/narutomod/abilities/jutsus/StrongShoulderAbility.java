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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Three paces of shoulder-first body check.
 *
 * <p>The charge is short on purpose. A taijutsu body does not run at anything - it closes the last
 * step of a gap it has already measured, takes the first body in the way off its feet and is done.
 * If the line ends on stone instead of a body, the shoulder takes the wall and the caster's own head
 * rings for it: that is the risk of a move that cannot be steered once it starts.</p>
 *
 * <p>The wall also makes the blow heavier for whoever was caught against it, which is the whole
 * reason to charge down a corridor rather than across open ground.</p>
 */
public class StrongShoulderAbility extends Ability implements Ability.Cooldown {

    /** A body check is loud, and the wall makes it louder. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B C B B A B - the taijutsu branch's impact seal. */
    public static final long COMBO = 132121L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "strong_shoulder");

    /** How far the charge carries, and how finely the path is tested against the world. */
    private static final double DASH = 3.0d;
    private static final double STEP = 0.25d;

    /** Stamina paid, and the wait before the shoulder can be put into another body. */
    private static final float COST = 15.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 120;

    /** What the body check deals, and what the wall adds to it. */
    private static final float DAMAGE = 4.0f;
    private static final float WALL_BONUS = 2.0f;

    /** A body caught between the shoulder and the stone loses its feet for half a second. */
    private static final int SLOW_TICKS = 10;
    private static final int SLOW_AMPLIFIER = 6;
    private static final int NAUSEA_TICKS = 60;

    /** How wide the body check reaches either side of the path, so a shoulder does not slip past. */
    private static final double HIT_RADIUS = 1.0d;

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
        Vec3 direction = horizontalLook(player);
        if (direction.lengthSqr() < 1.0E-4d) {
            return;
        }

        // The path is walked in small steps so the charge stops on the first block it cannot pass,
        // rather than arriving inside the wall and being pushed back out by the game afterwards.
        Vec3 start = player.position();
        double travelled = 0.0d;
        boolean stoppedByWall = false;
        while (travelled < DASH) {
            double next = Math.min(DASH, travelled + STEP);
            if (!level.noCollision(player, player.getBoundingBox().move(direction.scale(next)))) {
                stoppedByWall = true;
                break;
            }
            travelled = next;
        }

        LivingEntity hit = firstBodyAlong(level, player, start, direction, travelled);
        Vec3 end = start.add(direction.scale(travelled));
        player.teleportTo(end.x, end.y, end.z);
        // The charge ends where it ends: the caster keeps their footing instead of being flung on.
        player.setDeltaMovement(player.getDeltaMovement().scale(0.2d));
        player.hurtMarked = true;

        if (hit != null) {
            hit.hurt(player.damageSources().playerAttack(player), stoppedByWall ? DAMAGE + WALL_BONUS : DAMAGE);
            if (stoppedByWall) {
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS,
                        SLOW_AMPLIFIER, false, true));
                hit.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0, false, true));
            }
            level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY(0.6d), hit.getZ(),
                    12, 0.3d, 0.3d, 0.3d, 0.05d);
        }

        for (int i = 0; i <= 6; i++) {
            Vec3 at = start.add(direction.scale(travelled * i / 6.0d)).add(0.0d, 0.9d, 0.0d);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 2, 0.1d, 0.1d, 0.1d, 0.02d);
        }
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * The first body standing on the path of the charge, measured from the caster outwards. Bodies
     * behind the caster and bodies well off to the side are ignored; the shoulder hits what is in
     * front of it.
     */
    private static LivingEntity firstBodyAlong(ServerLevel level, Player caster, Vec3 start,
                                               Vec3 direction, double travelled) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().expandTowards(direction.scale(travelled)).inflate(HIT_RADIUS),
                entity -> canHit(caster, entity));
        LivingEntity first = null;
        double firstAlong = Double.MAX_VALUE;
        for (LivingEntity candidate : nearby) {
            Vec3 offset = candidate.position().subtract(start);
            double along = offset.dot(direction);
            if (along < 0.0d || along > travelled + HIT_RADIUS) {
                continue;
            }
            double sideways = Math.sqrt(Math.max(0.0d, offset.lengthSqr() - along * along));
            if (sideways > HIT_RADIUS + candidate.getBbWidth() * 0.5d) {
                continue;
            }
            if (along < firstAlong) {
                firstAlong = along;
                first = candidate;
            }
        }
        return first;
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

    /** The look vector flattened onto the ground: the shoulder goes where the feet can follow. */
    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        return flat.lengthSqr() < 1.0E-4d ? Vec3.ZERO : flat.normalize();
    }
}
