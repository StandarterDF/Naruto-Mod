package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.item.SakeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The unreadable drunkard's walk: a body that weaves when it should be hit and misses when it
 * should not.
 *
 * <p>There is no trick to it beyond refusing to move like a fighter. The weight is always a little
 * off centre and the steps never quite go where the eyes say they will, so an enemy's timing comes
 * apart: one blow in four goes past a body that is no longer there, and one in seven of the caster's
 * own wild swings catches nothing at all. Those odds are rolled where the hits are, see
 * {@link #UNTIL_TAG}, because an ability cannot see them.</p>
 *
 * <p>The stance keeps the caster a shade faster - a drunkard who is somehow always ahead of the
 * blow - and pays for it out of stamina every tick, so it is something to hold for a brawl rather
 * than something to leave on all day. It can only be entered after a drink: a bottle of sake opens
 * the window the stance reads, and while the stance is up the world swims with nausea.</p>
 */
public class DrunkenFistAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The mark the parent's dodge and miss hooks read: kept a few ticks ahead while the stance is up. */
    public static final String UNTIL_TAG = "narutomod:drunken_fist_until";

    /** B C B C A A - the taijutsu branch's sway seal. */
    public static final long COMBO = 112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "drunken_fist");

    /** Stamina paid for each tick of the stance. */
    private static final float COST_PER_TICK = 0.15f;
    private static final int STAMINA_REGEN_DELAY = 10;

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("8d1f4a63-2c95-4b7e-9f08-1a6d3e5c7b24");

    /** A sway that keeps the caster a step ahead: a twentieth of a block per tick. */
    private static final double SPEED_BONUS = 0.05d;

    /**
     * How far ahead the mark is kept while the stance is held. It only has to outlive the tick it was
     * written on, but a small grace means a hit resolved a moment after the stance drops still counts.
     */
    private static final int MARK_GRACE = 5;

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
     * The style is lived, not studied: a caster has to have had a drink recently. A bottle of sake
     * writes the window the stance reads, and the window is checked before the toggle joins the set
     * so a sober press costs nothing.
     */
    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        if (!isDrunk(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.drunken_fist.sober",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** @return whether the drinker is still inside the window a bottle of sake opened. */
    public static boolean isDrunk(Player player) {
        return player.getPersistentData().getLong(SakeItem.DRUNK_TAG) > player.level().getGameTime();
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!player.hasEffect(MobEffects.CONFUSION)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.drunken_fist.sober",
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
        applySpeed(player);
        // The world swims while the stance is held: the unsteadiness is the style, not a side effect.
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, true, false));
        player.getPersistentData().putLong(UNTIL_TAG,
                player.level().getGameTime() + MARK_GRACE);
    }

    /** Re-applied every tick so a lost modifier (a reload, a respawn) comes back with the stance. */
    private static void applySpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "narutomod:drunken_fist",
                    SPEED_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /** The sway is mastered by holding it, so the tick credit is paid from {@link #performServer}. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attribute is synced from the server; there is nothing to sway on the client.
    }

    /** Dropping the stance takes the speed and the mark with it, so the next blow is an honest one. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        CompoundTag data = player.getPersistentData();
        data.remove(UNTIL_TAG);
        player.removeEffect(MobEffects.CONFUSION);
    }

    /** Whether the drunkard's walk is currently up, asked by anything that leans on the stance. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
