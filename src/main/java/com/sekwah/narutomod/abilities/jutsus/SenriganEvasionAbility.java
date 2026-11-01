package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Senrigan Evasion: while the Senrigan is open, projectiles pass through the caster without harming
 * them, explosions are fully absorbed, and melee damage is halved. Falling and fire are not evaded at
 * all. Every hit that would have landed and every tick of the stance costs chakra; when the chakra
 * runs out the stance ends and the next hit lands in full.
 */
public class SenriganEvasionAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    public static final long COMBO = 12345L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "senrigan_evasion");

    /** Chakra burnt passively for every tick the stance is held. */
    private static final float PASSIVE_DRAIN = 0.5f;

    /** Chakra burnt for every hit that would have landed. */
    private static final float HIT_COST = 5.0f;

    private static final int CHAKRA_REGEN_DELAY = 10;

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
        if (!NarutoConfig.senriganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SenriganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, PASSIVE_DRAIN);
        if (ninjaData.getChakra() < cost) {
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!SenriganAbility.isActive(ninjaData)) {
            ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData, this);
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        float drain = this.masteryCost(ninjaData, PASSIVE_DRAIN);
        if (ninjaData.getChakra() < drain) {
            ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData, this);
            return;
        }
        ninjaData.useChakra(drain, CHAKRA_REGEN_DELAY);
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
    }

    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData != null && ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    public static float hitCost(INinjaData ninjaData) {
        SenriganEvasionAbility a = (SenriganEvasionAbility) NarutoRegistries.ABILITIES.getValue(ID);
        return a != null ? a.masteryCost(ninjaData, HIT_COST) : HIT_COST;
    }
}