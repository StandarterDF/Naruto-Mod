package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Draws the supply chest, the eighty-one slot window over the caster's own container.
 *
 * <p>There is no chest texture to borrow at this size, so the panel and its slot wells are drawn as
 * plain rectangles rather than painted from an atlas.</p>
 */
public class SupplyChestScreen extends AbstractContainerScreen<SupplyChestMenu> {

    public SupplyChestScreen(SupplyChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = SupplyChestMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = SupplyChestMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1B1B1B);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFFC6C6C6);
        for (Slot slot : this.menu.slots) {
            int slotX = x + slot.x;
            int slotY = y + slot.y;
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF6B6B6B);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                0x404040, false);
    }
}
