package com.sekwah.narutomod.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A nine wide, six tall window onto the caster's supply chest.
 *
 * <p>A slot's container index and its on-screen position are both fixed once it is built, so the only
 * way to scroll is to keep the window's slots put and move what they look at. This wrapper sits
 * between the window's slots and the real container and shifts every access down by
 * {@link #getOffset()}, which the menu raises and lowers as the caster scrolls. Local slot {@code i}
 * therefore always shows the real slot {@code offset + i}, on the server and on the client alike as
 * long as both are told the same offset.</p>
 */
public class SupplyChestWindow implements Container {

    private final SupplyChestContainer chest;
    private final int size;

    /** Real slot the first window slot points at; a multiple of the nine columns. */
    private int offset;

    public SupplyChestWindow(SupplyChestContainer chest, int size) {
        this.chest = chest;
        this.size = size;
    }

    public int getOffset() {
        return this.offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    private int real(int local) {
        return this.offset + local;
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.size; i++) {
            if (this.chest.getAmount(this.real(i)) > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int local) {
        return this.chest.getItem(this.real(local));
    }

    @Override
    public ItemStack removeItem(int local, int amount) {
        return this.chest.removeItem(this.real(local), amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int local) {
        return this.chest.removeItemNoUpdate(this.real(local));
    }

    @Override
    public void setItem(int local, ItemStack stack) {
        this.chest.setItem(this.real(local), stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public int getMaxStackSize() {
        return SupplyChestContainer.MAX_AMOUNT;
    }
}
