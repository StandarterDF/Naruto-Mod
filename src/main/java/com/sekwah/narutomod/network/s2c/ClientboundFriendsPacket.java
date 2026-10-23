package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientFriendsData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The whole friends picture of one player: who they are friends with, who is waiting for an
 * answer, and who they themselves are waiting on. Sent after every change and on login, so the
 * friends screen never has to ask for a refresh.
 */
public class ClientboundFriendsPacket {

    private final List<Entry> friends;
    private final List<Entry> incoming;
    private final List<Entry> outgoing;

    public ClientboundFriendsPacket(List<Entry> friends, List<Entry> incoming, List<Entry> outgoing) {
        this.friends = friends;
        this.incoming = incoming;
        this.outgoing = outgoing;
    }

    public List<Entry> friends() {
        return this.friends;
    }

    public List<Entry> incoming() {
        return this.incoming;
    }

    public List<Entry> outgoing() {
        return this.outgoing;
    }

    public static void encode(ClientboundFriendsPacket msg, FriendlyByteBuf buffer) {
        writeEntries(buffer, msg.friends);
        writeEntries(buffer, msg.incoming);
        writeEntries(buffer, msg.outgoing);
    }

    public static ClientboundFriendsPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundFriendsPacket(readEntries(buffer), readEntries(buffer), readEntries(buffer));
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.id());
            buffer.writeUtf(entry.name());
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf()));
        }
        return entries;
    }

    public static class Handler {
        public static void handle(ClientboundFriendsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientFriendsData.apply(msg)));
            context.setPacketHandled(true);
        }
    }

    /** One line of the screen: who it is, and the last name the server knows them by. */
    public record Entry(UUID id, String name) {
    }
}
