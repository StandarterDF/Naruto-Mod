package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the server that the user is entering jutsu keys
 */
public class ServerJutsuCastingPacket {

    /**
     * How far a hand seal carries. Vanilla broadcasts a world sound to everyone within sixteen
     * blocks; the seals keep that range, but only reach people who are inside the system.
     */
    private static final double SEAL_HEARING_RANGE = 16.0;

    private final int jutsuKey;

    public ServerJutsuCastingPacket(int jutsuKey) {
        this.jutsuKey = jutsuKey;
    }

    public static void encode(ServerJutsuCastingPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeInt(msg.jutsuKey);
    }

    public static ServerJutsuCastingPacket decode(FriendlyByteBuf inBuffer) {
        int jutsuKey = inBuffer.readInt();
        return new ServerJutsuCastingPacket(jutsuKey);
    }

    public static class Handler {
        public static void handle(ServerJutsuCastingPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if(player != null) {
                    player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                        if (!ninjaData.isNinjaModeEnabled()) {
                            player.displayClientMessage(Component.translatable("jutsu.not_a_ninja").withStyle(ChatFormatting.RED), true);
                            return;
                        }
                        if(player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                            return;
                        }
                        SoundEvent playSound = switch (msg.jutsuKey) {
                            case 1 -> NarutoSounds.SEAL_A.get();
                            case 2 -> NarutoSounds.SEAL_B.get();
                            case 3 -> NarutoSounds.SEAL_C.get();
                            default -> null;
                        };
                        if(playSound != null) {
                            playSeal(player, playSound);
                            player.level().gameEvent(player, NarutoGameEvents.JUTSU_CASTING.get(), player.position().add(0, player.getEyeHeight() * 0.7, 0));
                        }
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }

        /**
         * Hand seals are heard only by other ninjas. A world sound would reach every player in
         * range, and someone who has not become a ninja is not supposed to notice a shinobi's
         * signs at all. Sculk and the warden still get the game event, so the world itself keeps
         * reacting to the noise.
         */
        private static void playSeal(ServerPlayer caster, SoundEvent sound) {
            ClientboundSoundPacket packet = new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                    caster.getX(), caster.getY(), caster.getZ(), 1.0f, 1.0f,
                    caster.getRandom().nextLong());
            double range = SEAL_HEARING_RANGE * SEAL_HEARING_RANGE;
            for (ServerPlayer listener : caster.serverLevel().players()) {
                if (listener.distanceToSqr(caster) > range) {
                    continue;
                }
                boolean hears = listener.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                        .map(data -> data.isNinjaModeEnabled())
                        .orElse(false);
                if (hears) {
                    listener.connection.send(packet);
                }
            }
        }
    }
}
