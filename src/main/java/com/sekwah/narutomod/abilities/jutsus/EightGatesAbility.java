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
 * built for it: the gates grind the caster down the whole time they are open, and the higher the
 * gate the faster it goes, to the point where the last few are a losing race against your own
 * health bar.</p>
 *
 * <p>Casting once more at the eighth gate slams them all shut again, which is the only way to stop
 * the drain short of dying. The effects are kept alive from {@code PlayerEvents}, not here, since
 * they have to be refreshed every tick while the gates are open.</p>
 */
public class EightGatesAbility extends Ability {

    /** V B B B */
    public static final long COMBO = 2333L;

    public static final int MAX_GATES = 8;

    /** Chakra and stamina each gate costs to force open. */
    private static final float CHAKRA_PER_GATE = 25.0f;
    private static final float STAMINA_PER_GATE = 15.0f;

    /** How often, in ticks, the open gates take their toll on the caster. */
    private static final int DRAIN_INTERVAL = 40;

    /** Health lost per open gate each time the drain comes round. */
    private static final float DRAIN_PER_GATE = 0.5f;

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
        // At the eighth gate the next cast is the one that shuts them, and that is free.
        if (ninjaData.getGateCount() >= MAX_GATES) {
            return true;
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

        if (ninjaData.getGateCount() >= MAX_GATES) {
            close(player, ninjaData);
            level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                    player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                    30, 0.5d, 0.7d, 0.5d, 0.02d);
            level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
            player.displayClientMessage(Component.translatable("jutsu.eight_gates.close")
                    .withStyle(ChatFormatting.GRAY), true);
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
            player.hurt(player.damageSources().magic(), DRAIN_PER_GATE * gates);
        }
    }

    /**
     * Shuts every gate: no effects, no drain. Used both by the caster and by death.
     */
    public static void close(Player player, INinjaData ninjaData) {
        ninjaData.setGateCount(0);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
    }
}
