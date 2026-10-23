package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.network.s2c.ClientboundCameraPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of the Senrigan: holds the camera on the body the server picked.
 *
 * <p>A packet can arrive a tick before the entity it names is in the client level, so the id is
 * parked and applied on the next client tick; if the entity never turns up the camera simply goes
 * back to the caster rather than hanging on a body that is not there. If the body is removed while
 * it is being watched - the clone popped, the player changed dimension - the camera comes home on
 * its own, without waiting for the server's correction.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientCameraHandler {

    /** No packet waiting. */
    private static final int NONE = Integer.MIN_VALUE;

    /** How long a parked camera id is given to appear before it is dropped. */
    private static final int PATIENCE = 100;

    private static int pending = NONE;
    private static int waited;

    /** The body the client is watching, or {@link ClientboundCameraPacket#SELF}. */
    private static int current = ClientboundCameraPacket.SELF;

    private ClientCameraHandler() {
    }

    /** Called from the packet handler. */
    public static void watch(int entityId) {
        pending = entityId;
        waited = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            pending = NONE;
            current = ClientboundCameraPacket.SELF;
            return;
        }
        if (pending != NONE) {
            apply(minecraft);
            return;
        }
        // The watched body may vanish without the server having said anything yet.
        if (current != ClientboundCameraPacket.SELF) {
            Entity watched = minecraft.level.getEntity(current);
            if (watched == null || watched.isRemoved() || !watched.isAlive()) {
                home(minecraft);
            }
        }
    }

    private static void apply(Minecraft minecraft) {
        if (pending == ClientboundCameraPacket.SELF) {
            home(minecraft);
            pending = NONE;
            return;
        }
        Entity watched = minecraft.level.getEntity(pending);
        if (watched != null && watched.isAlive()) {
            minecraft.setCameraEntity(watched);
            current = pending;
            pending = NONE;
        } else if (++waited > PATIENCE) {
            home(minecraft);
            pending = NONE;
        }
    }

    /** Puts the viewpoint back on the caster, wherever it was. */
    public static void home(Minecraft minecraft) {
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        current = ClientboundCameraPacket.SELF;
    }
}
