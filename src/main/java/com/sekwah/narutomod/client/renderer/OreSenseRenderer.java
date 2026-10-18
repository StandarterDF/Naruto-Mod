package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sekwah.narutomod.client.ClientOreSenseData;
import com.sekwah.narutomod.network.s2c.ClientboundOreSensePacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the boxes of an ore sense pulse, through the rock.
 *
 * <p>It borrows the same view matrix the chakra sense captured at {@code AFTER_PARTICLES}, and draws
 * after the level for the same reason: that is the one point where the depth buffer can be wiped
 * clean and the boxes drawn over the terrain instead of being hidden by it. Each mark is a wireframe
 * cube on its block, coloured by what it is. The whole picture fades out over the last second of the
 * pulse in {@link ClientOreSenseData}, so the boxes dissolve rather than blink off.</p>
 */
public final class OreSenseRenderer {

    private OreSenseRenderer() {
    }

    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !SensingEspRenderer.hasView()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        float alpha = ClientOreSenseData.alpha();
        List<ClientboundOreSensePacket.Marker> markers = ClientOreSenseData.markers();
        if (alpha <= 0f || markers.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f view = SensingEspRenderer.viewMatrix();

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            // The boxes are meant to be seen through walls, so the depth buffer is wiped and the
            // lines drawn into the clean buffer; nothing of the terrain can stand in front of them.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0f);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (ClientboundOreSensePacket.Marker marker : markers) {
                float[] rgb = colourOf(marker.kind());
                cube(builder, view,
                        marker.pos().getX() - cam.x, marker.pos().getY() - cam.y, marker.pos().getZ() - cam.z,
                        rgb[0], rgb[1], rgb[2], alpha);
            }
            BufferUploader.drawWithShader(builder.end());

            RenderSystem.lineWidth(1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** The twelve edges of one block-sized box. */
    private static void cube(BufferBuilder builder, Matrix4f matrix,
                             double x, double y, double z, float r, float g, float b, float a) {
        double x1 = x + 1.0d;
        double y1 = y + 1.0d;
        double z1 = z + 1.0d;

        // Bottom and top rings.
        line(builder, matrix, x, y, z, x1, y, z, r, g, b, a);
        line(builder, matrix, x1, y, z, x1, y, z1, r, g, b, a);
        line(builder, matrix, x1, y, z1, x, y, z1, r, g, b, a);
        line(builder, matrix, x, y, z1, x, y, z, r, g, b, a);
        line(builder, matrix, x, y1, z, x1, y1, z, r, g, b, a);
        line(builder, matrix, x1, y1, z, x1, y1, z1, r, g, b, a);
        line(builder, matrix, x1, y1, z1, x, y1, z1, r, g, b, a);
        line(builder, matrix, x, y1, z1, x, y1, z, r, g, b, a);

        // The four uprights.
        line(builder, matrix, x, y, z, x, y1, z, r, g, b, a);
        line(builder, matrix, x1, y, z, x1, y1, z, r, g, b, a);
        line(builder, matrix, x1, y, z1, x1, y1, z1, r, g, b, a);
        line(builder, matrix, x, y, z1, x, y1, z1, r, g, b, a);
    }

    private static void line(BufferBuilder builder, Matrix4f matrix,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        builder.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
    }

    private static float[] colourOf(byte kind) {
        return switch (kind) {
            case ClientboundOreSensePacket.KIND_ANCIENT -> new float[]{0.85f, 0.45f, 0.30f};
            case ClientboundOreSensePacket.KIND_CHEST -> new float[]{0.45f, 0.85f, 1.0f};
            case ClientboundOreSensePacket.KIND_SPAWNER -> new float[]{0.70f, 0.45f, 1.0f};
            default -> new float[]{1.0f, 0.84f, 0.28f};
        };
    }
}
