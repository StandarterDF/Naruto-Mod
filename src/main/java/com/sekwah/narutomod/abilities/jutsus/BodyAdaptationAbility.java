package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The body quietly rewrites itself to fit wherever the caster is standing.
 *
 * <p>Under water it takes the water it needs and stops needing air; in lava it stops burning; in
 * powder snow it will not freeze. That is all it does - no armour, no strength, no speed. It is a
 * survival stance, not a combat one, and it is deliberately not the sage mode: sage mode is about
 * being more than human for a while, this is about being able to stay somewhere a human cannot.</p>
 *
 * <p>The price follows the environment rather than the clock: a trickle while the stance is up, and
 * roughly two and a half times that while it is holding the caster inside lava. The effects are given
 * short, refreshed durations, so when the stance is dropped they run out on their own within a
 * couple of seconds instead of having to be stripped off by hand.</p>
 */
public class BodyAdaptationAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** B V V. */
    public static final long COMBO = 233L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "body_adaptation");

    /** Chakra paid per tick while the stance is up, and while it is holding the caster in lava. */
    private static final float BASE_COST = 0.2f;
    private static final float LAVA_COST = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How long each gift lasts. It is renewed every tick, so this is only how long it lingers after
     * the stance ends - long enough not to cut off a breath mid-swim, short enough to be gone by the
     * time the caster has moved on.
     */
    private static final int EFFECT_DURATION = 60;

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
        if (!NarutoConfig.adaptationEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.adaptation.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, player.isInLava() ? LAVA_COST : BASE_COST);
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
        // Mastery is paid for the time the body is actually adapted, not for switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,
                    EFFECT_DURATION, 0, true, false, false));
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (player.isInLava() || player.isOnFire() || player.getRemainingFireTicks() > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                    EFFECT_DURATION, 0, true, false, false));
            player.setRemainingFireTicks(0);
        }
        if (player.isInPowderSnow || player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
        }
    }

    /**
     * A stance is mastered by the time it is up, not by switching it on, so the credit is paid per
     * active tick from {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Whether the body is currently adapted to its surroundings, asked by the events that lean on
     * it - eating something the stomach would rather not digest, for now.
     */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The effects are given by the server and reach the client through the usual effect sync.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // The effects were given short durations and expire on their own.
    }
}
