package com.sekwah.narutomod.util;

import net.minecraft.world.entity.player.Player;

/**
 * Turns a player's level and progress into the number of experience points they are actually holding.
 *
 * <p>Vanilla only exposes a level and a fraction towards the next one, but the skill tree is priced in
 * points, so both the HUD and the server need the same conversion. The formulas below are the exact
 * totals Minecraft uses to reach a level from zero.</p>
 */
public final class ExperienceUtils {

    private ExperienceUtils() {
    }

    /**
     * Total experience points needed to reach the given level from zero. Matches
     * {@code Player#getXpNeededForNextLevel} summed over every level below this one.
     */
    public static int pointsForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) Math.round(2.5d * level * level - 40.5d * level + 360d);
        }
        return (int) Math.round(4.5d * level * level - 162.5d * level + 2220d);
    }

    /**
     * Everything the player is holding right now: the points already banked in their levels plus the
     * fraction they have earned towards the next one.
     */
    public static int currentPoints(Player player) {
        int banked = pointsForLevel(player.experienceLevel);
        int progress = Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return banked + progress;
    }
}
