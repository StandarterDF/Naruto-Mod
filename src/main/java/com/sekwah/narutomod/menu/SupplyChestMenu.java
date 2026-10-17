package com.sekwah.narutomod.menu;

import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundSupplyChestPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.Arrays;

/**
 * The caster's supply chest, the one place the harvest of their worker clones ends up.
 *
 * <p>It is not a block and there is only ever one per caster: the whole thing lives in the caster's
 * own data (see {@code INinjaData#getSupplyChest}) and this menu is only the window onto it, opened
 * by {@code StorageChestAbility}. Because the container is not tied to the world, a worker can drop
 * into it from any distance without a chunk being loaded.</p>
 *
 * <p>The chest grows with its contents - {@link SupplyChestContainer#MIN_SIZE} slots by default,
 * more when a worker has more to put away - and this menu shows as many rows as the chest currently
 * holds, up to {@link #MAX_VISIBLE_ROWS}. Anything past the sixth row is reached by scrolling the
 * {@link SupplyChestWindow}, so the window never covers the whole screen. A slot can hold far more
 * than a vanilla stack, so the click handling here is written by hand against
 * {@link SupplyChestContainer#getAmount(int)} rather than reusing the stack-size based vanilla
 * logic.</p>
 *
 * <p>Nothing may be <em>put into</em> the chest by hand: it is the workers' delivery box, and a
 * caster only looks inside to take what they brought. Every click that would insert - a held stack
 * on a slot, a shift-click from the inventory, a hotbar swap, a creative clone - is simply ignored,
 * while taking still works as it always did.</p>
 */
public class SupplyChestMenu extends AbstractContainerMenu {

    /** The chest is a nine wide grid. */
    public static final int COLUMNS = SupplyChestContainer.COLUMNS;

    /** The window shows at least this many rows, even for the smallest chest. */
    public static final int MIN_ROWS = 3;

    /** How many rows fit on screen at once; anywhere past these is reached by scrolling. */
    public static final int MAX_VISIBLE_ROWS = 6;

    /** Layout of the vanilla double chest sheet, taken straight from a six row chest. */
    public static final int CHEST_Y = 18;
    public static final int IMAGE_WIDTH = 176;
    /** Where the caster's inventory starts on a full six row window, as the vanilla sheet has it. */
    private static final int PLAYER_INV_BASE_Y = 139;
    private static final int PLAYER_INV_ROW_GAP = 18;

    /** The most of one item the caster may take at once, a plain vanilla stack. */
    public static final int CARRY_LIMIT = 64;
    public static final int MAX_AMOUNT = SupplyChestContainer.MAX_AMOUNT;

    private final SupplyChestContainer container;
    private final SupplyChestWindow window;
    private final Player player;

    /** How many rows of the chest the window shows, decided when it is opened. */
    private final int visibleRows;
    /** Number of chest slots actually in the menu, the visible window. */
    private final int windowSize;

    /** The size the chest had when this window was built; if it changes the window is rebuilt. */
    private int openedSize;

    /** Which row of the chest the window currently starts at. */
    private int scrollRow;

    /** True while a rebuild of the window has been scheduled, so it is only done once. */
    private boolean reopening;

    /** Amounts last handed to the client, or all -1 until the first update. */
    private final int[] syncedAmounts;

    /** Client side copy of the amounts, filled in by the sync packet. */
    private final int[] clientAmounts;

    public SupplyChestMenu(int id, Inventory playerInventory, SupplyChestContainer container) {
        super(NarutoMenus.SUPPLY_CHEST.get(), id);
        this.container = container;
        this.visibleRows = visibleRows(container.getContainerSize());
        this.windowSize = this.visibleRows * COLUMNS;
        this.window = new SupplyChestWindow(container, this.windowSize);
        this.player = playerInventory.player;
        container.startOpen(playerInventory.player);
        this.openedSize = container.getContainerSize();

        int playerInvY = playerInvY(this.visibleRows);
        for (int row = 0; row < this.visibleRows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new SupplyChestSlot(this.window, column + row * COLUMNS,
                        8 + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, playerInvY + row * PLAYER_INV_ROW_GAP));
            }
        }
        int hotbarY = playerInvY + 58;
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, hotbarY));
        }
        this.syncedAmounts = new int[container.getContainerSize()];
        this.clientAmounts = new int[container.getContainerSize()];
        Arrays.fill(this.syncedAmounts, -1);
    }

    /** How many rows a chest of the given size needs, clamped to what a window can show. */
    public static int visibleRows(int size) {
        return Mth.clamp((size + COLUMNS - 1) / COLUMNS, MIN_ROWS, MAX_VISIBLE_ROWS);
    }

    /** The height of the screen for a window of the given number of rows. */
    public static int imageHeight(int rows) {
        return 114 + rows * 18;
    }

    /** Where the caster's own inventory starts, so the chest rows fit above it. */
    public static int playerInvY(int rows) {
        return PLAYER_INV_BASE_Y + (rows - MAX_VISIBLE_ROWS) * 18;
    }

    public int getVisibleRows() {
        return this.visibleRows;
    }

    public int getWindowSize() {
        return this.windowSize;
    }

    /** How many rows the chest currently has, whether or not the window shows them all. */
    public int getTotalRows() {
        return (this.container.getContainerSize() + COLUMNS - 1) / COLUMNS;
    }

    /** The furthest row the window may be scrolled to. */
    public int getMaxScrollRow() {
        return Math.max(0, this.getTotalRows() - MAX_VISIBLE_ROWS);
    }

    /**
     * The amount to draw on a window slot. On the client this is the copy the sync packet brought
     * over; on the server it is the real thing, which only matters for tests and tooling.
     */
    public int getDisplayAmount(int windowSlot) {
        int real = this.realSlot(windowSlot);
        if (real < 0) {
            return 0;
        }
        if (this.player.level().isClientSide) {
            return real < this.clientAmounts.length ? this.clientAmounts[real] : 0;
        }
        return this.container.getAmount(real);
    }

    /** Client side: drops in the amounts from the sync packet. */
    public void applyAmounts(int[] amounts) {
        Arrays.fill(this.clientAmounts, 0);
        System.arraycopy(amounts, 0, this.clientAmounts, 0,
                Math.min(amounts.length, this.clientAmounts.length));
    }

    public int getScrollRow() {
        return this.scrollRow;
    }

    /** Called on both ends: the client from its wheel, the server from the scroll packet. */
    public void setScrollRow(int row) {
        int clamped = Mth.clamp(row, 0, this.getMaxScrollRow());
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
        if (this.player instanceof ServerPlayer serverPlayer) {
            if (this.container.getContainerSize() != this.openedSize) {
                // The slot layout is fixed once built, so a chest that grew under the open window is
                // shown by opening it afresh rather than by trying to move slots around. The swap is
                // held back a tick so it never happens in the middle of the container tick that
                // noticed it, and while the caster is carrying something, since a window swap would
                // put the carried stack back.
                if (!this.getCarried().isEmpty()) {
                    return;
                }
                if (this.reopening) {
                    this.reopen(serverPlayer);
                } else {
                    this.reopening = true;
                }
                return;
            }
        }
        super.broadcastChanges();
        if (this.player instanceof ServerPlayer serverPlayer) {
            int[] amounts = this.container.snapshotAmounts();
            if (amounts.length == this.syncedAmounts.length && !Arrays.equals(amounts, this.syncedAmounts)) {
                PacketHandler.sendToPlayer(
                        new ClientboundSupplyChestPacket(this.containerId, amounts), serverPlayer);
                System.arraycopy(amounts, 0, this.syncedAmounts, 0, amounts.length);
            }
        }
    }

    /** Opens the chest again so the window picks up a grid that changed size. */
    private void reopen(ServerPlayer serverPlayer) {
        if (serverPlayer.containerMenu != this || !this.getCarried().isEmpty()) {
            this.reopening = false;
            return;
        }
        SupplyChestContainer chest = this.container;
        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, p) -> new SupplyChestMenu(id, inventory, chest),
                        Component.translatable("narutomod:storage_chest")),
                buffer -> buffer.writeVarInt(chest.getContainerSize()));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.windowSize) {
            switch (clickType) {
                case PICKUP -> this.takeFromWindow(slotId, button);
                case QUICK_MOVE -> this.takeFromChest(slotId);
                case THROW -> this.throwFromChest(slotId, button, player);
                default -> {
                    // Dragging, hotbar swaps, creative clones: all of them could put something in,
                    // and nothing may go in, so they are all dropped on the floor.
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < this.windowSize) {
            this.takeFromChest(index);
        } else if (index >= this.windowSize && index < this.slots.size()) {
            // A shift-click in the caster's own inventory just shuffles it about; it never reaches
            // the chest, which is take-only.
            this.moveWithinInventory(index);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /**
     * Takes from a chest slot into the caster's hand. Unlike a vanilla chest there is no putting
     * back: a click with a stack already in hand does nothing at all.
     */
    private void takeFromWindow(int windowSlot, int button) {
        int real = this.realSlot(windowSlot);
        int amount = this.container.getAmount(real);
        if (amount <= 0) {
            return;
        }
        ItemStack template = this.container.getItem(real);
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) {
            int take = button == 0 ? Math.min(amount, CARRY_LIMIT) : 1;
            ItemStack taken = template.copy();
            taken.setCount(take);
            this.setCarried(taken);
            this.container.addAmount(real, -take);
        } else if (SupplyChestContainer.sameKind(template, carried) && carried.getCount() < CARRY_LIMIT) {
            int take = button == 0 ? Math.min(amount, CARRY_LIMIT - carried.getCount()) : 1;
            if (take > 0) {
                carried.grow(take);
                this.container.addAmount(real, -take);
                this.setCarried(carried);
            }
        }
    }

    /** Shift-click from a chest slot: tips the whole amount out into the caster's own inventory. */
    private void takeFromChest(int windowSlot) {
        int real = this.realSlot(windowSlot);
        int amount = this.container.getAmount(real);
        if (amount <= 0) {
            return;
        }
        ItemStack template = this.container.getItem(real);
        int remaining = amount;
        for (int i = this.windowSize; i < this.slots.size() && remaining > 0; i++) {
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
        for (int i = this.windowSize; i < this.slots.size() && remaining > 0; i++) {
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
        int drop = button == 1 ? Math.min(amount, CARRY_LIMIT) : 1;
        ItemStack stack = this.container.getItem(real).copy();
        stack.setCount(drop);
        player.drop(stack, false);
        this.container.addAmount(real, -drop);
    }

    /** Shuffles a stack between the caster's main inventory and their hotbar, the usual shift-click. */
    private void moveWithinInventory(int index) {
        Slot slot = this.slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }
        int mainStart = this.windowSize;
        int hotbarStart = mainStart + 27;
        boolean moved;
        if (index < hotbarStart) {
            moved = this.moveItemStackTo(stack, hotbarStart, this.slots.size(), false);
        } else {
            moved = this.moveItemStackTo(stack, mainStart, hotbarStart, false);
        }
        if (moved) {
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
    }

    /** A chest slot: holds up to {@link #MAX_AMOUNT} of one item. */
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

    /** The amount badge: a plain number, since a slot never holds more than {@link #MAX_AMOUNT}. */
    public static String formatAmount(int amount) {
        return Integer.toString(amount);
    }
}
