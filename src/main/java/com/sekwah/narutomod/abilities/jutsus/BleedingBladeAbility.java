package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;

/**
 * The edge left in the wound: a cut that will not close on its own.
 *
 * <p>Most blows are paid for once. This one is paid for twice: the blade is held so that the cut it
 * leaves keeps a body open for five seconds after the sword has moved on, and a wound that is still
 * bleeding is a wound the caster can walk away from and let do the work. It is the cheapest way a
 * swordsman has of dealing with a body that will not stay down, and the reason a duel against one is
 * a matter of not being touched at all.</p>
 *
 * <p>The stance itself does nothing but leave a mark and a mark on the weapon arm; the wound it
 * leaves is the same {@code narutomod:bleed_until} the bone blade of the Kaguya leaves, and the
 * upkeep that bleeds it dry lives in {@code KenjutsuTaijutsuEvents} rather than here.</p>
 */
public class BleedingBladeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A blade doing nothing is silent; the wound it leaves is not this class's business. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 5 1 in the brief; 1 3 2 3 3 3 here, because a combo has only three keys. */
    public static final long COMBO = 132333L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "bleeding_blade");

    /** The mark the melee hook reads: kept a few ticks ahead while the stance is held. */
    public static final String UNTIL_TAG = "narutomod:bleeding_blade_until";

    /** How long a cut from this blade keeps bleeding, in ticks. */
    public static final int BLEED_TICKS = 100;

    /** Stamina paid for every tick the stance is up. */
    private static final float COST_PER_TICK = 0.1f;
    private static final int STAMINA_REGEN_DELAY = 10;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the stance drops still open a wound.
     */
    private static final int MARK_GRACE = 5;

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
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.bleeding_blade.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);
    }

    /** Opens the wound the sword just made, on the same mark the bone blade uses. */
    public static void onMeleeHit(Player caster, LivingEntity victim) {
        victim.getPersistentData().putLong(CamelliaDanceAbility.BLEED_TAG,
                caster.level().getGameTime() + BLEED_TICKS);
    }

    /** @return whether the stance is up right now, asked by the melee hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** The stance is mastered by holding it, so the tick credit is paid from {@link #performServer}. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Nothing about this stance is drawn on the client.
    }

    /** Sheathing the blade closes the stance; the wounds already open keep bleeding. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
