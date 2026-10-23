package com.sekwah.narutomod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Elemental states a creature can be left in by the breaths: soaked through, or chilled to the bone.
 *
 * <p>Both are stored on the entity's persistent data as an expiry tick rather than as an effect, so
 * they survive a chunk unload and do not show up as potion icons. Keeping them out of the effect
 * system is deliberate: a wet creature is not "under a potion", it is wet, and the way it interacts
 * with fire and lightning is ours to define.</p>
 *
 * <p>The cold state is fed back in by {@code events/ElementalStateEvents}, which applies the slow and
 * the frost particles while the timer runs. Wetness needs less upkeep: the event just puts out any
 * fire that tries to catch, and rain or water keeps it topped up.</p>
 */
public final class ElementalStates {

    private static final String WET_UNTIL = "narutomod:wet_until";
    private static final String CHILLED_UNTIL = "narutomod:chilled_until";

    /** How long a creature stays wet in ordinary weather. */
    public static final int WET_TICKS = 20 * 20;

    /** In a cold biome water does not evaporate, so a soaking lasts much longer. */
    public static final int WET_TICKS_COLD = 20 * 60;

    /** In a hot, dry biome a soaking is gone in seconds. */
    public static final int WET_TICKS_HOT = 20 * 5;

    /** How long the chill of cold water lasts. */
    public static final int CHILLED_TICKS = 20 * 10;

    /** How much of a fire breath's damage gets through a soaked target. */
    public static final float WET_FIRE_DAMAGE_FACTOR = 0.75f;

    /** How much extra a fire breath does to a chilled target: it melts the frost off. */
    public static final float CHILLED_FIRE_DAMAGE_FACTOR = 1.25f;

    private ElementalStates() {
    }

    public static boolean isWet(LivingEntity entity) {
        return entity.getPersistentData().getLong(WET_UNTIL) > entity.level().getGameTime();
    }

    public static boolean isChilled(LivingEntity entity) {
        return entity.getPersistentData().getLong(CHILLED_UNTIL) > entity.level().getGameTime();
    }

    public static long wetTicksLeft(LivingEntity entity) {
        return Math.max(0L, entity.getPersistentData().getLong(WET_UNTIL) - entity.level().getGameTime());
    }

    public static void setWet(LivingEntity entity, int ticks) {
        entity.getPersistentData().putLong(WET_UNTIL, entity.level().getGameTime() + ticks);
    }

    /** Keeps a creature wet without shortening a longer soaking it already has. */
    public static void refreshWet(LivingEntity entity, int ticks) {
        if (wetTicksLeft(entity) < ticks) {
            setWet(entity, ticks);
        }
    }

    public static void dry(LivingEntity entity) {
        entity.getPersistentData().remove(WET_UNTIL);
    }

    public static void setChilled(LivingEntity entity, int ticks) {
        entity.getPersistentData().putLong(CHILLED_UNTIL, entity.level().getGameTime() + ticks);
    }

    /** Warms a creature up, removing the chill. */
    public static void warm(LivingEntity entity) {
        entity.getPersistentData().remove(CHILLED_UNTIL);
    }

    /**
     * How long a soaking lasts where the creature stands: cold biomes hold water, hot ones drink it.
     */
    public static int wetTicksAt(ServerLevel level, BlockPos pos) {
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        if (temperature <= 0.15f) {
            return WET_TICKS_COLD;
        }
        if (temperature >= 1.0f) {
            return WET_TICKS_HOT;
        }
        return WET_TICKS;
    }

    /** @return whether the place is cold enough for a soaking to turn into a chill. */
    public static boolean freezingAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }
}
