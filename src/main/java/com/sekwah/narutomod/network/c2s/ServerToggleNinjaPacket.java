package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.item.NinjaHandbook;
import com.sekwah.narutomod.ranks.NinjaRanks;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the server that the user is entering jutsu keys
 */
public class ServerToggleNinjaPacket {

    private final boolean enableNinja;

    public ServerToggleNinjaPacket(boolean jutsuKey) {
        this.enableNinja = jutsuKey;
    }

    public static void encode(ServerToggleNinjaPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeBoolean(msg.enableNinja);
    }

    public static ServerToggleNinjaPacket decode(FriendlyByteBuf inBuffer) {
        boolean enableNinja = inBuffer.readBoolean();
        return new ServerToggleNinjaPacket(enableNinja);
    }

    public static class Handler {
        public static void handle(ServerToggleNinjaPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if(player == null || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                   boolean wasNinja = ninjaData.isNinjaModeEnabled();
                   ninjaData.setIsNinja(msg.enableNinja);
                   // Ninja mode can be turned off and on again, so "became a ninja just now" is not
                   // the same as "never was one": without a saved flag every toggle would hand out
                   // another handbook, which was a free book printer. The flag rides in the player
                   // data, so a quick double click that sends two packets in one tick also only pays
                   // once - the second packet sees it already set.
                   if (!wasNinja && msg.enableNinja && !ninjaData.isHandbookGiven()) {
                       ninjaData.setHandbookGiven(true);
                       NinjaRanks.onAwakened(player, ninjaData);
                       ItemStack handbook = NinjaHandbook.create();
                       if (!player.getInventory().add(handbook)) {
                           player.drop(handbook, false);
                       }
                   }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
