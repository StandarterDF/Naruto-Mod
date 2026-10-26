package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Wind Release footwork: the caster stops walking and starts being carried by the air.
 *
 * <p>It is not a dash and not a leap - those are single moves - but a way of moving that is kept up
 * for as long as the chakra lasts. The caster runs a quarter faster and steps up a full block without
 * jumping, which is what turns broken ground, fences and single-block walls into open road.</p>
 *
 * <p>What it costs is paid every tick and grows the longer it is held, so the stance is a decision
 * about distance rather than a buff to leave on. The wind also takes its price out of the body: the
 * parent hooks make every blow weaker and the caster hungrier while the stance is up, see
 * {@link #isActive(INinjaData)}.</p>
 *
 * <p>Both gifts ride on attributes rather than on effects because both have to reach the client that
 * actually moves the player, and attributes are the only movement figures the server syncs. The step
 * height in particular goes on Forge's step attribute on top of vanilla's 0.6, which is what makes a
 * full block walkable.</p>
 */
public class WindStepAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** C C A A - the wind branch's footwork seal. */
    public static final long COMBO = 3311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "wind_step");

    /** Stable ids, so the same two modifiers are added and taken off rather than stacking. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("2b7c4f0a-9d3e-4c81-b0a5-5e7f1c3d9a42");
    private static final UUID STEP_MODIFIER_ID = UUID.fromString("c4e1a9b2-6f7d-4e58-9a3c-1b0d5f8e2c67");

    /** Chakra per tick while the stance is held. */
    private static final float COST_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** A quarter again as fast, and half a block more on top of vanilla's 0.6 step. */
    private static final double SPEED_BONUS = 0.25d;
    private static final double STEP_BONUS = 0.4d;

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

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        apply(player);
    }

    /** Re-applied every tick so a lost modifier (a reload, a respawn) comes back with the stance. */
    private static void apply(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "narutomod:wind_step",
                    SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step != null && step.getModifier(STEP_MODIFIER_ID) == null) {
            step.addTransientModifier(new AttributeModifier(STEP_MODIFIER_ID, "narutomod:wind_step_step",
                    STEP_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Whether the footwork is currently held, asked by the hooks that add its drawbacks. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
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
        // The attributes are synced from the server, so there is nothing to do on the client.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step != null) {
            step.removeModifier(STEP_MODIFIER_ID);
        }
    }
}
