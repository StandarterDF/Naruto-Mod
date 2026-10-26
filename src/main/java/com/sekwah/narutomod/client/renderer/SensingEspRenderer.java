package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sekwah.narutomod.client.ClientSensingData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws what the chakra sense is feeling out, in the world and through walls.
 *
 * <p>Every creature the caster feels is drawn by how far it has resolved. A creature that has only
 * just begun to appear is a thin scattering of dim motes that hang on the outline of its body; as
 * the caster's mastery of the sense grows the motes thicken until, at full resolution, the creature's
 * own body is drawn in its place. Because the whole point is feeling through obstacles, none of this
 * is hidden by the terrain: the motes are drawn with the depth test off, and the bodies are drawn
 * after the depth buffer has been wiped, so a wall in front of a creature does not hide it.</p>
 *
 * <p>This draws at {@code AFTER_LEVEL}, the very end of the level pass: it is the only point where the
 * depth buffer can be wiped for the bodies without breaking the translucent or weather passes that
 * still use it. The event pose stack there is the projection rather than the view, so the view is
 * captured earlier from {@code AFTER_PARTICLES} (which is handed the level pose stack) and reused;
 * with that view and the modelview pinned to identity vertices are placed at {@code world - camera},
 * exactly like the vanilla world pass does.</p>
 */
public final class SensingEspRenderer {

    private SensingEspRenderer() {
    }

    /** A ceiling on how many creatures are drawn as full bodies at once, nearest first. */
    private static final int MAX_MODELS = 24;

    /** A ceiling on how many creatures are drawn as particle shells at once, nearest first. */
    private static final int MAX_SHELLS = 24;

    /** A ceiling on the motes one shell may spend, even for a creature with a lot of model parts. */
    private static final int MAX_MOTES_PER_ENTITY = 120;

    /**
     * The camera's view rotation, lifted from the pose stack of the world pass. The stage that hands
     * out the view is not the one where the depth buffer can be cleared, and the view does not change
     * between them in a frame, so it is captured early and used at the end.
     */
    private static final Matrix4f VIEW = new Matrix4f();
    private static boolean hasView;

    /** The view captured this frame, for the other after-level renderers (the ore sense). */
    public static Matrix4f viewMatrix() {
        return VIEW;
    }

    public static boolean hasView() {
        return hasView;
    }

    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // AFTER_PARTICLES is dispatched with the level pose stack, which at that point is the
            // camera view rotation and nothing else. AFTER_LEVEL, by contrast, gets the projection.
            VIEW.set(event.getPoseStack().last().pose());
            hasView = true;
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !hasView) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        float fade = ClientSensingData.getFade();
        if (fade <= 0f) {
            return;
        }
        Map<LivingEntity, Float> sensed = ClientSensingData.sensed();
        if (sensed.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(VIEW);

        List<Map.Entry<LivingEntity, Float>> entries = new ArrayList<>(sensed.entrySet());
        // Nearest first: if there are more creatures than the model budget allows, the ones the
        // caster is most likely to care about are the ones that get a body.
        entries.sort(Comparator.comparingDouble(entry -> entry.getKey().distanceToSqr(cam)));

        // With the modelview pinned to identity the pose stack carries the whole transform itself:
        // vertex = view * (world - camera), then projection.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            drawShells(mc, pose, camera, cam, partial, fade, entries);
            drawBodies(mc, pose, cam, partial, fade, entries);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /**
     * Draws the dim motes that stand in for a creature that has not resolved all the way yet.
     *
     * <p>The motes are placed on the creature's own model, not on its bounding box: the renderer is
     * run once into a vertex sink and the positions it would have drawn are collected, so the shell
     * traces the actual shape - head, legs, held sword and all. A beginner sees only a share of them
     * and a master sees nearly all, which is what makes one creature recognisable before another.</p>
     *
     * <p>While the sense is dissolving even the fully resolved bodies fall back to motes, so the
     * whole picture comes apart as specks of chakra rather than a body blinking out.</p>
     */
    private static void drawShells(Minecraft mc, PoseStack pose, Camera camera, Vec3 cam, float partial,
                                   float fade, List<Map.Entry<LivingEntity, Float>> entries) {
        boolean dissolving = fade < 1f;
        Matrix4f matrix = pose.last().pose();
        Vector3f left = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        long time = mc.level.getGameTime();

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        int shells = 0;
        for (Map.Entry<LivingEntity, Float> entry : entries) {
            LivingEntity entity = entry.getKey();
            float intensity = entry.getValue();
            if (entity.isRemoved() || (intensity >= 1f && !dissolving)) {
                continue;
            }
            if (shells++ >= MAX_SHELLS) {
                break;
            }
            // Even the faintest first reading keeps a handful of motes on the creature, so a new
            // sense never looks broken; past that the share of the model drawn follows the intensity.
            float density = dissolving ? 1f : Math.max(intensity, 0.15f);
            float alpha = fade * (0.3f + 0.7f * density);
            float[] rgb = colourOf(entity);
            double distance = Math.sqrt(entity.distanceToSqr(cam));
            // Motes grow with distance so a creature a few chunks away still reads as a speck
            // instead of a single pixel.
            double size = 0.02d + distance * 0.004d;
            RandomSource random = RandomSource.create(entity.getId() * 341873128712L + time / 4L);

            List<Vec3> shape = shapePoints(mc, entity, partial, cam);
            if (shape.isEmpty()) {
                // Nothing could be captured - an invisible creature, or a renderer that refuses to
                // run outside its usual pass. Fall back to the old box outline rather than nothing.
                AABB box = interpolatedBox(entity, partial);
                int count = 6 + (int) (density * 30f);
                for (int i = 0; i < count; i++) {
                    Vec3 point = surfacePoint(box, random);
                    emitMote(builder, matrix, point.x - cam.x, point.y - cam.y, point.z - cam.z,
                            right, up, size, rgb[0], rgb[1], rgb[2], alpha);
                    any = true;
                }
                continue;
            }

            // The points already sit on the model's surface, deduplicated down to its corners. Only
            // a share is drawn at low intensity; as the sense resolves the share climbs to all of
            // them and the full body takes over from the shell.
            int stride = Math.max(1, shape.size() / MAX_MOTES_PER_ENTITY);
            for (int i = random.nextInt(stride); i < shape.size(); i += stride) {
                if (random.nextFloat() > density) {
                    continue;
                }
                Vec3 point = shape.get(i);
                emitMote(builder, matrix, point.x, point.y, point.z,
                        right, up, size, rgb[0], rgb[1], rgb[2], alpha);
                any = true;
            }
        }
        if (!any) {
            builder.end();
            return;
        }
        // Drawn after every depth-tested thing in the level with the depth test off, so the shell is
        // seen through whatever stands between the caster and the creature.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Runs the creature's own renderer into a vertex sink and collects the camera-relative
     * positions it would have drawn. This is the only way to get the real model shape without
     * reimplementing every renderer: the sink sees exactly what the model emits, after all of the
     * entity, pose and animation transforms.
     */
    @SuppressWarnings("unchecked")
    private static List<Vec3> shapePoints(Minecraft mc, LivingEntity entity, float partial, Vec3 cam) {
        EntityRenderer<LivingEntity> renderer =
                (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(entity);
        ShapeCapture capture = new ShapeCapture();
        PoseStack pose = new PoseStack();
        Vec3 position = entity.getPosition(partial);
        // A fresh identity pose, translated only to the entity's camera-relative place: the recorded
        // points are then camera-relative world positions, ready for the view matrix above.
        pose.translate(position.x - cam.x, position.y - cam.y, position.z - cam.z);
        float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
        try {
            renderer.render(entity, yaw, partial, pose, capture.source(), LightTexture.FULL_BRIGHT);
        } catch (RuntimeException ignored) {
            // A third-party renderer is not required to like being invoked outside the usual entity
            // pass; the shell simply falls back to the box for that creature.
            return List.of();
        }
        return capture.points();
    }

    /** Draws fully resolved creatures as their own bodies, in place, over the terrain. */
    private static void drawBodies(Minecraft mc, PoseStack pose, Vec3 cam, float partial, float fade,
                                   List<Map.Entry<LivingEntity, Float>> entries) {
        if (fade < 1f) {
            return;
        }
        List<LivingEntity> bodies = new ArrayList<>();
        for (Map.Entry<LivingEntity, Float> entry : entries) {
            if (entry.getValue() >= 1f && !entry.getKey().isRemoved()) {
                bodies.add(entry.getKey());
                if (bodies.size() >= MAX_MODELS) {
                    break;
                }
            }
        }
        if (bodies.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        // The light layer is normally only bound while the vanilla pass needs it, so it is brought
        // back for the bodies; they are lit full-bright anyway, but the sampler still has to point
        // at the lightmap or the entity shader reads whatever texture happened to be bound last.
        LightTexture light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        try {
            // The bodies are meant to be seen through the terrain, so the depth buffer is wiped
            // clean first: without that a wall in front of a creature would hide it and full ESP
            // would be no more than the outline.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            for (LivingEntity entity : bodies) {
                Vec3 p = entity.getPosition(partial);
                float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                mc.getEntityRenderDispatcher().render(entity,
                        p.x - cam.x, p.y - cam.y, p.z - cam.z,
                        yaw, partial, pose, buffer, LightTexture.FULL_BRIGHT);
            }
            buffer.endBatch();
        } finally {
            light.turnOffLightLayer();
        }
    }

    private static AABB interpolatedBox(LivingEntity entity, float partial) {
        Vec3 p = entity.getPosition(partial);
        return entity.getBoundingBox().move(p.x - entity.getX(), p.y - entity.getY(), p.z - entity.getZ());
    }

    /** A random point on one of the six faces of the box, so the motes trace an outline, not a fog. */
    private static Vec3 surfacePoint(AABB box, RandomSource random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        return switch (random.nextInt(6)) {
            case 0 -> new Vec3(box.minX, Mth.lerp(u, box.minY, box.maxY), Mth.lerp(v, box.minZ, box.maxZ));
            case 1 -> new Vec3(box.maxX, Mth.lerp(u, box.minY, box.maxY), Mth.lerp(v, box.minZ, box.maxZ));
            case 2 -> new Vec3(Mth.lerp(u, box.minX, box.maxX), box.minY, Mth.lerp(v, box.minZ, box.maxZ));
            case 3 -> new Vec3(Mth.lerp(u, box.minX, box.maxX), box.maxY, Mth.lerp(v, box.minZ, box.maxZ));
            case 4 -> new Vec3(Mth.lerp(u, box.minX, box.maxX), Mth.lerp(v, box.minY, box.maxY), box.minZ);
            default -> new Vec3(Mth.lerp(u, box.minX, box.maxX), Mth.lerp(v, box.minY, box.maxY), box.maxZ);
        };
    }

    /** Green for ordinary creatures, red for hostile ones, aqua for other people. */
    private static float[] colourOf(LivingEntity entity) {
        Player self = net.minecraft.client.Minecraft.getInstance().player;
        if (self != null && entity != self && entity.isAlliedTo(self)) {
            return new float[]{0.55f, 1.0f, 0.55f};
        }
        if (entity instanceof Player) {
            return new float[]{0.45f, 0.85f, 1.0f};
        }
        if (entity instanceof Enemy) {
            return new float[]{1.0f, 0.35f, 0.35f};
        }
        return new float[]{0.4f, 1.0f, 0.5f};
    }

    /** Emits one camera-facing quad: the mote is a billboard, not a flat sheet. */
    private static void emitMote(BufferBuilder builder, Matrix4f matrix, double x, double y, double z,
                                 Vec3 right, Vec3 up, double size, float r, float g, float b, float a) {
        builder.vertex(matrix, (float) (x - (right.x + up.x) * size), (float) (y - (right.y + up.y) * size),
                (float) (z - (right.z + up.z) * size)).color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (x + (right.x - up.x) * size), (float) (y + (right.y - up.y) * size),
                (float) (z + (right.z - up.z) * size)).color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (x + (right.x + up.x) * size), (float) (y + (right.y + up.y) * size),
                (float) (z + (right.z + up.z) * size)).color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (x - (right.x - up.x) * size), (float) (y - (right.y - up.y) * size),
                (float) (z - (right.z - up.z) * size)).color(r, g, b, a).endVertex();
    }

    /**
     * A vertex consumer that keeps only where it was asked to draw, deduplicated to the model's
     * corners. Everything else - colour, texture, normal, light - is irrelevant to a particle shell.
     */
    private static final class ShapeCapture implements VertexConsumer {

        private final List<Vec3> points = new ArrayList<>();
        private final Set<Vec3> unique = new HashSet<>();
        private final Vector3f scratch = new Vector3f();

        MultiBufferSource source() {
            return type -> this;
        }

        List<Vec3> points() {
            return this.points;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            add(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            matrix.transformPosition(x, y, z, this.scratch);
            add(this.scratch.x(), this.scratch.y(), this.scratch.z());
            return this;
        }

        private void add(double x, double y, double z) {
            // A cube repeats each corner once per face; rounding to a hundredth of a block folds
            // those copies (and parts that happen to overlap) into one point.
            Vec3 point = new Vec3(Math.round(x * 100d) / 100d,
                    Math.round(y * 100d) / 100d, Math.round(z * 100d) / 100d);
            if (this.unique.add(point)) {
                this.points.add(point);
            }
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
