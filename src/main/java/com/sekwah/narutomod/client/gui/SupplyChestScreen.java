package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sekwah.narutomod.menu.SupplyChestMenu;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerSupplyChestScrollPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Draws the supply chest: a vanilla chest window over however many rows the chest currently holds.
 *
 * <p>The window is built at its full height for the size the chest had when it was opened and never
 * changes shape, so a chest that keeps growing past the visible rows does not move anything on screen;
 * the mouse wheel (or the little bar on the right) scrolls the rest into view, and the server keeps
 * the client's idea of the chest's size up to date so newly filled rows are always reachable. The
 * sheet is vanilla's own double chest texture, cut to the right
 * height, so nothing about the interface around it changes, and the world behind is not dimmed the
 * way it would be by the usual screen background.</p>
 */
public class SupplyChestScreen extends AbstractContainerScreen<SupplyChestMenu> {

    /** Vanilla's double chest sheet, cut down to the rows actually shown. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");

    /** Where the caster's inventory sits in the vanilla sheet, below the chest rows. */
    private static final int PLAYER_INV_SOURCE_Y = 126;
    private static final int PLAYER_INV_SOURCE_HEIGHT = 96;

    /** How many rows of the chest this window shows. */
    private final int rows;

    public SupplyChestScreen(SupplyChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.getVisibleRows();
        this.imageWidth = SupplyChestMenu.IMAGE_WIDTH;
        this.imageHeight = SupplyChestMenu.imageHeight(this.rows);
        this.inventoryLabelY = SupplyChestMenu.playerInvY(this.rows) - 11;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Deliberately blank: the world behind should look exactly as it does behind any vanilla
        // container, rather than being washed out by the usual dark curtain.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // The sheet is one tall double chest, but vanilla itself cuts it in two for a smaller chest:
        // the top of the sheet is the chest area (a header plus one row per line of slots) and the
        // caster's inventory is a fixed block at the bottom.
        int chestHeight = this.rows * 18 + 17;
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, chestHeight);
        graphics.blit(TEXTURE, this.leftPos, this.topPos + chestHeight,
                0, PLAYER_INV_SOURCE_Y, this.imageWidth, PLAYER_INV_SOURCE_HEIGHT);
        this.renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = this.menu.getMaxScrollRow();
        if (maxScroll <= 0) {
            return;
        }
        int x = this.leftPos + this.imageWidth - 7;
        int top = this.topPos + SupplyChestMenu.CHEST_Y;
        int height = this.rows * 18;
        graphics.fill(x, top, x + 5, top + height, 0x66000000);
        int thumbHeight = Math.max(10, height * this.rows / this.menu.getTotalRows());
        int thumbY = top + (height - thumbHeight) * this.menu.getScrollRow() / maxScroll;
        graphics.fill(x, thumbY, x + 5, thumbY + thumbHeight, 0xFFC6C6C6);
        graphics.fill(x, thumbY, x + 1, thumbY + thumbHeight, 0xFFFFFFFF);
        graphics.fill(x + 4, thumbY, x + 5, thumbY + thumbHeight, 0xFF555555);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The item sprites are drawn deep inside super.render, so the amount badges have to go in
        // afterwards to land in front of them instead of under the item icon.
        this.renderAmountBadges(graphics);
    }

    private void renderAmountBadges(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        for (int i = 0; i < this.menu.getWindowSize(); i++) {
            int amount = this.menu.getDisplayAmount(i);
            if (amount <= 1) {
                continue;
            }
            Slot slot = this.menu.slots.get(i);
            String text = SupplyChestMenu.formatAmount(amount);
            graphics.drawString(this.font, text,
                    this.leftPos + slot.x + 17 - this.font.width(text),
                    this.topPos + slot.y + 9, 0xFFFFFF, true);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = this.menu.getMaxScrollRow();
        if (maxScroll > 0 && delta != 0.0D) {
            int next = Mth.clamp(this.menu.getScrollRow() - (int) Math.signum(delta), 0, maxScroll);
            if (next != this.menu.getScrollRow()) {
                this.menu.setScrollRow(next);
                PacketHandler.sendToServer(
                        new ServerSupplyChestScrollPacket(this.menu.containerId, next));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
