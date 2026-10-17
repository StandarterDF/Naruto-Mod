package com.sekwah.narutomod.menu;

import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundSupplyChestPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Locale;

/**
 * The caster's supply chest, the one place the harvest of their worker clones ends up.
 *
 * <p>It is not a block and there is only ever one per caster: the whole thing lives in the caster's
 * own data (see {@code INinjaData#getSupplyChest}) and this menu is only the window onto it, opened
 * by {@code StorageChestAbility}. Because the container is not tied to the world, a worker can drop
 * into it from any distance without a chunk being loaded.</p>
 *
 * <p>The chest is nine rows of nine, more than fits on a screen, so the menu carries a
 * {@link SupplyChestWindow} of {@link #VISIBLE_ROWS} rows that the caster scrolls up and down. And
 * because a slot can hold far more than a vanilla stack, the click handling here is written by hand
 * against {@link SupplyChestContainer#getAmount(int)} rather than reusing the stack-size based vanilla
 * logic.</p>
 */
public class SupplyChestMenu extends AbstractContainerMenu {

    /** The chest is a nine by nine grid: eighty-one slots, three times a vanilla chest. */
    public static final int COLUMNS = 9;
    public static final int ROWS = 9;
    public static final int SIZE = COLUMNS * ROWS;

    /** How many rows fit on screen at once; the rest are reached by scrolling. */
    public static final int VISIBLE_ROWS = 6;
    /** Number of chest slots actually in the menu, the visible window. */
    public static final int WINDOW_SIZE = VISIBLE_ROWS * COLUMNS;

    /** Layout of the vanilla double chest sheet, which the screen paints. */
    public static final int CHEST_Y = 18;
    public static final int PLAYER_INV_Y = 139;
    public static final int HOTBAR_Y = 197;
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 222;

    /** The most of one item a single slot can hold. */
    public static final int MAX_AMOUNT = SupplyChestContainer.MAX_AMOUNT;

    private final SupplyChestContainer container;
    private final SupplyChestWindow window;
    private final Player player;

    /** Which row of the chest the window currently starts at. */
    private int scrollRow;

    /** Amounts last handed to the client, or all -1 until the first update. */
    private final int[] syncedAmounts = new int[SIZE];

    /** Client side copy of the amounts, filled in by the sync packet. */
    private final int[] clientAmounts = new int[SIZE];

    public SupplyChestMenu(int id, Inventory playerInventory, SupplyChestContainer container) {
        super(NarutoMenus.SUPPLY_CHEST.get(), id);
        this.container = container;
        this.window = new SupplyChestWindow(container, WINDOW_SIZE);
        this.player = playerInventory.player;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new SupplyChestSlot(this.window, column + row * COLUMNS,
                        8 + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
        Arrays.fill(this.syncedAmounts, -1);
    }

    /**
     * The amount to draw on a window slot. On the client this is the copy the sync packet brought
     * over; on the server it is the real thing, which only matters for tests and tooling.
     */
    public int getDisplayAmount(int windowSlot) {
        int real = this.realSlot(windowSlot);
        return this.player.level().isClientSide ? this.clientAmounts[real] : this.container.getAmount(real);
    }

    /** Client side: drops in the amounts from the sync packet. */
    public void applyAmounts(int[] amounts) {
        System.arraycopy(amounts, 0, this.clientAmounts, 0,
                Math.min(amounts.length, this.clientAmounts.length));
    }

    public int getScrollRow() {
        return this.scrollRow;
    }

    /** Called on both ends: the client from its wheel, the server from the scroll packet. */
    public void setScrollRow(int row) {
        int clamped = Mth.clamp(row, 0, ROWS - VISIBLE_ROWS);
        if (clamped == this.scrollRow) {
            return;
        }
        this.scrollRow = clamped;
        this.window.setOffset(clamped * COLUMNS);
        if (this.player instanceof ServerPlayer) {
            // Nudge the item sync so the client is told what the new slice holds right away.
            this.broadcastChanges();
        }
    }

    /** Real chest slot a window slot currently points at. */
    private int realSlot(int windowSlot) {
        return this.window.getOffset() + windowSlot;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.player instanceof ServerPlayer serverPlayer) {
            int[] amounts = this.container.snapshotAmounts();
            if (!Arrays.equals(amounts, this.syncedAmounts)) {
                PacketHandler.sendToPlayer(
                        new ClientboundSupplyChestPacket(this.containerId, amounts), serverPlayer);
                System.arraycopy(amounts, 0, this.syncedAmounts, 0, SIZE);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < WINDOW_SIZE) {
            switch (clickType) {
                case PICKUP -> this.pickUpChest(slotId, button);
                case QUICK_MOVE -> this.takeFromChest(slotId);
                case THROW -> this.throwFromChest(slotId, button, player);
                default -> {
                    // Dragging, hotbar swaps and the like are not worth redoing for amounts.
                }
            }
            return;
        }
        if (clickType == ClickType.QUICK_MOVE && slotId >= WINDOW_SIZE && slotId < this.slots.size()) {
            this.putIntoInventoryChest(slotId);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < WINDOW_SIZE) {
            this.takeFromChest(index);
        } else if (index >= WINDOW_SIZE && index < this.slots.size()) {
            this.putIntoInventoryChest(index);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /**
     * Takes from, or puts into, a chest slot. A left click moves up to a vanilla stack at a time, a
     * right click a single item, so the huge amounts stay manageable.
     */
    private void pickUpChest(int windowSlot, int button) {
        int real = this.realSlot(windowSlot);
        int amount = this.container.getAmount(real);
        ItemStack carried = this.getCarried();
        if (amount <= 0) {
            if (!carried.isEmpty()) {
                int add = button == 0 ? carried.getCount() : 1;
                this.container.setEntry(real, carried, add);
                carried.shrink(add);
                this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            return;
        }
        ItemStack template = this.container.getItem(real);
        if (carried.isEmpty()) {
            int take = button == 0 ? Math.min(amount, 64) : 1;
            ItemStack taken = template.copy();
            taken.setCount(take);
            this.setCarried(taken);
            this.container.addAmount(real, -take);
        } else if (SupplyChestContainer.sameKind(template, carried)) {
            int add = button == 0 ? carried.getCount() : 1;
            int move = Math.min(add, MAX_AMOUNT - amount);
            if (move > 0) {
                this.container.addAmount(real, move);
                carried.shrink(move);
                this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        }
    }

    /** Shift-click from a chest slot: tips the whole amount out into the player's own inventory. */
    private void takeFromChest(int windowSlot) {
        int real = this.realSlot(windowSlot);
        int amount = this.container.getAmount(real);
        if (amount <= 0) {
            return;
        }
        ItemStack template = this.container.getItem(real);
        int remaining = amount;
        for (int i = WINDOW_SIZE; i < this.slots.size() && remaining > 0; i++) {
            Slot slot = this.slots.get(i);
            ItemStack inv = slot.getItem();
            if (inv.isEmpty() || !SupplyChestContainer.sameKind(inv, template)) {
                continue;
            }
            int space = Math.min(slot.getMaxStackSize(), inv.getMaxStackSize()) - inv.getCount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, remaining);
            inv.grow(move);
            slot.setChanged();
            remaining -= move;
        }
        for (int i = WINDOW_SIZE; i < this.slots.size() && remaining > 0; i++) {
            Slot slot = this.slots.get(i);
            if (!slot.getItem().isEmpty()) {
                continue;
            }
            int move = Math.min(Math.min(slot.getMaxStackSize(), template.getMaxStackSize()), remaining);
            ItemStack placed = template.copy();
            placed.setCount(move);
            slot.set(placed);
            remaining -= move;
        }
        if (remaining != amount) {
            this.container.addAmount(real, remaining - amount);
        }
    }

    /** Throws one item, or a vanilla stack with the right button, out of a chest slot. */
    private void throwFromChest(int windowSlot, int button, Player player) {
        int real = this.realSlot(windowSlot);
        int amount = this.container.getAmount(real);
        if (amount <= 0) {
            return;
        }
        int drop = button == 1 ? Math.min(amount, 64) : 1;
        ItemStack stack = this.container.getItem(real).copy();
        stack.setCount(drop);
        player.drop(stack, false);
        this.container.addAmount(real, -drop);
    }

    /** Shift-click from the player's own inventory: tops up the chest, overflow staying put. */
    private void putIntoInventoryChest(int slotId) {
        Slot slot = this.slots.get(slotId);
        ItemStack from = slot.getItem();
        if (from.isEmpty()) {
            return;
        }
        ItemStack leftover = this.container.insert(from);
        if (leftover.getCount() == from.getCount()) {
            return;
        }
        if (leftover.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setCount(leftover.getCount());
            slot.setChanged();
        }
    }

    /** A chest slot: holds up to a million of one item. */
    public static class SupplyChestSlot extends Slot {

        public SupplyChestSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return MAX_AMOUNT;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return MAX_AMOUNT;
        }
    }

    /** Short form for the amount badge: {@code 1M}, {@code 12.5K}, {@code 999} and so on. */
    public static String formatAmount(int amount) {
        if (amount < 1000) {
            return Integer.toString(amount);
        }
        if (amount < 1_000_000) {
            return compact(amount, 1000) + "K";
        }
        return compact(amount, 1_000_000) + "M";
    }

    private static String compact(int amount, int unit) {
        double value = amount / (double) unit;
        if (value >= 100.0D || value == Math.floor(value)) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
