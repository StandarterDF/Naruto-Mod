package com.sekwah.narutomod.network.c2s;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.events.ByakuganEvents;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Tell the server that the user wants to cast a specific ability.
 */
public class ServerAbilityChannelPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ResourceLocation abilityResource;
    private final ChannelStatus status;

    public ServerAbilityChannelPacket(ResourceLocation abilityResource, ChannelStatus status) {
        this.abilityResource = abilityResource;
        this.status = status;
    }

    public enum ChannelStatus {
        START,
        STOP,
        MIN_ACTIVATE
    }

    public static void encode(ServerAbilityChannelPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeResourceLocation(msg.abilityResource);
        outBuffer.writeInt(msg.status.ordinal());
    }

    public static ServerAbilityChannelPacket decode(FriendlyByteBuf inBuffer) {
        ResourceLocation abilityResource = inBuffer.readResourceLocation();
        int status = inBuffer.readInt();
        return new ServerAbilityChannelPacket(abilityResource, ChannelStatus.values()[status]);
    }

    public static class Handler {
        public static void handle(ServerAbilityChannelPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                final ServerPlayer player = ctx.get().getSender();
                if(player == null || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                    if(!ninjaData.isNinjaModeEnabled()) {
                        player.displayClientMessage(Component.translatable("jutsu.not_a_ninja").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    Ability ability = NarutoRegistries.ABILITIES.getValue(msg.abilityResource);
                    if(ability == null) {
                        LOGGER.error("Ability doesnt exist {}", msg.abilityResource);
                        return;
                    }
                    if (!AbilityUnlocks.isUnlocked(ninjaData, msg.abilityResource)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.notunlocked",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // Switched off in the jutsu menu, see DisabledAbilityData.
                    if (ninjaData.isAbilityDisabled(msg.abilityResource)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.techdisabled",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // A Byakugan palm has closed the caster's channels. A hold may still be let go,
                    // so a sealed caster never loses the technique they already had in hand.
                    if (ByakuganEvents.isSealed(player) && msg.status != ChannelStatus.STOP) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.tenketsu")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // The chakra cloak leaves no room to start a hold, but releasing one that is
                    // already running stays possible.
                    if (msg.status != ChannelStatus.STOP && !ChakraCloakAbility.leavesRoomFor(player, ability)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.chakra_cloak.locked",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // Just check if its
                    if (ability.activationType() == Ability.ActivationType.CHANNELED) {
                        if (msg.status == ChannelStatus.START) {
                            ninjaData.setCurrentlyChanneledAbility(player, ability);
                        } else if (msg.status == ChannelStatus.STOP) {
                            NarutoRegistries.ABILITIES.getResourceKey(ability).ifPresent(resourceKey -> {
                                // Compared the safe way round: a hold can end on the server before
                                // the keys come up (a thread that snapped, chakra that ran out), so
                                // there may be nothing channeled by the time the stop packet lands.
                                if(resourceKey.location().equals(ninjaData.getCurrentlyChanneledAbility())) {
                                    ability.performServer(player, ninjaData, ninjaData.getCurrentlyChanneledTicks());
                                    // A tap that never held the channel is not a cast worth mastering.
                                    if (ninjaData.getCurrentlyChanneledTicks() > 0) {
                                        ability.recordMastery(player, ninjaData);
                                    }
                                    ninjaData.setCurrentlyChanneledAbility(player, null);
                                }
                            });
                        } else if(msg.status == ChannelStatus.MIN_ACTIVATE) {
                            if (ability instanceof Ability.Channeled channeled && channeled.canActivateBelowMinCharge()) {
                                if(ability.handleCost(player, ninjaData, -1)) {
                                    if (ability.castingSound() != null) {
                                        player.level().playSound(null, player, ability.castingSound(), SoundSource.PLAYERS, 0.5f, 1.0f);
                                    }
                                    ability.emitNoise(player);
                                    player.displayClientMessage(Component.translatable("jutsu.cast", Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN), true);
                                    ability.performServer(player, ninjaData, -1);
                                    ability.recordMastery(player, ninjaData);
                                }
                            } else {
                                player.displayClientMessage(Component.translatable("jutsu.channel.needed", Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
                            }
                        }
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
