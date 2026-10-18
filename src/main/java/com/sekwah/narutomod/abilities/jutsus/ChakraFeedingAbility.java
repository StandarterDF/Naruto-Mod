package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Runs the body on chakra instead of on a full stomach.
 *
 * <p>Slowly turns chakra into food points, a little at a time. It is not the military pill: a pill is
 * swallowed once, fills the bar and hands out buffs, and this is a stance that keeps a ninja walking
 * on a long journey. It will not fill the hunger bar in one go either - it tops it up a point at a
 * time, and only while there is chakra to burn, so it keeps someone alive between meals rather than
 * replacing eating.</p>
 */
public class ChakraFeedingAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** C B B. */
    public static final long COMBO = 122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_feeding");

    /** How often a food point is paid for, in ticks. */
    private static final int FEED_INTERVAL = 40;

    /** Chakra burnt for one food point, before mastery. */
    private static final float CHAKRA_PER_POINT = 10.0f;

    /** A sliver of saturation so the point is not spent the moment it arrives. */
    private static final float SATURATION_PER_POINT = 0.2f;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The stance itself is free - a ninja who is full and has chakra to spare can leave it up - and
     * the price is paid only when a point of food is actually made.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Mastery is paid for the time the stance is up, not for switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        FoodData food = player.getFoodData();
        if (food.getFoodLevel() >= 20 || player.tickCount % FEED_INTERVAL != 0) {
            return;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_POINT);
        if (ninjaData.getChakra() < cost) {
            return;
        }
        ninjaData.useChakra(cost, FEED_INTERVAL);
        food.setFoodLevel(Math.min(20, food.getFoodLevel() + 1));
        food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + SATURATION_PER_POINT));
    }

    /**
     * A stance is mastered by holding it, not by switching it on, so the credit is paid per active
     * tick from {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Nothing to show; the hunger bar is the feedback.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to clean up.
    }
}
