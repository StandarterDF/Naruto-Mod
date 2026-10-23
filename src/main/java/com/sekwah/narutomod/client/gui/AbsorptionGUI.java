package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * The row of borrowed chakra pips drawn above the chakra bar.
 *
 * <p>One pip per charge held, grouped by nature and coloured like the technique it came from: orange
 * for fire, blue for water, green for wind. It says two things at once - how much is in the palm and
 * what the next technique of each nature is worth - which a single number could not.</p>
 *
 * <p>It only shows up once something has been swallowed, for the same reason the nature energy bar
 * only appears once there is nature energy: an empty row above the bars is just clutter.</p>
 */
public class AbsorptionGUI implements PlayerGUI {

    private static final int PIP = 5;
    private static final int GAP = 2;
    private static final int GROUP_GAP = 6;

    /** Bottom edge of the row, in GUI pixels from the top. Kept clear of the senin bar at -44. */
    private static final int OFFSET_Y = 66;

    private static final Color FIRE = new Color(255, 122, 46);
    private static final Color WATER = new Color(64, 143, 255);
    private static final Color WIND = new Color(90, 219, 132);

    private final Minecraft minecraft;

    private int fire;
    private int water;
    private int wind;
    private boolean enabled;

    public AbsorptionGUI(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(GuiGraphics guiGraphics, Matrix4f worldMatrix, Vec3 cameraPos) {
        if (!this.enabled) {
            return;
        }
        int total = this.fire + this.water + this.wind;
        if (total <= 0) {
            return;
        }

        int width = this.groupWidth(this.fire) + this.groupWidth(this.water) + this.groupWidth(this.wind);
        int gaps = (this.fire > 0 ? 1 : 0) + (this.water > 0 ? 1 : 0) + (this.wind > 0 ? 1 : 0);
        if (gaps > 1) {
            width += GROUP_GAP * (gaps - 1);
        }

        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 - width / 2;
        int y = screenHeight - OFFSET_Y;

        x = this.drawGroup(guiGraphics, x, y, this.fire, FIRE);
        x = this.drawGroup(guiGraphics, x, y, this.water, WATER);
        this.drawGroup(guiGraphics, x, y, this.wind, WIND);
    }

    /** Draws one nature's pips and returns where the next group starts. */
    private int drawGroup(GuiGraphics guiGraphics, int x, int y, int count, Color colour) {
        if (count <= 0) {
            return x;
        }
        int argb = 0xFF000000 | (colour.getRGB() & 0xFFFFFF);
        int dark = 0xFF000000 | (colour.darker().darker().getRGB() & 0xFFFFFF);
        for (int i = 0; i < count; i++) {
            // A dark rim so the pip reads against any background.
            guiGraphics.fill(x - 1, y - 1, x + PIP + 1, y + PIP + 1, dark);
            guiGraphics.fill(x, y, x + PIP, y + PIP, argb);
            x += PIP + GAP;
        }
        return x + GROUP_GAP - GAP;
    }

    private int groupWidth(int count) {
        return count <= 0 ? 0 : count * (PIP + GAP) - GAP;
    }

    @Override
    public void tick(Player player) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            this.enabled = ninjaData.isNinjaModeEnabled();
            AbsorptionData data = ninjaData.getAbsorptionData();
            this.fire = data.charges(AbsorptionData.Nature.FIRE);
            this.water = data.charges(AbsorptionData.Nature.WATER);
            this.wind = data.charges(AbsorptionData.Nature.WIND);
        });
    }
}
