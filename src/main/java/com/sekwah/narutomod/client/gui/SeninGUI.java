package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A slim bar for nature energy (senin mana), sitting immediately above the chakra bar.
 *
 * <p>It is drawn by hand rather than from the chakra bar texture, because it is a different kind of
 * resource and should not be mistaken for a second chakra bar. It only shows up once the caster has
 * gathered something or has sage mode running - an empty bar a new ninja cannot fill yet would just
 * be clutter.</p>
 */
public class SeninGUI implements PlayerGUI {

    private static final int WIDTH = 100;
    private static final int HEIGHT = 6;
    private static final int OFFSET = 128;

    /** Same colour family as the sage particles. */
    private static final int COLOR_FILL = 0xFF43D16B;
    private static final int COLOR_FRAME = 0xFF0E3A1B;
    private static final int COLOR_BACK = 0xAA1B1B1B;

    private final Minecraft minecraft;

    private float seninMana;
    private float maxSeninMana;
    private boolean sageMode;
    private boolean enabled;

    public SeninGUI(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(GuiGraphics guiGraphics, Matrix4f worldMatrix, Vec3 cameraPos) {
        if (!this.enabled || this.maxSeninMana <= 0.0f) {
            return;
        }
        if (this.seninMana <= 0.0f && !this.sageMode) {
            return;
        }

        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 - OFFSET - WIDTH;
        int y = screenHeight - 22 - HEIGHT - 4;

        guiGraphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, COLOR_FRAME);
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, COLOR_BACK);

        float percent = Math.min(this.seninMana / this.maxSeninMana, 1.0f);
        int filled = (int) (WIDTH * percent);
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + HEIGHT, COLOR_FILL);
        }

        String label = Component.translatable("gui.narutomod.senin",
                (int) this.seninMana, (int) this.maxSeninMana).getString();
        Font font = this.minecraft.font;
        guiGraphics.drawString(font, label, x + WIDTH + 3, y - 1, 0xFFFFFFFF);
    }

    @Override
    public void tick(Player player) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            this.enabled = ninjaData.isNinjaModeEnabled();
            this.seninMana = ninjaData.getSeninMana();
            this.maxSeninMana = ninjaData.getMaxSeninMana();
            this.sageMode = ninjaData.isSageMode();
        });
    }
}
