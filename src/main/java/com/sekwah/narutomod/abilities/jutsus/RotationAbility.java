package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Eight Trigrams Palms Revolving Heaven: the caster spins and everything that was coming at them
 * stops.
 *
 * <p>It is the Byakugan's answer to being surrounded, and it answers the two things that surround a
 * ninja: bodies and projectiles. Arrows, thrown tridents and fireballs inside the spin are torn apart
 * on the spot, everything standing close is thrown outwards, and for a heartbeat the caster is
 * simply not where the blow lands - a short resistance and a window in which vanilla refuses to let
 * new damage through.</p>
 *
 * <p>It does not break blocks and does not kill anyone on its own; it buys the caster a step of
 * space to take with their hands. The eye has to be open, see {@link ByakuganAbility}.</p>
 */
public class RotationAbility extends Ability implements Ability.Cooldown {

    /** The spin is heard a good way off. */
    @Override
    public int noiseRadius() {
        return 14;
    }

    /** V B V V B - the Byakugan, then one more key. */
    public static final long COMBO = 23223L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rotation");

    /** How far the spin reaches for bodies and projectiles. */
    private static final double RADIUS = 5.0d;

    /** What it costs and how long the eye needs before it can spin again. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 160;

    /** How hard the spin throws a body outwards, and how high it lifts it. */
    private static final double PUSH_SPEED = 0.9d;
    private static final double PUSH_LIFT = 0.3d;

    /** The least speed a caught shot is sent back with, and how much of its own speed it keeps. */
    private static final double REFLECT_SPEED = 1.35d;
    private static final double REFLECT_KEEP = 1.1d;

    /** How long the resistance and the vanilla damage window last, in ticks. */
    private static final int GUARD_TICKS = 40;
    private static final int INVULNERABLE_TICKS = 20;

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

        clearProjectiles(level, player);
        pushBodies(level, player);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, GUARD_TICKS, 1, false, false));
        // The vanilla window is what actually refuses the next hit; resistance only softens the ones
        // that come after it. Both are short, so the spin is a dodge and not a fortress.
        player.invulnerableTime = INVULNERABLE_TICKS;
        player.fallDistance = 0.0f;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4f, 0.8f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                10, RADIUS * 0.4d, 0.4d, RADIUS * 0.4d, 0.0d);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                24, RADIUS * 0.35d, 0.5d, RADIUS * 0.35d, 0.06d);
    }

    /**
     * Turns away every projectile in the spin that was not fired by the caster. A shot with a body to
     * it - an arrow or a thrown technique - is sent back the way it came rather than torn apart, so
     * the spin answers the archer as well as the arrow; anything else (a pearl, a bottle, a trident
     * with its own rules) is simply destroyed, which is what the old version did to everything.
     */
    private void clearProjectiles(ServerLevel level, Player player) {
        List<Projectile> incoming = level.getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(RADIUS), projectile -> projectile.getOwner() != player);
        for (Projectile projectile : incoming) {
            if (projectile.distanceToSqr(player) > RADIUS * RADIUS) {
                continue;
            }
            level.sendParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(),
                    6, 0.15d, 0.15d, 0.15d, 0.05d);
            if (projectile instanceof AbstractArrow || projectile instanceof AbstractHurtingProjectile) {
                reflect(projectile, player);
            } else {
                projectile.discard();
            }
        }
    }

    /**
     * Sends a caught shot back outwards with the caster as its new owner, so a reflected arrow or
     * fireball can actually hurt whoever loosed it. The direction is taken from the shot's own place
     * relative to the caster, which is the way it came in; speed is kept, with a floor so a spent
     * arrow does not flop out of the spin.
     */
    private void reflect(Projectile projectile, Player player) {
        Vec3 away = projectile.position().subtract(player.position());
        if (away.lengthSqr() < 0.04d) {
            away = player.getLookAngle().reverse();
        }
        double speed = Math.max(REFLECT_SPEED, projectile.getDeltaMovement().length() * REFLECT_KEEP);
        projectile.setOwner(player);
        projectile.setDeltaMovement(away.normalize().scale(speed).add(0.0d, 0.08d, 0.0d));
        projectile.hurtMarked = true;
    }

    /** Throws every body inside the spin outwards, sparing the caster's own side. */
    private void pushBodies(ServerLevel level, Player player) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS), entity -> canPush(player, entity));
        for (LivingEntity target : nearby) {
            if (target.distanceToSqr(player) > RADIUS * RADIUS) {
                continue;
            }
            Vec3 away = target.position().subtract(player.position());
            if (away.lengthSqr() < 0.01d) {
                away = player.getLookAngle().reverse();
            }
            away = away.normalize().scale(PUSH_SPEED);
            target.push(away.x, PUSH_LIFT, away.z);
            target.hurtMarked = true;
            target.fallDistance = 0.0f;
        }
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private boolean canPush(Player caster, LivingEntity target) {
        if (target == caster || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && player.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
