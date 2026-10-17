package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The eight inner gates, opened one at a time.
 *
 * <p>Every cast opens the next gate, and by the time all eight are open the caster is fast, strong
 * and hard to hurt in a way nothing else in the mod can match. The catch is that the body is not
 * built for it: the gates grind the caster down the whole time they are open, and the drain grows
 * with the square of how many are open, so the first few gates are something a caster can fight
 * through and the last few are a countdown.</p>
 *
 * <p><b>The eighth gate cannot be closed.</b> Up to the seventh the caster can simply stop, but once
 * the last gate is open no cast shuts it again and eight gates drain a full health bar in a couple of
 * seconds. Death is the only way out, which is the point: the eighth gate is meant to be spent, not
 * used. The gates fall away with death and are gone when the caster comes back.</p>
 *
 * <p>The effects are kept alive from {@code PlayerEvents}, not here, since they have to be refreshed
 * every tick while the gates are open.</p>
 */
public class EightGatesAbility extends Ability {

    /** V B B B */
    public static final long COMBO = 2333L;

    public static final int MAX_GATES = 8;

    /** Chakra and stamina each gate costs to force open. */
    private static final float CHAKRA_PER_GATE = 25.0f;
    private static final float STAMINA_PER_GATE = 15.0f;

    /** How often, in ticks, the open gates take their toll on the caster. */
    private static final int DRAIN_INTERVAL = 20;

    /**
     * Health lost each tick of drain, multiplied by the square of the open gates. Squared on purpose:
     * one gate is a trickle (0.15 a second), four is survivable for a while (2.4), six drains a full
     * bar in under four seconds and the eighth leaves the caster roughly two seconds to live.
     */
    private static final float DRAIN_COEFFICIENT = 0.15f;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // There is no way back from the eighth gate: the cast that would close them fails instead.
        if (ninjaData.getGateCount() >= MAX_GATES) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gates_open",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getChakra() < CHAKRA_PER_GATE || ninjaData.getStamina() < STAMINA_PER_GATE) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_PER_GATE, 20 * 3);
        ninjaData.useStamina(STAMINA_PER_GATE, 20 * 3);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        int gates = ninjaData.getGateCount() + 1;
        ninjaData.setGateCount(gates);

        // A burst coloured by how far the caster has pushed it.
        level.sendParticles(ParticleTypes.FLAME, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                10 + gates * 6, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                0.7f, 0.6f + gates * 0.05f);
        player.displayClientMessage(Component.translatable("jutsu.eight_gates.open",
                Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Keeps the effects of however many gates are open running, and takes the health they cost.
     * Called every tick from {@code PlayerEvents} for every player, so it has to be cheap when there
     * is nothing to do.
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates <= 0) {
            return;
        }

        int shortDuration = DRAIN_INTERVAL + 20;
        // Speed and strength rise with every gate; the defensive ones only every other gate, so the
        // last few gates are about hitting harder rather than becoming untouchable.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, shortDuration, gates - 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, shortDuration, (gates - 1) / 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, shortDuration, (gates - 1) / 3, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, shortDuration, Math.min(gates - 1, 3), true, false));

        if (player.tickCount % DRAIN_INTERVAL == 0) {
            player.hurt(player.damageSources().magic(), DRAIN_COEFFICIENT * gates * gates);
        }
    }

    /**
     * Shuts every gate: no effects, no drain. Only death gets to do this now, the caster cannot.
     */
    public static void close(Player player, INinjaData ninjaData) {
        ninjaData.setGateCount(0);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
    }
}
