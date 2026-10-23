package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Switches one technique off, or back on, from the jutsu menu.
 *
 * <p>The menu only asks; the server decides. A technique that has not been learned yet cannot be
 * switched off (there is nothing to switch off), and switching one off while it is running ends it
 * rather than leaving a stance burning chakra in the background. The disabled set lives in the
 * player's data (see {@code DisabledAbilityData}), so both cast packets can refuse a technique the
 * menu has put aside.</p>
 */
public class ServerToggleAbilityDisabledPacket {

    private final ResourceLocation ability;
    private final boolean disabled;

    public ServerToggleAbilityDisabledPacket(ResourceLocation ability, boolean disabled) {
        this.ability = ability;
        this.disabled = disabled;
    }

    public static void encode(ServerToggleAbilityDisabledPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeResourceLocation(msg.ability);
        outBuffer.writeBoolean(msg.disabled);
    }

    public static ServerToggleAbilityDisabledPacket decode(FriendlyByteBuf inBuffer) {
        return new ServerToggleAbilityDisabledPacket(inBuffer.readResourceLocation(), inBuffer.readBoolean());
    }

    public static class Handler {
        public static void handle(ServerToggleAbilityDisabledPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                final ServerPlayer player = ctx.get().getSender();
                if (player == null || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                    Ability ability = NarutoRegistries.ABILITIES.getValue(msg.ability);
                    if (ability == null) {
                        return;
                    }
                    // Only techniques that stay on are controllable; a fireball is cast with its
                    // combo, not switched off from a list.
                    if (!ability.isMenuControllable()) {
                        return;
                    }
                    // Only learned techniques can be put aside: an unknown row is already unusable.
                    if (msg.disabled && !AbilityUnlocks.isUnlocked(ninjaData, msg.ability)) {
                        return;
                    }
                    boolean disabled = ninjaData.getDisabledAbilityData().setDisabled(msg.ability, msg.disabled);
                    if (disabled) {
                        stopRunning(player, ninjaData, ability, msg.ability);
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }

        /**
         * A technique that is switched off mid-cast should stop, not keep draining chakra from behind
         * a greyed-out row. Stances are ended through the normal path so their cleanup still runs.
         */
        private static void stopRunning(ServerPlayer player, INinjaData ninjaData, Ability ability,
                                        ResourceLocation abilityId) {
            if (ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(abilityId)) {
                ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, ability);
            }
            if (abilityId.equals(ninjaData.getCurrentlyChanneledAbility())) {
                ninjaData.setCurrentlyChanneledAbility(player, null);
            }
        }
    }
}
