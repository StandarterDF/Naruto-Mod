package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the server which slice of the supply chest the caster has scrolled to.
 *
 * <p>The window's slots cannot be moved or repointed, so both ends keep a fixed set of slots and only
 * change what they look at. If the server did not follow the client's scroll it would think a click on
 * window slot {@code i} meant real slot {@code i} while the client meant {@code offset + i}, and items
 * would jump around. The menu clamps the row it is given, so a wayward client cannot reach outside the
 * chest.</p>
 */
public class ServerSupplyChestScrollPacket {

    private final int containerId;
    private final int scrollRow;

    public ServerSupplyChestScrollPacket(int containerId, int scrollRow) {
        this.containerId = containerId;
        this.scrollRow = scrollRow;
    }

    public static void encode(ServerSupplyChestScrollPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.containerId);
        buffer.writeVarInt(msg.scrollRow);
    }

    public static ServerSupplyChestScrollPacket decode(FriendlyByteBuf buffer) {
        return new ServerSupplyChestScrollPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static class Handler {
        public static void handle(ServerSupplyChestScrollPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.containerMenu instanceof SupplyChestMenu menu
                        && menu.containerId == msg.containerId) {
                    menu.setScrollRow(msg.scrollRow);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
