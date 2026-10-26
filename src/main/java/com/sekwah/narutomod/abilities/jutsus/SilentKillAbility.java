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
 * A short window of walking without sound and striking without warning.
 *
 * <p>A body can be taught to put its weight down in an order that makes no noise and leaves no
 * vibration for a sculk sensor to pick up, and a blow landed on a back that has not turned yet
 * carries more than the arm alone. Both of those are consequences of the mark this stance leaves,
 * not of the stance itself: the events that watch footsteps and hits read
 * {@link #UNTIL_TAG} and decide, so the technique stays a small class with no listeners of its own.</p>
 *
 * <p>It is a stance only in the sense that it is switched on and stays for its ten seconds; there is
 * no upkeep to pay while it runs, so the cost is the door price and the floor is the clock. When the
 * window closes the stance ends itself, rather than waiting for the player to remember it.</p>
 */
public class SilentKillAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The mark the parent's step and backstab hooks read: the tick the silence runs out. */
    public static final String UNTIL_TAG = "narutomod:silent_kill_until";

    /** B C B A A B - the taijutsu branch's stealth seal. */
    public static final long COMBO = 132112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "silent_kill");

    /** Paid once, when the stance is entered. Nothing is charged per tick. */
    private static final float COST = 15.0f;
    private static final int STAMINA_REGEN_DELAY = 20;

    /** How long the silence lasts: ten seconds, then the stance closes itself. */
    private static final int DURATION = 200;

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
     * Opens the window and keeps it open for free. The deadline is stamped once per activation rather
     * than refreshed every tick: a mark that is pushed forward each tick would never run out, which
     * would turn a ten-second stance into a permanent one. Once the deadline has passed the stance
     * is refused here, which is how the toggle loop learns to close it.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        long until = data.getLong(UNTIL_TAG);

        if (until > now) {
            return true;
        }
        if (until != 0L) {
            // The window ran out: drop the stale mark and let the toggle loop end the stance.
            data.remove(UNTIL_TAG);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        data.putLong(UNTIL_TAG, now + DURATION);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // The silence is lived in, so the stance is mastered by the time it is up.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    /** A stance is mastered by being held, not by switching it on. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The mark is server side; the client has nothing to draw for a silenced footstep.
    }

    /** Switching off, running out and dying all end the silence at the same moment. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
