package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.client.ClientSensingData;
import com.sekwah.narutomod.network.s2c.ClientboundSensingPacket;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * The marker half of the chakra sense: a tag for every structure the server felt out around the
 * caster, drawn where the structure is.
 *
 * <p>It works the same way the substitution log marker does - the point is pushed through the world
 * projection the level render leaves behind, so the marker sits over the real place and slides off
 * screen when the structure is behind the caster. The creature glow, the other half of the sense, is
 * not a drawing at all and lives in {@code ClientSensingData}.</p>
 */
public class SensingGUI implements PlayerGUI {

    private final Minecraft minecraft;

    public SensingGUI(Minecraft mc) {
        this.minecraft = mc;
    }

    @Override
    public void render(GuiGraphics guiGraphics, Matrix4f worldMatrix, Vec3 cameraPos) {
        int alpha = (int) (ClientSensingData.getFade() * 255f);
        if (alpha <= 0 || cameraPos == null) {
            return;
        }
        List<ClientboundSensingPacket.Marker> markers = ClientSensingData.markers();
        if (markers.isEmpty() || worldMatrix == null) {
            return;
        }
        int halfWidth = guiGraphics.guiWidth() / 2;
        int halfHeight = guiGraphics.guiHeight() / 2;
        Font font = this.minecraft.font;
        int boxColour = (alpha * 0xAA / 255) << 24 | 0x33CCFF;
        int edgeColour = (alpha << 24) | 0x66E0FF;
        int nameColour = (alpha << 24) | 0xFFFFFF;
        int shadowColour = alpha << 24;

        for (ClientboundSensingPacket.Marker marker : markers) {
            Vec3 pos = Vec3.atCenterOf(marker.pos());
            Vector4f projected = new Vector4f((float) (pos.x - cameraPos.x), (float) (pos.y - cameraPos.y),
                    (float) (pos.z - cameraPos.z), 1.0f);
            projected.mul(worldMatrix);
            if (projected.w() <= 0.0f) {
                continue;
            }
            projected.div(projected.w());
            if (projected.z() <= 0.0f || projected.z() >= 1.0f) {
                continue;
            }
            int x = Math.round(halfWidth + projected.x() * halfWidth);
            int y = Math.round(halfHeight - projected.y() * halfHeight);
            double distance = cameraPos.distanceTo(pos);

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            guiGraphics.fill(x - 3, y - 3, x + 4, y + 4, boxColour);
            guiGraphics.fill(x - 4, y - 4, x + 5, y - 3, edgeColour);
            guiGraphics.fill(x - 4, y + 4, x + 5, y + 5, edgeColour);
            GuiUtils.centeredTextOutlined(guiGraphics, font, prettyName(marker.structure()), x, y - 22,
                    nameColour, shadowColour);
            GuiUtils.centeredTextOutlined(guiGraphics, font, Math.round(distance) + "m", x, y - 12,
                    edgeColour, shadowColour);
            poseStack.popPose();
        }
    }

    /** Turns {@code minecraft:village_plains} into {@code Village Plains} for the tag. */
    private static String prettyName(ResourceLocation structure) {
        StringBuilder name = new StringBuilder();
        for (String word : structure.getPath().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    @Override
    public void tick(Player player) {
        // The sense is read straight off the caster's data each frame, nothing to keep here.
    }
}
