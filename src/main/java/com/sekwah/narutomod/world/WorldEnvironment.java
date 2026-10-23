package com.sekwah.narutomod.world;

import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The world around the caster as a quiet multiplier: a fire breath in the rain, a water breath in
 * the ocean, a gust in a thunderstorm, nature energy in a jungle.
 *
 * <p>Nothing here changes what a technique does, only how far it reaches and how hard it hits, so a
 * breath still reads the same in every biome - it just listens to the place it is cast in. The whole
 * layer is behind {@code environmentEnabled} so a server that wants flat numbers can turn it off.</p>
 */
public final class WorldEnvironment {

    private WorldEnvironment() {
    }

    /** The four elemental breaths. */
    public enum Element {
        FIRE, WATER, WIND, WOOD
    }

    /** How much further a breath reaches, 0.5x to 1.4x. */
    public static double rangeMultiplier(Player player, Element element) {
        if (!NarutoConfig.environmentEnabled) {
            return 1.0d;
        }
        return Mth.clamp(1.0d + rangeDelta(player, element), 0.5d, 1.4d);
    }

    /** The same weather, but only half as loud: far reaches matter more than raw damage. */
    public static float damageMultiplier(Player player, Element element) {
        return (float) (1.0d + (rangeMultiplier(player, element) - 1.0d) * 0.5d);
    }

    /**
     * How fast nature energy gathers here. Thin in the Nether, absent in the End (there is no life
     * to draw it from), rich in a living forest or jungle, poor in a desert or on open water.
     */
    public static float senjutsuMultiplier(Player player) {
        Level level = player.level();
        if (!NarutoConfig.environmentEnabled) {
            return 1.0f;
        }
        if (level.dimension() == Level.END) {
            return 0.0f;
        }
        if (level.dimension() == Level.NETHER) {
            return 0.5f;
        }
        BlockPos pos = player.blockPosition();
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_FOREST)
                || biome.is(BiomeTags.IS_TAIGA)) {
            return 1.5f;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)
                || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
            return 0.75f;
        }
        return 1.0f;
    }

    /**
     * How clearly the moon lights the night, from zero on a new moon to one on a full one, and zero
     * by day or away from the overworld. The night is when illusions and the chakra sense are at
     * their strongest, so this is the quiet multiplier both of them lean on.
     */
    public static float moonClarity(Player player) {
        if (!NarutoConfig.environmentEnabled) {
            return 0.0f;
        }
        Level level = player.level();
        if (level.dimension() != Level.OVERWORLD || !level.isNight()) {
            return 0.0f;
        }
        // Moon phase 0 is the full moon, 4 the new one, and 5..7 wax back towards full.
        int phase = level.getMoonPhase();
        int distanceFromFull = Math.min(phase, 8 - phase);
        return 1.0f - distanceFromFull / 4.0f;
    }

    private static double rangeDelta(Player player, Element element) {
        Level level = player.level();
        double delta = 0.0d;

        boolean nether = level.dimension() == Level.NETHER;
        boolean end = level.dimension() == Level.END;
        BlockPos pos = player.blockPosition();
        boolean raining = !nether && !end && level.isRainingAt(pos);
        boolean thundering = raining && level.isThundering();
        var biome = level.getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        boolean cold = temperature < 0.15f;
        boolean hot = temperature >= 1.5f;
        boolean ocean = biome.is(BiomeTags.IS_OCEAN);
        boolean jungle = biome.is(BiomeTags.IS_JUNGLE);

        switch (element) {
            case FIRE -> {
                if (nether) {
                    delta += 0.25d;
                }
                if (raining) {
                    delta -= 0.30d;
                }
                if (cold) {
                    delta -= 0.15d;
                }
                if (hot) {
                    delta += 0.15d;
                }
            }
            case WATER -> {
                if (ocean) {
                    delta += 0.20d;
                }
                if (raining) {
                    delta += 0.15d;
                }
                if (nether) {
                    delta -= 0.50d;
                }
                if (hot) {
                    delta -= 0.15d;
                }
            }
            case WIND -> {
                if (thundering) {
                    delta += 0.25d;
                }
                if (hot) {
                    delta += 0.15d;
                }
                if (raining) {
                    delta -= 0.10d;
                }
            }
            case WOOD -> {
                if (jungle) {
                    delta += 0.20d;
                }
                if (raining) {
                    delta += 0.15d;
                }
                if (nether) {
                    delta -= 0.15d;
                }
                if (cold) {
                    delta -= 0.10d;
                }
            }
        }
        return delta;
    }
}
