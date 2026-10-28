package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The upkeep of genjutsu: what a mob (or, on servers that allow it, a player) does while its mind is
 * being held.
 *
 * <p>A victim keeps the illusion until its timer runs out. For mobs the existing behaviour applies:
 * they are pointed at other mobs and never acquire players. For players the illusion is lighter but
 * still costly - they cannot hit the caster, their techniques deal no damage to the caster and they
 * cannot draw on their chakra for as long as it lasts.</p>
 *
 * <p>The affected flag lives in the entity's persistent data rather than a map kept in memory, so a
 * mob or player that is unloaded and loaded again (or saved and reloaded with the world) keeps the
 * illusion for exactly as long as it should.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GenjutsuEvents {

    /** Game time at which the illusion drops off the victim. */
    private static final String END_TAG = "narutomod:genjutsu_until";

    /** Who cast it. */
    private static final String CASTER_TAG = "narutomod:genjutsu_caster";

    /** How far a victim looks for something else to fight. */
    private static final double SEARCH_RANGE = 16.0d;

    /** How often a victim that has lost its target is given a new one, in ticks. */
    private static final int RETARGET_INTERVAL = 20;

    /**
     * Puts a mob under the illusion. Server side only, callers are expected to have checked that the
     * mob is a sensible victim.
     */
    public static void apply(Player caster, Mob victim, int duration) {
        // Kai leaves a short window in which the mind refuses another illusion.
        if (victim.getPersistentData().getLong("narutomod:kai_immunity_until") > victim.level().getGameTime()) {
            return;
        }
        CompoundTag data = victim.getPersistentData();
        data.putLong(END_TAG, victim.level().getGameTime() + duration);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        // Whatever it was about to hit, it is no longer its enemy.
        victim.setTarget(null);
    }

    /** Puts a player under the illusion. Only meaningful when the server allows it. */
    public static void apply(Player caster, Player victim, int duration) {
        if (victim.getTags().contains("narutomod:genjutsu_resistant")) {
            return;
        }
        if (victim.getPersistentData().getLong("narutomod:kai_immunity_until") > victim.level().getGameTime()) {
            return;
        }
        CompoundTag data = victim.getPersistentData();
        data.putLong(END_TAG, victim.level().getGameTime() + duration);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Math.min(duration, 160), 1, false, true));
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.8d, victim.getZ(),
                    24, 0.5d, 0.7d, 0.5d, 0.02d);
        }
    }

    /** Drops the illusion early. */
    public static void clear(Mob victim) {
        CompoundTag data = victim.getPersistentData();
        data.remove(END_TAG);
        data.remove(CASTER_TAG);
        victim.setTarget(null);
    }

    /** Drops the illusion early from a player. */
    public static void clear(Player victim) {
        CompoundTag data = victim.getPersistentData();
        data.remove(END_TAG);
        data.remove(CASTER_TAG);
        victim.removeEffect(MobEffects.DARKNESS);
        victim.removeEffect(MobEffects.CONFUSION);
        victim.removeEffect(MobEffects.BLINDNESS);
    }

    /** @return whether the mob is currently under the illusion. */
    public static boolean isUnderGenjutsu(Mob mob) {
        return mob.getPersistentData().contains(END_TAG);
    }

    /** @return whether the player is currently under the illusion. */
    public static boolean isUnderGenjutsu(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(END_TAG)) {
            return false;
        }
        return data.getLong(END_TAG) > player.level().getGameTime();
    }

    /** @return the illusion's end time, or 0 when the mob is not affected. */
    public static long getEnd(Mob mob) {
        return mob.getPersistentData().getLong(END_TAG);
    }

    /** @return the illusion's end time, or 0 when the player is not affected. */
    public static long getEnd(Player player) {
        return player.getPersistentData().getLong(END_TAG);
    }

    /** @return the caster's id, or null when the player is not affected. */
    public static UUID getCasterUUID(Player player) {
        String value = player.getPersistentData().getString(CASTER_TAG);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity instanceof Mob mob) {
            if (!isUnderGenjutsu(mob)) {
                return;
            }
            if (mob.level().getGameTime() >= getEnd(mob)) {
                clear(mob);
                return;
            }

            LivingEntity target = mob.getTarget();
            if (target != null && !isValidVictim(mob, target)) {
                mob.setTarget(null);
                target = null;
            }

            if (target == null && mob.tickCount % RETARGET_INTERVAL == 0) {
                LivingEntity next = findVictim(mob);
                if (next != null) {
                    mob.setTarget(next);
                }
            }

            if (mob.tickCount % 10 == 0 && mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.WITCH, mob.getX(),
                        mob.getY() + mob.getBbHeight() + 0.3d, mob.getZ(), 3, 0.3d, 0.2d, 0.3d, 0.0d);
            }
        } else if (entity instanceof Player player) {
            if (!isUnderGenjutsu(player)) {
                return;
            }
            if (player.level().getGameTime() >= getEnd(player)) {
                clear(player);
            }
        }
    }

    /**
     * Refuses targeting changes that would break the illusion, which is what actually keeps a victim
     * off the caster: the vanilla targeting goals fire this every time they pick a player.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (!isUnderGenjutsu(mob)) {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        // Clearing the target must stay possible, or the victim could never be let go.
        if (newTarget == null) {
            return;
        }
        if (!isValidVictim(mob, newTarget)) {
            event.setCanceled(true);
        }
    }

    private static boolean isValidVictim(Mob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof Player) {
            return false;
        }
        if (candidate instanceof com.sekwah.narutomod.entity.ShadowCloneEntity) {
            return false;
        }
        if (candidate instanceof Mob other && isUnderGenjutsu(other)) {
            return false;
        }
        return !mob.isAlliedTo(candidate);
    }

    private static LivingEntity findVictim(Mob mob) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (!isValidVictim(mob, other)) {
                continue;
            }
            double distance = mob.distanceToSqr(other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }
}
