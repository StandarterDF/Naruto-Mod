package com.sekwah.narutomod.client;

import com.sekwah.narutomod.client.gui.SupplyChestScreen;
import com.sekwah.narutomod.menu.NarutoMenus;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Hooks the mod's menus up to their screens. Called from client setup, so it never loads on a
 * dedicated server.
 */
public final class NarutoClientScreens {

    private NarutoClientScreens() {
    }

    public static void register() {
        MenuScreens.register(NarutoMenus.SUPPLY_CHEST.get(), SupplyChestScreen::new);
    }
}
