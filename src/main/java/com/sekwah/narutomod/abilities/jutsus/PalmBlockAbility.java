package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The open-palm guard every taijutsu school teaches before it teaches anything that hits back.
 *
 * <p>There is nothing to parry with, so the body is taught to meet the blow instead: a palm takes
 * the force, the arm gives with it and the striker's own momentum is turned into an opening. Half of
 * the blows that land are caught that way, and each one that is caught is paid for out of the
 * caster's stamina and answered with a short palm of their own.</p>
 *
 * <p>The hands have to stay empty. A sword or a tool in either hand means the guard is not the palm
 * guard any more, and the stance drops on the next tick rather than pretending. The rolls and the
 * answer live in {@code KenjutsuTaijutsuEvents}; this class only holds the mark and pays the
 * upkeep.</p>
 */
public class PalmBlockAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A guard is held in silence; nothing about the stance itself is heard. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 3 2 1 - the taijutsu branch's palm seal. */
    public static final long COMBO = 122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "palm_block");

    /** The mark the hit hook reads: kept a few ticks ahead while the guard is up. */
    public static final String UNTIL_TAG = "narutomod:palm_block_until";

    /** The trickle paid for every tick the palms are up. */
    private static final float UPKEEP = 0.15f;

    private static final int STAMINA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept, so a blow landing just as the guard drops still finds it. */
    private static final int MARK_GRACE = 5;

    /** Half of the melee blows that land are caught, at this price in stamina each. */
    public static final float BLOCK_CHANCE = 0.5f;
    public static final float BLOCK_STAMINA = 5.0f;

    /** What the palm gives back to whoever threw the blow. */
    public static final float COUNTER_DAMAGE = 3.0f;

    /** The regen delay a caught blow puts on the stamina bar. */
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
        if (!hasEmptyHands(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.palm_block.weapon",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, UPKEEP);
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

    /** Dropping the palms takes the guard with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** @return whether the palms are up right now, asked by the hit hook. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the caster's hands are both empty, which the guard needs. */
    public static boolean hasEmptyHands(Player player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }
}
