package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientFriendsData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A short message about a friendship: an incoming request (which the client shows as a toast so it
 * is noticed even when the menu is closed), or an answer to one.
 *
 * <p>The chat lines live here rather than on the server so they are translated on the receiving
 * machine. The friends list itself travels separately in {@link ClientboundFriendsPacket}.</p>
 */
public class ClientboundFriendNoticePacket {

    public static final byte REQUEST = 0;
    public static final byte ACCEPTED = 1;
    public static final byte DECLINED = 2;
    public static final byte REMOVED = 3;
    public static final byte CANCELLED = 4;

    private final byte type;
    private final String name;

    public ClientboundFriendNoticePacket(byte type, String name) {
        this.type = type;
        this.name = name;
    }

    public static ClientboundFriendNoticePacket request(String name) {
        return new ClientboundFriendNoticePacket(REQUEST, name);
    }

    public byte type() {
        return this.type;
    }

    public String name() {
        return this.name;
    }

    public static void encode(ClientboundFriendNoticePacket msg, FriendlyByteBuf buffer) {
        buffer.writeByte(msg.type);
        buffer.writeUtf(msg.name);
    }

    public static ClientboundFriendNoticePacket decode(FriendlyByteBuf buffer) {
        return new ClientboundFriendNoticePacket(buffer.readByte(), buffer.readUtf());
    }

    public static class Handler {
        public static void handle(ClientboundFriendNoticePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientFriendsData.notice(msg.type, msg.name)));
            context.setPacketHandled(true);
        }
    }
}
