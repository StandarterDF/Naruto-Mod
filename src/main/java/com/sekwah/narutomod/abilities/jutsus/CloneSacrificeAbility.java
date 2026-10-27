package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The substitution of a life: a copy stands in for the caster when the blow that would have killed
 * them lands.
 *
 * <p>The technique is not a shield and not a heal. It is a body double held ready, and it is spent
 * the way a substitution is spent - at the last moment, on a hit that would otherwise have ended the
 * fight. The copy pops, the caster is left standing on the edge of death, and if there is no copy
 * left in reach then there is nothing between the blow and the caster.</p>
 *
 * <p>The price has two parts on purpose. Opening the seal is expensive because the stance is only
 * worth holding when a fight is about to go badly, and the upkeep is trivial so the cost of a long
 * hold is the chakra it keeps out of everything else. Nothing here decides what counts as lethal -
 * that is the damage hook in {@code SummonMedicineEvents}, which reads {@link #SACRIFICE_UNTIL_KEY}.</p>
 */
public class CloneSacrificeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** V C V B - the clone seal with the last key turned. */
    public static final long COMBO = 1223L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clone_sacrifice");

    /**
     * The mark that says a copy is standing between the caster and death. Kept public because the
     * lethal-hit hook in {@code SummonMedicineEvents} lives on the other side of the package.
     */
    public static final String SACRIFICE_UNTIL_KEY = "narutomod:clone_sacrifice_until";

    /** Set while the stance is armed, so the opening price is only ever paid once per hold. */
    private static final String ARMED_KEY = "narutomod:clone_sacrifice_armed";

    /** Game time the current hold was opened at, used for the cap. */
    private static final String STARTED_KEY = "narutomod:clone_sacrifice_started";

    /** Chakra to arm the seal. */
    private static final float OPENING_COST = 30.0f;

    /** Chakra burnt for every tick it stays armed. */
    private static final float UPKEEP_COST = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How long one arming may last before it has to be paid for again. */
    private static final int MAX_TICKS = 600;

    /**
     * How far past the last upkeep tick the mark reaches. Long enough to cover the gap between the
     * upkeep that set it and the blow that lands, short enough that it cannot outlive the stance.
     */
    private static final int MARK_HOLD_TICKS = 40;

    @Override
    public int noiseRadius() {
        return 0;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The opening price is charged on the first upkeep tick rather than at activation, so the two
     * numbers cannot drift apart: the stance either pays to arm itself or is switched straight back
     * off by the toggle handler.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        boolean opening = !player.getPersistentData().getBoolean(ARMED_KEY);
        float cost = this.masteryCost(ninjaData, opening ? OPENING_COST : UPKEEP_COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        if (!data.getBoolean(ARMED_KEY)) {
            data.putBoolean(ARMED_KEY, true);
            data.putLong(STARTED_KEY, now);
        }
        if (now - data.getLong(STARTED_KEY) >= MAX_TICKS) {
            // The stance has run its course: hand it back to the toggle handler so the menu and the
            // chat agree that it is down, instead of leaving a mark nobody is paying for.
            this.end(player, ninjaData);
            return;
        }

        data.putLong(SACRIFICE_UNTIL_KEY, now + MARK_HOLD_TICKS);
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    /** A stance is mastered by standing in it, not by switching it on. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    private void end(Player player, INinjaData ninjaData) {
        this.clear(player);
        ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, this);
    }

    /** Drops the arming marks. Called both by the cap and by anything else that closes the toggle. */
    private void clear(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(ARMED_KEY);
        data.remove(STARTED_KEY);
        data.remove(SACRIFICE_UNTIL_KEY);
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The stance has nothing to show on the client; the swap particles come from the server.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        this.clear(player);
    }
}
