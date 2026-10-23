package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientCameraHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client which body to hold the camera on, or {@link #SELF} to come back home.
 *
 * <p>The server decides; the client only obeys, and only ever receives an entity id it has already
 * been allowed to see. The id is applied on the next client tick, because the entity may arrive a
 * moment after this packet does.</p>
 */
public class ClientboundCameraPacket {

    /** The caster's own body: put the viewpoint back where it belongs. */
    public static final int SELF = -1;

    private final int entityId;

    public ClientboundCameraPacket(int entityId) {
        this.entityId = entityId;
    }

    public int entityId() {
        return this.entityId;
    }

    public static void encode(ClientboundCameraPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.entityId + 1);
    }

    public static ClientboundCameraPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundCameraPacket(buffer.readVarInt() - 1);
    }

    public static class Handler {
        public static void handle(ClientboundCameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientCameraHandler.watch(msg.entityId())));
            context.setPacketHandled(true);
        }
    }
}
