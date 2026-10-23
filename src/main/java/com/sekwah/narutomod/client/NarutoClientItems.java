package com.sekwah.narutomod.client;

import com.sekwah.narutomod.item.ChakraCompassItem;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * Item model properties the mod needs on the client.
 *
 * <p>Kept separate from screens so the compass can be given its needle without a GUI being
 * involved. Only ever called from client setup.</p>
 */
public final class NarutoClientItems {

    private NarutoClientItems() {
    }

    public static void register() {
        // The compass needle is not reimplemented here: vanilla's own angle function is reused with
        // our binding as its target, so the needle points exactly like a lodestone compass would.
        ItemProperties.register(NarutoItems.CHAKRA_COMPASS.get(), new ResourceLocation("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> ChakraCompassItem.getTarget(stack)));
    }
}
