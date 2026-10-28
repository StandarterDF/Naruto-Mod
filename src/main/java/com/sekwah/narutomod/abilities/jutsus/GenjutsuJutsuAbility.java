package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.RinneganAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.GenjutsuEvents;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * Genjutsu: the illusion that turns a hostile mob into a friend, or at least into somebody else's
 * problem.
 *
 * <p>It is cast at whatever the ninja is looking at, within a short range, and it takes hold of the
 * mob's senses for half a minute. For that half minute the victim cannot see players at all - it will
 * not acquire them as targets and any target it already has on one is dropped - and it is instead
 * pointed at the nearest other mob, which it attacks with whatever it normally attacks with. The
 * actual behaviour lives in {@link GenjutsuEvents}; this class is only the cast.</p>
 *
 * <p>A mastered genjutsu is no longer a single target: once half the technique has been learned the
 * seal can be held, and everything inside the caster's cone of sight is caught at once. The hold also
 * deepens the illusion, so a long seal leaves its victims confused for longer after it is released.</p>
 *
 * <p>It is deliberately not a combat technique on its own: a genjutsu ninja does not kill with it,
 * they make a fight break out somewhere else and walk away from it.</p>
 */
public class GenjutsuJutsuAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C B C */
    public static final long COMBO = 131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "genjutsu");

    /** Chakra for one cast. */
    public static final float CHAKRA_COST = 35.0f;

    /** How far the ninja can reach with the technique. */
    public static final double RANGE = 24.0d;

    /** How much further a full moon lets an illusion reach. */
    public static final double MOON_RANGE_BONUS = 0.25d;

    /** How long the illusion lasts, in ticks. */
    public static final int DURATION = 20 * 30;

    /** Mastery from which the seal can be held to catch everything in the caster's sight. */
    public static final float MASS_MASTERY = 0.5f;

    /** Extra duration a held mass illusion earns per tick, and the ceiling it can reach. */
    private static final int HELD_BONUS_PER_TICK = 5;
    private static final int HELD_BONUS_MAX = 20 * 20;

    /** The most minds one mass cast will hold at once. */
    private static final int MAX_MASS_TARGETS = 8;

    /** Chakra burnt for every tick past the opening one. */
    private static final float UPKEEP = 0.8f;

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
        boolean mass = this.masteryLevel(ninjaData) >= MASS_MASTERY;
        Mob target = findTarget(player);
        Player playerTarget = NarutoConfig.genjutsuOnPlayers ? findPlayerTarget(player) : null;
        boolean opening = chargeAmount <= 0;
        boolean tap = chargeAmount < 0;
        // A tap needs something to take hold of; a held channel stays open even with nothing in
        // sight yet, so the caster can sweep the illusion across whatever enters their view later.
        if (tap && target == null && playerTarget == null
                && !(mass && !findTargets(player, player.isShiftKeyDown() ? 1 : MAX_MASS_TARGETS).isEmpty())) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        LivingEntity activeTarget = playerTarget != null ? playerTarget : target;
        // A fresh tap on an already-affected mob is refused.
        if (activeTarget != null && !mass && opening) {
            boolean under = (activeTarget instanceof Mob mob && GenjutsuEvents.isUnderGenjutsu(mob))
                    || (activeTarget instanceof Player p && GenjutsuEvents.isUnderGenjutsu(p));
            if (under) {
                player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.active",
                        activeTarget.getDisplayName().copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        // Holding the seal to sweep several minds is a mastered technique.
        if (!mass && !opening) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.hold_locked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, opening ? CHAKRA_COST : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            if (opening) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    /**
     * The illusion is laid while the seal is held. A caster below half mastery only reaches the one
     * mob under the crosshair; a mastered caster holds the seal and catches everything in the cone
     * of their sight, and the longer the seal is kept the longer the illusion clings afterwards.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (ticksChanneled % 10 != 0) {
            return;
        }
        boolean mass = this.masteryLevel(ninjaData) >= MASS_MASTERY;
        int limit = mass ? MAX_MASS_TARGETS : 1;
        int duration = DURATION + (mass ? Math.min(HELD_BONUS_MAX, ticksChanneled * HELD_BONUS_PER_TICK) : 0);
        // An open eye reads the mind it is taking hold of far more deeply.
        if (RinneganAbility.isActive(ninjaData)) {
            duration *= 5;
        } else if (SharinganAbility.isActive(ninjaData)) {
            duration *= 2;
        }
        for (Mob target : findTargets(player, limit)) {
            GenjutsuEvents.apply(player, target, duration);
            tryTame(player, target);
            player.displayClientMessage(Component.translatable("jutsu.genjutsu.cast",
                    target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                    target.getY() + target.getBbHeight(), target.getZ(),
                    30, 0.5d, 0.4d, 0.5d, 0.4d);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        if (NarutoConfig.genjutsuOnPlayers) {
            Player aimedPlayer = findPlayerTarget(player);
            if (aimedPlayer != null && !Friends.areFriends(player, aimedPlayer)) {
                GenjutsuEvents.apply(player, aimedPlayer, duration);
                player.displayClientMessage(Component.translatable("jutsu.genjutsu.cast",
                        aimedPlayer.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                level.sendParticles(ParticleTypes.ENCHANT, aimedPlayer.getX(),
                        aimedPlayer.getY() + aimedPlayer.getBbHeight(), aimedPlayer.getZ(),
                        30, 0.5d, 0.4d, 0.5d, 0.4d);
                level.playSound(null, aimedPlayer.getX(), aimedPlayer.getY(), aimedPlayer.getZ(),
                        SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        }
    }

    /** A tap lays one illusion; a hold is handled tick by tick. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive < 0) {
            handleChannelling(player, ninjaData, 0);
        }
    }

    /** Only the mob the caster is actually looking at: a raycast, not the whole view. */
    private static java.util.List<Mob> findTargets(Player player, int limit) {
        java.util.List<Mob> found = new java.util.ArrayList<>();
        Mob aimed = findTarget(player);
        if (aimed != null && !Friends.areFriends(player, aimed)) {
            found.add(aimed);
        }
        return found;
    }

    /** Same as {@code Targeting.lookedAtEntity}, inlined so the cast never depends on that class. */
    private static Entity lookedAtEntity(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reach = eye.add(look.scale(range));
        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, reach,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = blockHit.getType() == HitResult.Type.MISS
                ? range
                : blockHit.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(limit));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(limit)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(), limit * limit);
        return hit != null ? hit.getEntity() : null;
    }

    /** @return the mob the ninja is looking at, or null when they are looking at anything else. */
    public static Mob findTarget(Player player) {
        // The full moon makes an illusion reach further; by day or on a new moon it is the plain range.
        double range = RANGE * (1.0d + MOON_RANGE_BONUS * WorldEnvironment.moonClarity(player));
        Entity lookedAt = lookedAtEntity(player, range);
        if (lookedAt instanceof Mob mob && mob.isAlive()) {
            return mob;
        }
        return null;
    }

    /** @return the player the ninja is looking at, or null when illusions on players are off or the look lands elsewhere. */
    private Player findPlayerTarget(Player caster) {
        if (!NarutoConfig.genjutsuOnPlayers) {
            return null;
        }
        double range = RANGE * (1.0d + MOON_RANGE_BONUS * WorldEnvironment.moonClarity(caster));
        Entity lookedAt = lookedAtEntity(caster, range);
        if (lookedAt instanceof Player other && other != caster
                && !other.isCreative() && !other.isSpectator()
                && !other.isAlliedTo(caster) && !Friends.areFriends(caster, other)
                && !other.getTags().contains("narutomod:genjutsu_resistant")) {
            return other;
        }
        return null;
    }

    /**
     * Takes an animal that is nobody's pet as the caster's own.
     *
     * <p>A beast whose mind has just been taken over is a beast that comes out of the illusion as the
     * ninja's own: it is tamed to the caster, so it follows (and can be told to sit, like any tamed
     * animal) once the illusion itself has worn off. It is deliberately <em>not</em> put on a lead -
     * a lead is a carried object, not a part of taming, and hanging one on the animal was never
     * asked for. The genjutsu stays on it as it was - the whole technique still happens, this only
     * makes the result the ninja's. Pets that already belong to somebody else are left alone: this is
     * a claiming, not a theft.</p>
     */
    private static void tryTame(Player player, Mob target) {
        if (!(target instanceof TamableAnimal animal)) {
            return;
        }
        if (animal.isTame() && !player.getUUID().equals(animal.getOwnerUUID())) {
            return;
        }
        animal.tame(player);
        animal.setOrderedToSit(false);
        player.displayClientMessage(Component.translatable("jutsu.genjutsu.tame",
                animal.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }
}
