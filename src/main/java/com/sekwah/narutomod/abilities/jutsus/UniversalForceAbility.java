package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
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

import java.util.List;

/**
 * The Rinnegan's plainest power: one hand pulls, the other pushes.
 *
 * <p>Tapped, it takes hold of whatever the caster is looking at and drags it off its feet towards
 * them, which is the opening half of the usual trick of following it up with a rising blow. Held
 * with sneak, the same hand sweeps outward instead and throws everything standing around the caster
 * away from them, which is the answer to being surrounded. Both are the same power read in opposite
 * directions, so they share one key and one cooldown.</p>
 *
 * <p>It is not a technique of its own: the eye has to be open, see {@link RinneganAbility}. The
 * shackle is deliberate - a caster who cannot pay for the eye has lost the powers that come with
 * it.</p>
 */
public class UniversalForceAbility extends Ability implements Ability.Cooldown {

    /**
     * How far a cast of this technique carries as a game event. The push is heard a good way off.
     */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** B V B V - short, and not a prefix of the Rinnegan stance or of Chibaku Tensei. */
    public static final long COMBO = 2121L;

    /** How far the pull can reach, measured along the ray the caster is looking down. */
    private static final double PULL_RANGE = 28.0d;

    /** How far around the caster the push sweeps. */
    private static final double PUSH_RADIUS = 12.0d;

    /** What the pull and the push cost, and how long the hand needs before it can do either again. */
    private static final float PULL_COST = 25.0f;
    private static final float PUSH_COST = 35.0f;
    private static final int COOLDOWN = 100;

    /**
     * The impulse a pulled target leaves with. The eye is meant to be a battlefield-scale power, not
     * a tug: a pulled body crosses most of the gap in a couple of ticks.
     */
    private static final double PULL_BASE_SPEED = 0.75d;
    private static final double PULL_SPEED_PER_BLOCK = 0.05d;
    private static final double PULL_MAX_SPEED = 2.2d;
    private static final double PULL_LIFT = 0.55d;

    /** The impulse the push gives everything around the caster. */
    private static final double PUSH_SPEED = 2.4d;
    private static final double PUSH_LIFT = 0.9d;

    /** The force itself hurts: being slammed into the caster or thrown across the field is not free. */
    private static final float PULL_DAMAGE = 6.0f;
    private static final float PUSH_DAMAGE = 4.0f;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean pushing = player.isShiftKeyDown();
        if (!pushing && !(Targeting.lookedAtEntity(player, PULL_RANGE) instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.force.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, pushing ? PUSH_COST : PULL_COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            repel(level, player);
        } else {
            pull(level, player);
        }
    }

    /** Drags the looked-at creature to the caster, holding it up a little on the way. */
    private void pull(ServerLevel level, Player player) {
        Entity lookedAt = Targeting.lookedAtEntity(player, PULL_RANGE);
        if (!(lookedAt instanceof LivingEntity target) || !canAffect(player, target)) {
            return;
        }
        Vec3 chest = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d);
        Vec3 towards = player.getEyePosition().subtract(chest);
        double distance = towards.length();
        if (distance < 0.001d) {
            return;
        }
        double speed = Math.min(PULL_MAX_SPEED, PULL_BASE_SPEED + distance * PULL_SPEED_PER_BLOCK);
        Vec3 impulse = towards.normalize().scale(speed);

        target.setDeltaMovement(impulse.x, Math.max(impulse.y, 0.0d) + PULL_LIFT, impulse.z);
        target.hurtMarked = true;
        target.fallDistance = 0.0f;
        target.hurt(level.damageSources().indirectMagic(player, player), PULL_DAMAGE);

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, chest.x, chest.y, chest.z,
                20, 0.4d, 0.4d, 0.4d, 0.15d);
    }

    /** Throws everything standing around the caster outwards, friend and foe alike excluded by side. */
    private void repel(ServerLevel level, Player player) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PUSH_RADIUS), entity -> canAffect(player, entity));
        for (LivingEntity target : nearby) {
            if (target.distanceToSqr(player) > PUSH_RADIUS * PUSH_RADIUS) {
                continue;
            }
            Vec3 away = target.position().subtract(player.position());
            if (away.lengthSqr() < 0.01d) {
                away = player.getLookAngle();
            }
            away = away.normalize().scale(PUSH_SPEED);
            target.push(away.x, PUSH_LIFT, away.z);
            target.hurtMarked = true;
            target.fallDistance = 0.0f;
            target.hurt(level.damageSources().indirectMagic(player, player), PUSH_DAMAGE);
            level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    6, 0.3d, 0.3d, 0.3d, 0.05d);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                8, PUSH_RADIUS * 0.35d, 0.4d, PUSH_RADIUS * 0.35d, 0.0d);
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private boolean canAffect(Player caster, LivingEntity target) {
        if (target == caster || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && player.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
