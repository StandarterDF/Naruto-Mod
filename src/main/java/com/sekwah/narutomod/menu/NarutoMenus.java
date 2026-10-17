package com.sekwah.narutomod.menu;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's container menus.
 */
public class NarutoMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, NarutoMod.MOD_ID);

    /**
     * The supply chest menu. The chest has no fixed size, so the server writes the current one into
     * the window's extra data and the client builds an empty container of exactly that size; the
     * server feeds its own menu the real container. The contents themselves live in the caster's
     * data, not in a block.
     */
    public static final RegistryObject<MenuType<SupplyChestMenu>> SUPPLY_CHEST = MENUS.register("supply_chest",
            () -> new MenuType<>(
                    (IContainerFactory<SupplyChestMenu>) (id, inventory, data) -> new SupplyChestMenu(id, inventory,
                            new SupplyChestContainer(data == null
                                    ? SupplyChestContainer.MIN_SIZE
                                    : data.readVarInt())),
                    FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
