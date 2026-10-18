package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientSensingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hands the caster of a held chakra sense the structures their sense has found.
 *
 * <p>The client cannot work this out for itself: structure starts are world generation bookkeeping
 * the server keeps to itself, so the search has to be done where the chunks are and the results sent
 * over. Each entry is only where a structure is and what it is - the client draws the marker, see
 * {@code SensingGUI}. An empty packet is sent when the sense ends so the markers are dropped at
 * once rather than hanging around until the caster moves.</p>
 */
public class ClientboundSensingPacket {

    private final List<Marker> markers;

    public ClientboundSensingPacket(List<Marker> markers) {
        this.markers = markers;
    }

    public static ClientboundSensingPacket empty() {
        return new ClientboundSensingPacket(List.of());
    }

    public static void encode(ClientboundSensingPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.markers.size());
        for (Marker marker : msg.markers) {
            buffer.writeResourceLocation(marker.structure());
            buffer.writeBlockPos(marker.pos());
        }
    }

    public static ClientboundSensingPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Marker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            markers.add(new Marker(buffer.readResourceLocation(), buffer.readBlockPos()));
        }
        return new ClientboundSensingPacket(markers);
    }

    public static class Handler {
        public static void handle(ClientboundSensingPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientSensingData.applyMarkers(msg.markers)));
            context.setPacketHandled(true);
        }
    }

    /** One structure the sense picked up: which structure it is and roughly where. */
    public record Marker(ResourceLocation structure, BlockPos pos) {
    }
}
