package com.sekwah.narutomod.network.s2c;

import com.sekwah.narutomod.client.ClientSupplyChestSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Hands the supply chest's per-slot amounts to the caster who has it open.
 *
 * <p>Vanilla writes a stack's size into a single byte and only ships item tags for damageable items,
 * so a slot holding anything past one hundred and twenty-seven of an item - or any custom tag at all -
 * cannot ride along with the normal container update. The chest therefore keeps a count-one
 * <em>template</em> of each item (which vanilla syncs quite happily) and sends the real amounts over
 * here. The client draws the template's icon plus the amount it is told about.</p>
 */
public class ClientboundSupplyChestPacket {

    private final int containerId;
    private final int[] amounts;

    public ClientboundSupplyChestPacket(int containerId, int[] amounts) {
        this.containerId = containerId;
        this.amounts = amounts;
    }

    public static void encode(ClientboundSupplyChestPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.containerId);
        buffer.writeVarInt(msg.amounts.length);
        for (int amount : msg.amounts) {
            buffer.writeVarInt(amount);
        }
    }

    public static ClientboundSupplyChestPacket decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int length = buffer.readVarInt();
        int[] amounts = new int[length];
        for (int i = 0; i < length; i++) {
            amounts[i] = buffer.readVarInt();
        }
        return new ClientboundSupplyChestPacket(containerId, amounts);
    }

    public static class Handler {
        public static void handle(ClientboundSupplyChestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientSupplyChestSync.apply(msg.containerId, msg.amounts)));
            context.setPacketHandled(true);
        }
    }
}
