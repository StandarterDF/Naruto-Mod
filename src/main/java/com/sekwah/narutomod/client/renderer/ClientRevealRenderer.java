package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.client.ClientRevealData;
import com.sekwah.narutomod.network.s2c.ClientboundRevealPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws what the Byakugan found behind the stone: voids, water, lava and mechanisms.
 *
 * <p>Same trick as the ore pulse, and for the same reason: the depth buffer is wiped at
 * {@code AFTER_LEVEL} so the boxes read as seeing <em>through</em> the wall rather than lying on it.
 * The colour says what was found, and the picture fades out with {@link ClientRevealData} so a
 * glance does not leave a permanent overlay on the world.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientRevealRenderer {

    private ClientRevealRenderer() {
    }

    /** Ticks the client-side picture down once per frame-worth of client time. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientRevealData.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !SensingEspRenderer.hasView()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        float alpha = ClientRevealData.alpha();
        List<ClientboundRevealPacket.Marker> markers = ClientRevealData.markers();
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
            for (ClientboundRevealPacket.Marker marker : markers) {
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

    private static float[] colourOf(byte kind) {
        return switch (kind) {
            case ClientboundRevealPacket.KIND_WATER -> new float[]{0.30f, 0.65f, 1.00f};
            case ClientboundRevealPacket.KIND_LAVA -> new float[]{1.00f, 0.38f, 0.12f};
            case ClientboundRevealPacket.KIND_MECHANISM -> new float[]{0.55f, 1.00f, 0.45f};
            default -> new float[]{0.72f, 0.82f, 1.00f};
        };
    }

    /** The twelve edges of one block-sized box. */
    private static void cube(BufferBuilder builder, Matrix4f matrix,
                             double x, double y, double z, float r, float g, float b, float a) {
        double x1 = x + 1.0d;
        double y1 = y + 1.0d;
        double z1 = z + 1.0d;

        line(builder, matrix, x, y, z, x1, y, z, r, g, b, a);
        line(builder, matrix, x1, y, z, x1, y, z1, r, g, b, a);
        line(builder, matrix, x1, y, z1, x, y, z1, r, g, b, a);
        line(builder, matrix, x, y, z1, x, y, z, r, g, b, a);
        line(builder, matrix, x, y1, z, x1, y1, z, r, g, b, a);
        line(builder, matrix, x1, y1, z, x1, y1, z1, r, g, b, a);
        line(builder, matrix, x1, y1, z1, x, y1, z1, r, g, b, a);
        line(builder, matrix, x, y1, z1, x, y1, z, r, g, b, a);
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
}
