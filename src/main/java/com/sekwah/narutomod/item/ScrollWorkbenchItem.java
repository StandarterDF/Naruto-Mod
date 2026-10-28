package com.sekwah.narutomod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A field workbench, folded into a scroll.
 *
 * <p>Ninja travel light, and a crafting table is the one thing a camp always needs and never has.
 * The scroll is the table: opening it gives the ordinary three by three grid, so the recipes and the
 * recipe book keep working exactly as they do at home. It is deliberately not a portable furnace or
 * an anvil - one convenience is enough, and the rest of the camp still has to be built.</p>
 */
public class ScrollWorkbenchItem extends Item {

    public ScrollWorkbenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> new CraftingMenu(id, inventory,
                            ContainerLevelAccess.create(level, player.blockPosition())),
                    Component.translatable("item.narutomod.scroll_workbench")));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
