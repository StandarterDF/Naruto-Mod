package com.sekwah.narutomod.menu;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
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
     * The supply chest menu. The client builds it around an empty container of the right size and the
     * server feeds it the real one; the contents themselves live in the caster's data, not in a block.
     */
    public static final RegistryObject<MenuType<SupplyChestMenu>> SUPPLY_CHEST = MENUS.register("supply_chest",
            () -> new MenuType<>(
                    (id, inventory) -> new SupplyChestMenu(id, inventory,
                            new SupplyChestContainer(SupplyChestMenu.SIZE)),
                    FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
