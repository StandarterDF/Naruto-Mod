package com.sekwah.narutomod.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Helpers for pushing stacks into a container - the worker storage chest, most of all.
 */
public final class Inventories {

    private Inventories() {
    }

    /**
     * Pushes as much of {@code stack} as will fit into {@code container}: matching stacks are topped
     * up first, then empty slots are filled.
     *
     * @return what could not be placed, or an empty stack when it all went in.
     */
    public static ItemStack putInto(Container container, ItemStack stack) {
        ItemStack remaining = stack.copy();
        int size = container.getContainerSize();
        for (int slot = 0; slot < size && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remaining)
                    || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int space = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            container.setChanged();
        }
        for (int slot = 0; slot < size && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            container.setItem(slot, placed);
            remaining.shrink(moved);
            container.setChanged();
        }
        return remaining;
    }
}
