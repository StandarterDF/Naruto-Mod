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
 * Draws the supply chest: a vanilla double chest window over nine rows of slots.
 *
 * <p>Only {@link SupplyChestMenu#VISIBLE_ROWS} of the nine rows are shown at a time, so the window
 * keeps the size of an ordinary double chest instead of covering the whole screen; the mouse wheel
 * (or the little bar on the right) scrolls the rest into view. The sheet is vanilla's own double
 * chest texture, so nothing about the interface around it changes, and the world behind is not dimmed
 * the way it would be by the usual screen background.</p>
 */
public class SupplyChestScreen extends AbstractContainerScreen<SupplyChestMenu> {

    /** Vanilla's double chest sheet: six rows, exactly what is shown at once. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");

    public SupplyChestScreen(SupplyChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SupplyChestMenu.IMAGE_WIDTH;
        this.imageHeight = SupplyChestMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = SupplyChestMenu.PLAYER_INV_Y - 11;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Deliberately blank: the world behind should look exactly as it does behind any vanilla
        // container, rather than being washed out by the usual dark curtain.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = SupplyChestMenu.ROWS - SupplyChestMenu.VISIBLE_ROWS;
        if (maxScroll <= 0) {
            return;
        }
        int x = this.leftPos + this.imageWidth - 7;
        int top = this.topPos + SupplyChestMenu.CHEST_Y;
        int height = SupplyChestMenu.VISIBLE_ROWS * 18;
        graphics.fill(x, top, x + 5, top + height, 0x66000000);
        int thumbHeight = Math.max(10, height * SupplyChestMenu.VISIBLE_ROWS / SupplyChestMenu.ROWS);
        int thumbY = top + (height - thumbHeight) * this.menu.getScrollRow() / maxScroll;
        graphics.fill(x, thumbY, x + 5, thumbY + thumbHeight, 0xFFC6C6C6);
        graphics.fill(x, thumbY, x + 1, thumbY + thumbHeight, 0xFFFFFFFF);
        graphics.fill(x + 4, thumbY, x + 5, thumbY + thumbHeight, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // Vanilla draws the slot items just before the labels, so the amount badges land on top of
        // them here, before the tooltip is drawn.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        for (int i = 0; i < SupplyChestMenu.WINDOW_SIZE; i++) {
            int amount = this.menu.getDisplayAmount(i);
            if (amount <= 1) {
                continue;
            }
            Slot slot = this.menu.slots.get(i);
            String text = SupplyChestMenu.formatAmount(amount);
            graphics.drawString(this.font, text, slot.x + 17 - this.font.width(text), slot.y + 9,
                    0xFFFFFF, true);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = SupplyChestMenu.ROWS - SupplyChestMenu.VISIBLE_ROWS;
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
