package com.sekwah.narutomod.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * The caster's supply chest, the one place the harvest of their worker clones ends up.
 *
 * <p>It is not a block and there is only ever one per caster: the whole thing lives in the caster's
 * own data, so a worker can drop into it from any distance without a chunk being loaded.</p>
 *
 * <p>A slot does not hold a vanilla stack. Vanilla writes a stack's size as a single byte, so a slot
 * could never hold more than 127 of anything. Instead every slot keeps a count-one <em>template</em>
 * of the item plus its own amount, which can go up to {@link #MAX_AMOUNT}. The template is what
 * vanilla syncs (a harmless stack of one), while the amounts travel over the chest's own packet.</p>
 */
public class SupplyChestContainer implements Container {

    /** How much of one item a single slot can hold. */
    public static final int MAX_AMOUNT = 1_000_000;

    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";
    private static final String AMOUNT_TAG = "Amount";
    private static final String ITEM_TAG = "Item";

    private final int size;
    private final NonNullList<ItemStack> items;
    private final int[] amounts;

    public SupplyChestContainer(int size) {
        this.size = size;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.amounts = new int[size];
    }

    public int getAmount(int slot) {
        return this.amounts[slot];
    }

    /** A copy of every slot's amount, for the sync packet. */
    public int[] snapshotAmounts() {
        return this.amounts.clone();
    }

    /** Sets the amounts straight from the sync packet, leaving the synced item templates alone. */
    public void applyAmounts(int[] amounts) {
        System.arraycopy(amounts, 0, this.amounts, 0, Math.min(amounts.length, this.amounts.length));
    }

    /** Adds to (or takes from) a slot's amount, keeping its item template. */
    public void addAmount(int slot, int delta) {
        this.setEntry(slot, this.items.get(slot), this.amounts[slot] + delta);
    }

    /** Writes one slot from a template (its own count is ignored) and an amount. */
    public void setEntry(int slot, ItemStack template, int amount) {
        if (amount <= 0 || template.isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
            this.amounts[slot] = 0;
        } else {
            ItemStack copy = template.copy();
            copy.setCount(1);
            this.items.set(slot, copy);
            this.amounts[slot] = Math.min(amount, MAX_AMOUNT);
        }
    }

    /**
     * Merges a stack into the chest: matching items first, then empty slots, up to {@link #MAX_AMOUNT}
     * each.
     *
     * @return what did not fit, or an empty stack when it all went in.
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < this.size && !remaining.isEmpty(); slot++) {
            if (this.amounts[slot] <= 0 || !sameKind(this.items.get(slot), remaining)) {
                continue;
            }
            int space = MAX_AMOUNT - this.amounts[slot];
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getCount());
            this.amounts[slot] += moved;
            remaining.shrink(moved);
        }
        for (int slot = 0; slot < this.size && !remaining.isEmpty(); slot++) {
            if (this.amounts[slot] > 0) {
                continue;
            }
            int moved = Math.min(MAX_AMOUNT, remaining.getCount());
            ItemStack template = remaining.copy();
            template.setCount(1);
            this.items.set(slot, template);
            this.amounts[slot] = moved;
            remaining.shrink(moved);
        }
        return remaining;
    }

    /** The same item with the same tag; the amount is not part of it. */
    public static boolean sameKind(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty()
                && a.getItem() == b.getItem()
                && Objects.equals(a.getTag(), b.getTag());
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (int amount : this.amounts) {
            if (amount > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.amounts[slot] > 0 ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        int taken = Math.min(amount, this.amounts[slot]);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = this.items.get(slot).copy();
        result.setCount(taken);
        this.setEntry(slot, this.items.get(slot), this.amounts[slot] - taken);
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (this.amounts[slot] <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = this.items.get(slot).copy();
        result.setCount(this.amounts[slot]);
        this.items.set(slot, ItemStack.EMPTY);
        this.amounts[slot] = 0;
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.setEntry(slot, stack, stack.isEmpty() ? 0 : stack.getCount());
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
        for (int slot = 0; slot < this.size; slot++) {
            this.items.set(slot, ItemStack.EMPTY);
            this.amounts[slot] = 0;
        }
    }

    @Override
    public int getMaxStackSize() {
        return MAX_AMOUNT;
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < this.size; slot++) {
            if (this.amounts[slot] <= 0 || this.items.get(slot).isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT_TAG, slot);
            entry.putInt(AMOUNT_TAG, this.amounts[slot]);
            entry.put(ITEM_TAG, this.items.get(slot).save(new CompoundTag()));
            list.add(entry);
        }
        tag.put(ITEMS_TAG, list);
    }

    public void load(CompoundTag tag) {
        this.clearContent();
        ListTag list = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt(SLOT_TAG);
            if (slot < 0 || slot >= this.size) {
                continue;
            }
            this.setEntry(slot, ItemStack.of(entry.getCompound(ITEM_TAG)), entry.getInt(AMOUNT_TAG));
        }
    }

    /**
     * Reads the plain container list an earlier build wrote ({@code SimpleContainer#createTag}):
     * each entry is a whole item stack with a {@code Slot}, so its count becomes the amount.
     */
    public void loadLegacy(ListTag list) {
        this.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < 0 || slot >= this.size) {
                continue;
            }
            ItemStack stack = ItemStack.of(entry);
            if (!stack.isEmpty()) {
                this.setEntry(slot, stack, stack.getCount());
            }
        }
    }
}
