package com.sekwah.narutomod.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The caster's supply chest, the one place the harvest of their worker clones ends up.
 *
 * <p>It is not a block and there is only ever one per caster: the items live in the caster's own data
 * (see {@code INinjaData#getSupplyChest}) and this menu is only the window onto them, opened by
 * {@code StorageChestAbility}. Because the container is not tied to the world, a worker can drop into
 * it from any distance without a chunk being loaded.</p>
 */
public class SupplyChestMenu extends AbstractContainerMenu {

    /** The chest is a nine by nine grid: eighty-one slots, three times a vanilla chest. */
    public static final int COLUMNS = 9;
    public static final int ROWS = 9;
    public static final int SIZE = COLUMNS * ROWS;

    /** Top of the chest grid, leaving room for the title above it. */
    private static final int CHEST_Y = 15;
    /** Gap between the chest and the player's own inventory below it. */
    private static final int PLAYER_GAP = 12;

    /** Where the player's main inventory starts; the screen labels it here. */
    public static final int PLAYER_INV_Y = CHEST_Y + ROWS * 18 + PLAYER_GAP;
    private static final int HOTBAR_Y = PLAYER_INV_Y + 3 * 18 + 4;
    public static final int IMAGE_HEIGHT = HOTBAR_Y + 18 + 5;

    private final Container container;

    public SupplyChestMenu(int id, Inventory playerInventory, Container container) {
        super(NarutoMenus.SUPPLY_CHEST.get(), id);
        this.container = container;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(container, column + row * COLUMNS, 8 + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < SIZE) {
                // Chest to the player's own inventory, hotbar last.
                if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, SIZE, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
