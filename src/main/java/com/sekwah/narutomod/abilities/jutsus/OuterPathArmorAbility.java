package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The outer path worn as a shell: the caster stops being a body and becomes a thing that has to be
 * broken rather than cut.
 *
 * <p>The shell is armour and weight at the same time. It turns a share of every blow aside, makes
 * the caster impossible to shove around, and pays for both with speed - a caster in the shell
 * walks like a much heavier person, which is the cost of standing in the open and not caring.</p>
 *
 * <p>Both halves are attribute modifiers with stable ids, so nothing stacks if the stance is
 * switched on twice and nothing is left behind if it ends badly. The reduction and the immunity are
 * read from the mark rather than from the toggle set, so they cannot outlive the shell on a body
 * that lost its capability for a moment.</p>
 */
public class OuterPathArmorAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A shell makes no noise of its own; the blows it takes do. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 2 3 3 3 1 3 - the last key of the outer run. */
    public static final long COMBO = 233313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "outer_armor");

    /** What the shell costs for every tick it is worn. */
    private static final float COST_PER_TICK = 0.4f;

    /** Ticks before the caster's own regeneration resumes after paying for the shell. */
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** The armour the shell adds to the body, in armour points. */
    private static final double ARMOUR_BONUS = 6.0d;

    /** The share of the caster's speed the shell takes away. */
    private static final double SPEED_PENALTY = -0.10d;

    /** The share of an incoming blow that gets through the shell. */
    public static final float DAMAGE_MULTIPLIER = 0.8f;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on; if the
     * stance stops for any reason the mark runs out within a moment and the hooks stop trusting it.
     */
    public static final int MARK_TICKS = 5;

    /** The mark read by {@code RinneganExtraEvents} for the reduction and the knockback immunity. */
    public static final String UNTIL_TAG = "narutomod:outer_armor_until";

    /** Stable ids, so the same two modifiers are added and taken off rather than piling up. */
    private static final UUID ARMOUR_MODIFIER_ID = UUID.fromString("4b7c2a18-6d3e-4f59-8a1c-2e5d9b0f7c31");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("c2e8f4a6-1b7d-4c90-b3e5-8f6a0d2c9b47");

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A stance is mastered by the time it is worn, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        applyModifiers(player);
        player.getPersistentData().putLong(UNTIL_TAG,
                player.level().getGameTime() + MARK_TICKS);
    }

    /** Re-applied every tick so a modifier lost to a reload or a respawn comes back with the shell. */
    private static void applyModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null && armour.getModifier(ARMOUR_MODIFIER_ID) == null) {
            armour.addTransientModifier(new AttributeModifier(ARMOUR_MODIFIER_ID, "narutomod:outer_armor",
                    ARMOUR_BONUS, AttributeModifier.Operation.ADDITION));
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "narutomod:outer_armor_speed",
                    SPEED_PENALTY, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * Takes the shell off. Public because the mark-running event also has to do it when the mark
     * outlives the stance for any reason.
     */
    public static void removeModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(ARMOUR_MODIFIER_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /**
     * A stance is mastered by the time it is up, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attributes and the mark are synced from the server; the client has nothing to do.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        removeModifiers(player);
    }
}
