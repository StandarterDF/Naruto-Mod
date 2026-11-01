package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
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
 * <p>Tapped and held, it sweeps everything standing around the caster away from them, which is
 * the answer to being surrounded. Held with sneak, the same hand takes hold of whatever the caster
 * is looking at and drags it off its feet towards them every tick the key is down, the opening
 * half of the usual trick of following it up with a rising blow. Both are the same power read in
 * opposite directions, so they share one key.</p>
 *
 * <p>It is not a technique of its own: the eye has to be open, see {@link RinneganAbility}. The
 * shackle is deliberate - a caster who cannot pay for the eye has lost the powers that come with
 * it.</p>
 */
public class UniversalForceAbility extends Ability implements Ability.Channeled {

    /**
     * How far a cast of this technique carries as a game event. The push is heard a good way off.
     */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** B V B V - short, and not a prefix of the Rinnegan stance or of Chibaku Tensei. */
    public static final long COMBO = 3232L;

    /** Base reach of the pull, measured along the ray the caster is looking down; mastery scales it. */
    private static final double PULL_RANGE = 28.0d;

    /** Base radius of the push around the caster; mastery scales it as well. */
    private static final double PUSH_RADIUS = 12.0d;

    /** What opening the pull and the push cost, and what the held pull burns each further tick. */
    private static final float PULL_COST = 25.0f;
    private static final float PUSH_COST = 35.0f;
    private static final float PULL_UPKEEP = 2.0f;
    private static final int CHAKRA_REGEN_DELAY = 20;

    /**
     * The impulse a pulled target leaves with. The eye is meant to be a battlefield-scale power, not
     * a tug: a pulled body crosses most of the gap in a couple of ticks.
     */
    private static final double PULL_BASE_SPEED = 1.1d;
    private static final double PULL_SPEED_PER_BLOCK = 0.08d;
    private static final double PULL_MAX_SPEED = 3.0d;
    private static final double PULL_LIFT = 0.65d;

    /** The impulse the push gives everything around the caster. */
    private static final double PUSH_SPEED = 2.4d;
    private static final double PUSH_LIFT = 0.9d;

    /** The force itself hurts: being slammed into the caster or thrown across the field is not free. */
    private static final float PULL_DAMAGE = 6.0f;
    private static final float PUSH_DAMAGE = 4.0f;

    /** How often the held pull lands its damage, so holding the key does not shred a body instantly. */
    private static final int PULL_DAMAGE_INTERVAL = 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        boolean pulling = player.isShiftKeyDown();
        boolean opening = chargeAmount <= 0;
        if (pulling && opening
                && !(Targeting.lookedAtEntity(player, PULL_RANGE * reach(ninjaData, this))
                instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.force.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float base = opening ? (pulling ? PULL_COST : PUSH_COST) : PULL_UPKEEP;
        float cost = this.masteryCost(ninjaData, base);
        if (ninjaData.getChakra() < cost) {
            if (opening) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /** A tap pushes once; a hold with sneak is handled tick by tick. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive >= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            pull(level, player, 0, PULL_RANGE * reach(ninjaData, this));
        } else {
            repel(level, player, PUSH_RADIUS * reach(ninjaData, this));
        }
    }

    /**
     * The pull is a hold, not a tap: while the key and sneak are down the looked-at body is dragged
     * towards the caster every tick, and the push is spent once at the start of any other hold.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            pull(level, player, ticksChanneled, PULL_RANGE * reach(ninjaData, this));
        } else if (ticksChanneled == 0) {
            repel(level, player, PUSH_RADIUS * reach(ninjaData, this));
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    /** A held stance is mastered by holding it, not by opening it. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** Drags the looked-at creature to the caster, holding it up a little on the way. */
    private void pull(ServerLevel level, Player player, int ticksChanneled, double range) {
        Entity lookedAt = Targeting.lookedAtEntity(player, range);
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
        if (ticksChanneled % PULL_DAMAGE_INTERVAL == 0) {
            target.hurt(level.damageSources().indirectMagic(player, player), PULL_DAMAGE);
        }

        if (ticksChanneled % 4 == 0) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.6f);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, chest.x, chest.y, chest.z,
                6, 0.4d, 0.4d, 0.4d, 0.15d);
    }

    /** Throws everything standing around the caster outwards, friend and foe alike excluded by side. */
    private void repel(ServerLevel level, Player player, double radius) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), entity -> canAffect(player, entity));
        for (LivingEntity target : nearby) {
            if (target.distanceToSqr(player) > radius * radius) {
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
                8, radius * 0.35d, 0.4d, radius * 0.35d, 0.0d);
    }

    /**
     * How far the technique reaches at this mastery: a little over half of the base reach on the
     * first cast, a good deal more once it is fully learned. Used for both the ray and the sweep,
     * so the two directions grow together.
     */
    private static double reach(INinjaData ninjaData, Ability ability) {
        return 0.6d + 0.8d * Mastery.level(ninjaData, ability);
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
