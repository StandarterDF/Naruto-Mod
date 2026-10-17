package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
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
 * Sage mode: the gathered nature energy is opened up and burned, turning the caster into something
 * faster, stronger and harder to put down for as long as it lasts.
 *
 * <p>The mode is not on a timer, it is on the pool. Every tick it takes nature energy, and when the
 * pool is empty the mode closes on its own - so how long a sage lasts is decided entirely by how long
 * they stood still beforehand, which is the trade the whole branch of techniques is built on. Death
 * and logging out also end it: the state is deliberately kept out of the save file.</p>
 *
 * <p>The maintenance lives in {@link #maintain(Player, INinjaData)}, called every tick from
 * {@code PlayerEvents#onEntityUpdate}, in the same way the eight gates are kept alive.</p>
 */
public class SageModeAbility extends Ability {

    /** V B V */
    public static final long COMBO = 232L;

    /** Nature energy needed to open the mode at all. */
    public static final float MIN_SENIN = 50.0f;

    /** Nature energy burned every tick the mode is up (100 energy is twenty seconds). */
    private static final float DRAIN_PER_TICK = 0.25f;

    /** Extra chakra the mode feeds its caster every tick. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    /** How long the effects are handed out for; refreshed every tick, so this is just a hold time. */
    private static final int EFFECT_DURATION = 40;

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
        if (!NarutoConfig.sageModeEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sage.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ninjaData.isSageMode()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sage.active",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ninjaData.getSeninMana() < MIN_SENIN) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senin.notenough",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW),
                    (int) MIN_SENIN).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        ninjaData.setSageMode(true);
        player.displayClientMessage(Component.translatable("jutsu.sage.start").withStyle(ChatFormatting.GREEN), true);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    /**
     * Keeps a sage a sage: drained, fed, buffed and lit up, every server tick.
     *
     * <p>The effects are handed out with a short duration and refreshed rather than given one long
     * duration, so the moment the mode ends the buffs go with it instead of hanging around.</p>
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        if (!ninjaData.isSageMode()) {
            return;
        }
        ninjaData.addSeninMana(-DRAIN_PER_TICK);
        if (ninjaData.getSeninMana() <= 0.0f) {
            ninjaData.setSeninMana(0.0f);
            ninjaData.setSageMode(false);
            player.displayClientMessage(Component.translatable("jutsu.sage.end").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        ninjaData.addChakra(CHAKRA_PER_TICK);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_DURATION, 0, true, false));

        if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                    player.getY() + 1.0d, player.getZ(), 4, 0.4d, 0.7d, 0.4d, 0.02d);
        }
    }
}
