package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Wraps the caster's chakra around their hands: everything mined breaks about two and a half times
 * as fast and melee hits land harder.
 *
 * <p>The price is deliberately spread out rather than charged per tick of mining. Holding the stance
 * costs a trickle of chakra and, more importantly, holds natural regeneration down, while the two
 * things that actually do the work are charged as they happen: a point of chakra for every block
 * that is broken (see {@code ChakraFistEvents}) and a few for every melee hit. Mine flat out and the
 * stance pays for itself; mine nothing and it still bleeds slowly.</p>
 *
 * <p>The ability itself only owns the toggle and the trickle. Speed and the melee bonus cannot be
 * done from an ability at all, they need Forge events, which is why they live in
 * {@code events/ChakraFistEvents}.</p>
 *
 * <p>Mastery follows the same split. Holding the stance is worth almost nothing - one cast per half
 * an hour - while breaking a block pays a thirty-second of a cast and landing a hit an eighth. The
 * technique levels from doing the thing it exists for, not from leaving it switched on in a field.</p>
 */
public class ChakraFistAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C C - one key past chakra charge, the stance it builds on. */
    public static final long COMBO = 11L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_fist");

    /** Mining speed multiplier while the stance is fully mastered. */
    public static final float BREAK_SPEED_MULTIPLIER = 2.5f;

    /**
     * What the stance is worth on the very first cast. The hands are wrapped in chakra from the
     * start, but the fine control that turns that into real speed is learned: a beginner gets a
     * nudge, a master gets the full multiplier above.
     */
    public static final float MIN_BREAK_SPEED_MULTIPLIER = 1.2f;

    /**
     * The mining multiplier for this caster, sliding from {@link #MIN_BREAK_SPEED_MULTIPLIER} to
     * {@link #BREAK_SPEED_MULTIPLIER} with mastery. Mastery is earned by breaking blocks and
     * landing hits with the stance up, so the speed also grows with use instead of being handed
     * over on the first cast.
     */
    public static float breakSpeedMultiplier(INinjaData ninjaData) {
        float mastery = Mastery.level(ninjaData, ID);
        return MIN_BREAK_SPEED_MULTIPLIER
                + (BREAK_SPEED_MULTIPLIER - MIN_BREAK_SPEED_MULTIPLIER) * mastery;
    }

    /** Chakra charged for each block that is actually broken, not for each tick of digging. */
    public static final float CHAKRA_PER_BLOCK = 1.0f;

    /** Chakra charged for each melee hit, and what that extra buys in damage. */
    public static final float CHAKRA_PER_HIT = 3.0f;
    public static final float BONUS_MELEE_DAMAGE = 3.0f;

    /** Chakra per tick just for holding the stance. */
    private static final float DRAIN_PER_TICK = 0.02f;

    /**
     * The stance keeps natural regeneration pinned down, that is part of what it costs. Turning it
     * off, and breaking enough blocks, push the delay out.
     */
    private static final int REGEN_DELAY = 10;
    public static final int REGEN_DELAY_PER_BLOCK = 20;
    public static final int REGEN_DELAY_PER_HIT = 40;

    /**
     * Mastery paid for one tick of merely holding the stance. Deliberately tiny: the fist is
     * mastered by the work it does, so standing in a field with it switched on is worth almost
     * nothing - one cast per half an hour of idling, against one cast per 32 blocks or 8 hits.
     */
    public static final float IDLE_MASTERY_CREDIT_PER_TICK = 1.0f / (30.0f * 60.0f * 20.0f);

    /** Who the fist has actually worked for: one mastery cast per this many broken blocks. */
    public static final float MASTERY_BLOCKS_PER_CAST = 32.0f;

    /** Who the fist has actually worked for: one mastery cast per this many melee hits. */
    public static final float MASTERY_HITS_PER_CAST = 8.0f;

    /** Mastery earned by breaking one block while the stance is up. */
    public static final float MASTERY_CREDIT_PER_BLOCK = 1.0f / MASTERY_BLOCKS_PER_CAST;

    /** Mastery earned by landing one melee hit while the stance is up. */
    public static final float MASTERY_CREDIT_PER_HIT = 1.0f / MASTERY_HITS_PER_CAST;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * A stance the caster can end up unable to pay for: run dry and the next tick ends it, which is
     * a better way out than letting it run into negative chakra.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, DRAIN_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // The work happens in the break and hit events, so holding the stance is worth only a
        // trickle of mastery. The real credit is paid where the fist is actually used: see
        // ChakraFistAbility#MASTERY_CREDIT_PER_BLOCK and #MASTERY_CREDIT_PER_HIT.
        this.recordMasteryCredit(player, ninjaData, IDLE_MASTERY_CREDIT_PER_TICK);
    }

    /**
     * This stance is mastered by working with it, not by leaving it on, so the tick credit is tiny
     * and paid from {@link #performServer}; the real credit comes from the block and hit events.
     * Casting it on and off still records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Nothing to do on the client, the toggle state is already synced for the events.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to clean up, the events simply stop finding the stance active.
    }

    /**
     * Whether the stance is currently up, asked by the events before they touch anything.
     */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
