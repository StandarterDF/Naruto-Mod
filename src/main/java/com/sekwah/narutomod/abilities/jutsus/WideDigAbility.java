package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The wide pick swing: while the stance is up every block the caster breaks takes a three by three
 * plane of blocks with it, oriented by the face that was hit.
 *
 * <p>This is deliberately not the ore excavation. That technique finds a vein by itself and follows
 * it out of the rock; this one only widens the swing the caster already made, so the caster decides
 * where the tunnel goes and what it is for. It also works on any block, not just ore.</p>
 *
 * <p>The stance itself costs nothing per tick - it is a way of swinging, not a technique that burns
 * chakra while idle. The price is paid per block: every extra block the swing takes costs chakra and
 * one use of the tool's durability. Any block the caster could have dug up with that tool is taken -
 * a block the tool cannot harvest at all (deepslate under a wooden pick, obsidian under anything but
 * diamond) is left, but ordinary stone, dirt, gravel and the rest all move, so the swing widens the
 * tunnel rather than only the rock seam. See {@code WideDigEvents} for the work, which has to live in
 * a break event because an ability cannot see blocks being broken.</p>
 */
public class WideDigAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C V V. */
    public static final long COMBO = 133L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "wide_dig");

    /** Chakra charged for every extra block the swing takes, after mastery. */
    public static final float CHAKRA_PER_EXTRA_BLOCK = 2.0f;

    /** How long the extra blocks hold natural chakra regeneration down. */
    public static final int REGEN_DELAY_PER_BLOCK = 20;

    /**
     * Mastery is earned per extra block that actually comes out of the ground, not per tick the
     * stance is held: a caster who swings does the work, one who only carries the stance on with an
     * empty tunnel does not. The unit is the same "one cast" as everywhere else, so this is one cast
     * worth of progress every thirty-two extra blocks.
     */
    public static final float MASTERY_CREDIT_PER_BLOCK = 1.0f / 32.0f;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The stance is free to hold; the blocks it actually widens the swing over are charged by the
     * break handler. Refusing here is only about the server having the technique switched off.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.wideDigEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.widedig.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to do per tick: a stance only buys mastery for the blocks it actually takes, which
        // the break handler credits one by one. recordMastery stays empty too, so flicking the stance
        // on is not a cast either.
    }

    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Credits one broken block towards mastery. The break handler lives outside this class, so this
     * thin public door exists only to reach the protected credit helper.
     */
    public void creditBrokenBlock(Player player, INinjaData ninjaData) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_BLOCK);
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
    }

    /** The price of one extra block after mastery, for the break handler. */
    public float chakraPerExtraBlock(INinjaData ninjaData) {
        return this.masteryCost(ninjaData, CHAKRA_PER_EXTRA_BLOCK);
    }

    /** Whether the wide swing is currently up. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
