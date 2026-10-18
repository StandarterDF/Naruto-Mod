package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientOreSenseData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hands the caster of an ore sense pulse the blocks the server found, and what each one is.
 *
 * <p>The client cannot scan for this itself - the blocks may be in chunks it has not loaded - so the
 * search happens on the server and only the result crosses over. Each entry is a position and a kind;
 * the client owns how long the picture lasts and how it fades. An empty list is never sent by a
 * successful cast, but decoding one is harmless.</p>
 */
public class ClientboundOreSensePacket {

    public static final byte KIND_ORE = 0;
    public static final byte KIND_ANCIENT = 1;
    public static final byte KIND_CHEST = 2;
    public static final byte KIND_SPAWNER = 3;

    private final List<Marker> markers;

    public ClientboundOreSensePacket(List<Marker> markers) {
        this.markers = markers;
    }

    public List<Marker> markers() {
        return this.markers;
    }

    public static void encode(ClientboundOreSensePacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.markers.size());
        for (Marker marker : msg.markers) {
            buffer.writeBlockPos(marker.pos());
            buffer.writeByte(marker.kind());
        }
    }

    public static ClientboundOreSensePacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Marker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            markers.add(new Marker(buffer.readBlockPos(), buffer.readByte()));
        }
        return new ClientboundOreSensePacket(markers);
    }

    public static class Handler {
        public static void handle(ClientboundOreSensePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientOreSenseData.apply(msg.markers)));
            context.setPacketHandled(true);
        }
    }

    /** One block the pulse found, and which colour it should be drawn in. */
    public record Marker(BlockPos pos, byte kind) {
    }
}
