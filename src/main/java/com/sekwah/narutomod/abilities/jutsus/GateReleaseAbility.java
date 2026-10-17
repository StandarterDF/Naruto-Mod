package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Shuts the inner gates the caster forced open, and stops the drain with them.
 *
 * <p>This is the counterpart to {@link EightGatesAbility}: open a gate or two for the strength, then
 * seal them again before the price gets out of hand. It costs nothing but the chance to open them
 * again, which is the trade: opening was paid for, shutting them is free.</p>
 *
 * <p><b>The eighth gate cannot be sealed.</b> That is the whole point of it, so at eight gates this
 * technique fails the same way the opening one does, and the caster has to see it through.</p>
 */
public class GateReleaseAbility extends Ability {

    /** V B B V - the mirror of the technique that opens the gates (V B B B). */
    public static final long COMBO = 2332L;

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
        if (ninjaData.getGateCount() <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gates_none",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getGateCount() >= EightGatesAbility.MAX_GATES) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gates_open",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        int gates = ninjaData.getGateCount();
        EightGatesAbility.close(player, ninjaData);

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                20 + gates * 4, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
        player.displayClientMessage(Component.translatable("jutsu.eight_gates.close",
                Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY), true);
    }
}
