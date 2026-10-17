package com.sekwah.narutomod.client;

import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.client.Minecraft;

/**
 * Client end of {@code ClientboundSupplyChestPacket}: stashes the chest's amounts on the open menu so
 * the screen can draw them. Kept out of the packet class so a dedicated server never loads it.
 */
public final class ClientSupplyChestSync {

    private ClientSupplyChestSync() {
    }

    public static void apply(int containerId, int[] amounts) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.containerMenu instanceof SupplyChestMenu menu
                && menu.containerId == containerId) {
            menu.applyAmounts(amounts);
        }
    }
}
