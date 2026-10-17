package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
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
 */
public class ChakraFistAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** C C - one key past chakra charge, the stance it builds on. */
    public static final long COMBO = 11L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_fist");

    /** Mining speed multiplier while the stance is up. */
    public static final float BREAK_SPEED_MULTIPLIER = 2.5f;

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
        if (ninjaData.getChakra() < DRAIN_PER_TICK) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(DRAIN_PER_TICK, REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // All of the work happens in the break and hit events, there is nothing to do per tick.
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
