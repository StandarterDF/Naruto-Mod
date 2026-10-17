package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Standing still to let nature energy gather in the body, which is what sage mode runs on.
 *
 * <p>The technique is a channelled one, and the channelling is the technique: the caster has to hold
 * the keys and has to be genuinely still. Moving does not break the cast or waste anything, it simply
 * gathers nothing until they stop again, and the pool they have already gathered is kept - so a
 * half charged sage who has to run away has not lost the work, they just have to come back to it.</p>
 *
 * <p>The pool is written into the player's saved data, so logging out with a full set of nature
 * energy does not throw it away. Spending it is {@link SageModeAbility}'s business.</p>
 */
public class SenjutsuChargingAbility extends Ability implements Ability.Channeled {

    /** V B C */
    public static final long COMBO = 231L;

    /** Nature energy gathered per tick of perfect stillness. */
    private static final float CHARGE_PER_TICK = 0.5f;

    /**
     * How much drift still counts as standing still, as a squared horizontal speed. Vanilla players
     * always keep a sliver of momentum, so this is a tolerance rather than a strict zero.
     */
    private static final double STILLNESS_TOLERANCE = 0.0016d;

    /** How often the caster is told where the pool is, in ticks. */
    private static final int PROGRESS_INTERVAL = 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
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
        // Free: the price of nature energy is the time spent standing still.
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!isStill(player)) {
            if (ticksChanneled % PROGRESS_INTERVAL == 0) {
                player.displayClientMessage(Component.translatable("jutsu.senin.moving")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (ninjaData.getSeninMana() >= ninjaData.getMaxSeninMana()) {
            if (ticksChanneled % PROGRESS_INTERVAL == 0) {
                player.displayClientMessage(Component.translatable("jutsu.senin.full")
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return;
        }
        ninjaData.addSeninMana(CHARGE_PER_TICK);

        if (player.level() instanceof ServerLevel level) {
            // Nature energy crawling into the caster, so it is obvious the technique is working.
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                    player.getY() + 1.0d, player.getZ(), 4, 0.4d, 0.7d, 0.4d, 0.02d);
        }

        if (ticksChanneled % PROGRESS_INTERVAL == 0) {
            player.displayClientMessage(Component.translatable("jutsu.senin.progress",
                            (int) ninjaData.getSeninMana(), (int) ninjaData.getMaxSeninMana())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    /**
     * A tap is nothing: the technique has no instant form, and a message is the only honest answer.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive == -1) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senin.hold",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /** The progress read out is the message, the channel start/stop lines would just be noise. */
    @Override
    public boolean hideChannelMessages() {
        return true;
    }

    /** @return whether the caster is standing on the ground without walking or being pushed. */
    public static boolean isStill(Player player) {
        return player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < STILLNESS_TOLERANCE;
    }
}
