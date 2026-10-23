package com.sekwah.narutomod.skills;

import com.sekwah.narutomod.util.ExperienceUtils;
import net.minecraft.world.entity.player.Player;

/**
 * The exchange between experience and skill points.
 *
 * <p>The tree used to be priced directly in experience, which made every purchase chip away at the
 * experience bar and made it hard to see what was actually left to spend. Now experience is only
 * ever exchanged for points and the nodes are priced in points, so a caster can bank what they have
 * earned and spend it at their own pace. The exchange rate is a round number - {@value #XP_PER_POINT}
 * experience per point - and the node prices are the old experience prices divided by the same rate,
 * so filling the whole tree still costs the same amount of experience as before.</p>
 *
 * <p>The maths lives here so the screen, the purchase packet and the tooltip all agree on it. The
 * server is the only side that actually moves either currency.</p>
 */
public final class SkillPoints {

    /** Experience needed to buy a single skill point. */
    public static final int XP_PER_POINT = 10;

    /** Upper bound on one purchase request, so a malformed packet cannot overflow anything. */
    public static final int MAX_PURCHASE = 10_000;

    private SkillPoints() {
    }

    /** Experience the given number of points costs. */
    public static int exchangeCost(int points) {
        return points * XP_PER_POINT;
    }

    /** Points the caster can just afford with the experience they are holding right now. */
    public static int affordable(Player player) {
        return ExperienceUtils.currentPoints(player) / XP_PER_POINT;
    }
}
