package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.events.GenjutsuEvents;
import com.sekwah.narutomod.util.Targeting;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
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
 * <p>It is deliberately not a combat technique on its own: a genjutsu ninja does not kill with it,
 * they make a fight break out somewhere else and walk away from it.</p>
 */
public class GenjutsuJutsuAbility extends Ability implements Ability.Cooldown {
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

    private static final int COOLDOWN = 20 * 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        Mob target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (GenjutsuEvents.isUnderGenjutsu(target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.active",
                    target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A mastered illusion does not stop at one mind: the caster can lead up to three creatures
        // that are standing together, which is what turns the technique from a duel trick into a
        // way to break a patrol.
        float mastery = this.masteryLevel(ninjaData);
        int limit = 1 + (mastery >= 0.3f ? 1 : 0) + (mastery >= 0.6f ? 1 : 0);
        java.util.List<Mob> targets = findTargets(player, limit);
        if (targets.isEmpty()) {
            // The check in handleCost already passed, so this should not happen; refund nothing, the
            // cast simply fizzles rather than charging for nothing.
            return;
        }
        for (Mob target : targets) {
            GenjutsuEvents.apply(player, target, DURATION);
            // A mind that is already held can be led: a beast comes out of the illusion as the ninja's own.
            tryTame(player, target);

            player.displayClientMessage(Component.translatable("jutsu.genjutsu.cast",
                    target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.DARK_PURPLE), true);

            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                        target.getY() + target.getBbHeight(), target.getZ(),
                        30, 0.5d, 0.4d, 0.5d, 0.4d);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ILLUSIONER_CAST_SPELL, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        }
    }

    /** The looked-at mob first, then the nearest others sharing roughly the same line of sight. */
    private static java.util.List<Mob> findTargets(Player player, int limit) {
        java.util.List<Mob> found = new java.util.ArrayList<>();
        Mob aimed = findTarget(player);
        if (aimed == null) {
            return found;
        }
        found.add(aimed);
        double range = RANGE * (1.0d + MOON_RANGE_BONUS * WorldEnvironment.moonClarity(player));
        var look = player.getLookAngle();
        java.util.List<Mob> nearby = player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(range),
                mob -> mob.isAlive() && mob != aimed && mob.distanceToSqr(player) <= range * range);
        nearby.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        for (Mob mob : nearby) {
            if (found.size() >= limit) {
                break;
            }
            var toMob = mob.position().subtract(player.position());
            if (toMob.lengthSqr() < 1.0e-4d) {
                continue;
            }
            if (look.dot(toMob.normalize()) < 0.5d) {
                continue;
            }
            found.add(mob);
        }
        return found;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /** @return the mob the ninja is looking at, or null when they are looking at anything else. */
    public static Mob findTarget(Player player) {
        // The full moon makes an illusion reach further; by day or on a new moon it is the plain range.
        double range = RANGE * (1.0d + MOON_RANGE_BONUS * WorldEnvironment.moonClarity(player));
        Entity lookedAt = Targeting.lookedAtEntity(player, range);
        if (lookedAt instanceof Mob mob && mob.isAlive()) {
            return mob;
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
