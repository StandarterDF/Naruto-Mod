package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;

/**
 * The blade held ready between blows rather than swung.
 *
 * <p>A swordsman who has learned to read a fight does not need to be faster than the arm coming at
 * them, only to have the edge already where that arm will be. While the stance is up a share of
 * every blow that lands - a sword, a fist or an arrow - is turned aside on the flat of the blade and
 * answered with a cut of the caster's own, paid for out of stamina per parry rather than per second.
 * The blade has to be in the hand for any of it: sheath the sword and the guard is gone.</p>
 *
 * <p>An arrow can be knocked out of the air but cannot be cut back, so a parried projectile only
 * costs the parry and its shooter nothing. The rolls and the counter-blow live in
 * {@code KenjutsuTaijutsuEvents}, because a technique cannot see a blow that is about to land; all
 * this class does is hold the stance, pay for it and leave the mark the hit hook reads.</p>
 */
public class ParryAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A guard is held, not announced: the clash itself is what is heard. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 3 1 1 - the kenjutsu branch's guard seal. */
    public static final long COMBO = 132311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "blade_parry");

    /** The mark the hit hook reads: kept a few ticks ahead while the guard is up. */
    public static final String UNTIL_TAG = "narutomod:blade_parry_until";

    /** What it costs to raise the blade, and what holding it there costs every tick. */
    private static final float OPEN_COST = 10.0f;
    private static final float UPKEEP = 0.1f;

    private static final int STAMINA_REGEN_DELAY = 10;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but a small
     * grace lets a blow that resolves a moment after the stance drops still find the guard.
     */
    private static final int MARK_GRACE = 5;

    /** One blow in roughly three is turned aside, and what a parry costs in stamina. */
    public static final float BLOCK_CHANCE = 0.35f;
    public static final float BLOCK_STAMINA = 4.0f;

    /** What the blade gives back to whoever swung a melee blow into it. */
    public static final float COUNTER_DAMAGE = 4.0f;

    /** The regen delay a parry puts on the stamina bar, so blocking cannot be spammed for free. */
    public static final int BLOCK_REGEN_DELAY = 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!hasSword(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.blade_parry.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The first tick is the one that raises the blade, and it pays for the raising as well as its
        // own upkeep; every tick after that is only the trickle. The mark is what tells the two
        // apart, because the toggle set already holds the stance by the time this is first called.
        boolean opening = !isActive(player);
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST + UPKEEP : UPKEEP);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A guard is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);
    }

    /** The guard is mastered by holding it, so casting it on and off records nothing. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The mark is written by the server and nothing about the guard is drawn on the client.
    }

    /** Dropping the blade takes the guard with it, so the next blow is an honest one. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** @return whether the blade is up right now, asked by the hit hook. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data instead. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** @return whether the caster is holding a sword in the main hand, which the guard needs. */
    public static boolean hasSword(Player player) {
        return player.getMainHandItem().getItem() instanceof SwordItem;
    }
}
