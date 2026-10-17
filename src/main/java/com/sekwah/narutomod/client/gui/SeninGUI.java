package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.ColorUtil;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * The nature energy (senin mana) bar.
 *
 * <p>It is drawn from the very same bar texture and geometry as the chakra bar, sitting directly
 * above it, because a hand drawn rectangle looked nothing like the rest of the mod's bars. Only the
 * colour sets it apart - sage green, the same family as the sage particles - and the value is written
 * with the same outlined font the chakra and stamina bars use.</p>
 *
 * <p>It only appears once there is something to show: gathering has started or sage mode is running.
 * An empty bar a new ninja cannot fill yet would just be clutter.</p>
 */
public class SeninGUI implements PlayerGUI {

    private static final int WIDTH = 100;
    private static final int OFFSET = 128;

    /** Sage nature energy reads as green, matching the sage particles. */
    private static final Color SENIN_COLOR = new Color(67, 209, 107);

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
        int screenMid = screenWidth / 2;

        BarDesigns.BarInfo bar = ChakraAndStaminaGUI.barTypes[NarutoConfig.chakraBarDesign];
        int barWidth = bar.width;
        int xOffset = bar.offset;

        // One bar height above the chakra bar, on the same side, so the two line up as a stack.
        int y = screenHeight - 44;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, bar.texture);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        guiGraphics.blit(bar.texture, screenMid - WIDTH - OFFSET, y,
                0, 22,
                WIDTH, 22,
                WIDTH, 44);

        float percent = Math.min(this.seninMana / this.maxSeninMana, 1.0f);
        int filled = (int) (barWidth * percent);
        if (filled > 0) {
            this.setColor(SENIN_COLOR);
            guiGraphics.blit(bar.texture, screenMid - filled - OFFSET - (WIDTH - xOffset - barWidth), y,
                    xOffset + (barWidth - filled), 0,
                    filled, 22,
                    WIDTH, 44);
            this.setColor(Color.WHITE);
        }

        int color = ColorUtil.toMCColor(SENIN_COLOR).getValue();
        int darker = ColorUtil.toMCColor(new Color(
                (int) (SENIN_COLOR.getRed() * 0.25f),
                (int) (SENIN_COLOR.getGreen() * 0.25f),
                (int) (SENIN_COLOR.getBlue() * 0.25f))).getValue();
        String text = (int) this.seninMana + "/" + (int) this.maxSeninMana;
        GuiUtils.centeredTextOutlined(guiGraphics, this.minecraft.font, text,
                screenMid - OFFSET - (barWidth / 2), y - 4, color, darker);
    }

    private void setColor(Color color) {
        RenderSystem.setShaderColor(color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                1.0f);
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
