package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.nbt.CompoundTag;
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
 *
 * <p>At first it is deliberately slower than a meal is worth waiting for: a food point every
 * {@link #FEED_INTERVAL} ticks. Mastery speeds that up until a mastered ninja gets a point every
 * {@link #MIN_FEED_INTERVAL} ticks instead, and at full mastery the stance fills both the hunger bar
 * and the saturation behind it outright, so a finished technique is a meal in its own right.</p>
 */
public class ChakraFeedingAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C B B. */
    public static final long COMBO = 3331L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_feeding");

    /** How often a food point is paid for without any mastery, in ticks. */
    private static final int FEED_INTERVAL = 80;

    /** How much mastery takes off that interval, in ticks, so a mastered stance feeds twice as often. */
    private static final int MIN_FEED_INTERVAL = 40;

    /**
     * Ticks since the last feeding, kept on the player because the interval changes with mastery.
     */
    private static final String FEED_TIMER_TAG = "narutomod:feed_timer";

    /** Mastery at which one feeding restores the whole bar instead of a single point. */
    private static final float FULL_MASTERY = 0.999f;

    /** The top of the hunger and saturation bars, in their own units. */
    private static final int FULL_FOOD_LEVEL = 20;

    private static final float FULL_SATURATION = 20.0f;

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

        float mastery = Mastery.level(ninjaData, this);
        int interval = (int) (FEED_INTERVAL - MIN_FEED_INTERVAL * mastery);

        // The interval shrinks with mastery, so a wall-clock modulo would drift; count the ticks here
        // instead. The timer is left alone when the stance is switched off, so a short break from it
        // does not throw the rhythm away.
        CompoundTag data = player.getPersistentData();
        int timer = data.getInt(FEED_TIMER_TAG) + 1;
        if (timer < interval) {
            data.putInt(FEED_TIMER_TAG, timer);
            return;
        }
        data.putInt(FEED_TIMER_TAG, 0);

        FoodData food = player.getFoodData();
        if (food.getFoodLevel() >= FULL_FOOD_LEVEL) {
            return;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_POINT);
        if (ninjaData.getChakra() < cost) {
            return;
        }
        ninjaData.useChakra(cost, interval);
        if (mastery >= FULL_MASTERY) {
            // Entirely lived-in technique: the stance is worth a full meal, saturation included.
            food.setFoodLevel(FULL_FOOD_LEVEL);
            food.setSaturation(FULL_SATURATION);
        } else {
            // Otherwise it is still one point at a time, with a sliver of saturation under it.
            food.setFoodLevel(Math.min(FULL_FOOD_LEVEL, food.getFoodLevel() + 1));
            food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + SATURATION_PER_POINT));
        }
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
