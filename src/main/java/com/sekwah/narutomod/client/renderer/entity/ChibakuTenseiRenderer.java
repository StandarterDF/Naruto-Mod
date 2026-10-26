package com.sekwah.narutomod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.sekwah.narutomod.entity.jutsuprojectile.ChibakuTenseiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the Chibaku Tensei ball as what it is in 1.7.10: a sphere built out of the ground itself,
 * block by block, with the blocks drifting in from the field around it and, at the end, coming
 * apart again.
 *
 * <p>There is no smooth ball and no model anywhere in here. The ball is a voxel shell of the local
 * ground - the same block sizes, the same textures, the same stair-stepped silhouette Minecraft
 * draws everything else with - and, more importantly, the same blocks the biome actually has: the
 * palette is sampled from the ground around the cast point, and every piece is drawn with that
 * block's own biome tint, so a ball in a forest is grass and dirt and one in a desert is sand and
 * sandstone.</p>
 *
 * <p>Each block is torn out of the ground on a wide ring around the cast, flies slowly in a
 * straight line to its cell, and locks into the lattice. On impact every block is thrown outward
 * and falls, so the ball scatters instead of simply vanishing.</p>
 *
 * <p>Nothing is taken out of the world: the blocks are drawn, not moved. The ground under the cast
 * is untouched, which is the one rule this version of the technique has to keep.</p>
 */
public class ChibakuTenseiRenderer extends EntityRenderer<ChibakuTenseiEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Radius of the voxel shell in blocks of the lattice. The shell is scaled to the ball's radius,
     * so this is also how many blocks fit across the ball: 21 at any size. Ten keeps the ball at
     * around a thousand blocks, which is Minecraft's own scale and still cheap enough to draw.
     */
    private static final int LATTICE_RADIUS = 10;

    /** How long the last block takes to leave the ground, in ticks. */
    private static final int ASSEMBLY_TICKS = 200;

    /** Logs the palette and the launch ring once per ball; useful while tuning the look. */
    private static final boolean DEBUG_PALETTE = true;

    /** How far out on the ground a block is torn from, as a multiple of the ball's radius. */
    private static final float MIN_GATHER = 1.6f;
    private static final float GATHER_SPREAD = 1.8f;

    /** Every block of the shell: its cell, where it comes from, when it leaves and how it drifts. */
    private static final int COUNT;
    private static final float[] LAT_X;
    private static final float[] LAT_Y;
    private static final float[] LAT_Z;
    private static final float[] START_ANGLE;
    private static final float[] START_DISTANCE;
    private static final int[] DELAY;
    private static final int[] TRAVEL;
    private static final int[] ORIENT_Y;
    private static final int[] ORIENT_X;
    private static final int[] ORIENT_Z;
    private static final float[] TUMBLE_X;
    private static final float[] TUMBLE_Y;
    private static final float[] RANDOM;

    static {
        List<int[]> points = new ArrayList<>();
        double inner = (LATTICE_RADIUS - 1.0d) * (LATTICE_RADIUS - 1.0d);
        double outer = LATTICE_RADIUS * (double) LATTICE_RADIUS;
        for (int x = -LATTICE_RADIUS; x <= LATTICE_RADIUS; x++) {
            for (int y = -LATTICE_RADIUS; y <= LATTICE_RADIUS; y++) {
                for (int z = -LATTICE_RADIUS; z <= LATTICE_RADIUS; z++) {
                    double distance = x * x + y * y + z * z;
                    if (distance > inner && distance <= outer) {
                        points.add(new int[]{x, y, z});
                    }
                }
            }
        }

        COUNT = points.size();
        LAT_X = new float[COUNT];
        LAT_Y = new float[COUNT];
        LAT_Z = new float[COUNT];
        START_ANGLE = new float[COUNT];
        START_DISTANCE = new float[COUNT];
        DELAY = new int[COUNT];
        TRAVEL = new int[COUNT];
        ORIENT_Y = new int[COUNT];
        ORIENT_X = new int[COUNT];
        ORIENT_Z = new int[COUNT];
        TUMBLE_X = new float[COUNT];
        TUMBLE_Y = new float[COUNT];
        RANDOM = new float[COUNT];

        RandomSource random = RandomSource.create(0x5EED5EEDL);
        for (int i = 0; i < COUNT; i++) {
            int[] point = points.get(i);
            // Kept on the lattice instead of pushed onto a sphere: that is what makes the blocks
            // tile like real terrain and gives the ball Minecraft's stair-stepped silhouette.
            LAT_X[i] = point[0];
            LAT_Y[i] = point[1];
            LAT_Z[i] = point[2];
            RANDOM[i] = random.nextFloat();

            // Every block is torn out of the ground, on a wide ring around the cast point: wider
            // than the ball itself, so it gathers from the whole field and not from under its edge.
            START_ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
            START_DISTANCE[i] = MIN_GATHER + random.nextFloat() * GATHER_SPREAD;

            // Launches are mostly scattered, with only a slight bottom-first lean, so the ball closes
            // up all over rather than filling like a bucket.
            float height = (point[1] + LATTICE_RADIUS) / (2.0f * LATTICE_RADIUS);
            DELAY[i] = (int) (ASSEMBLY_TICKS * (0.25f * height + 0.75f * random.nextFloat()));
            // Slow drift: four to six seconds on the way in. Combined with the straight line this
            // is what makes the assembly read as a steady build and not as a launch.
            TRAVEL[i] = 84 + random.nextInt(42);
            // A random cube orientation, snapped to quarter turns so the block stays a clean cube
            // but its faces point anywhere. Without this a ball of grass shows its green tops only
            // on the very top and reads as dirt from every side.
            ORIENT_Y[i] = random.nextInt(4) * 90;
            ORIENT_X[i] = random.nextInt(4) * 90;
            ORIENT_Z[i] = random.nextInt(4) * 90;
            TUMBLE_X[i] = random.nextFloat() * 2.0f + 0.5f;
            TUMBLE_Y[i] = random.nextFloat() * 2.0f + 0.5f;
        }
    }

    private final BlockRenderDispatcher blockRenderer;

    /** Everything visual a particular ball needs, thrown away with the ball. */
    private final Map<ChibakuTenseiEntity, Ball> balls = new WeakHashMap<>();

    /** Rotation of the whole shell this frame, and scratch vectors so a thousand blocks cost no garbage. */
    private final Matrix4f shellRotation = new Matrix4f();
    private final Vector3f target = new Vector3f();
    private final Vector3f scratch = new Vector3f();

    public ChibakuTenseiRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ChibakuTenseiEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float radius = entity.getFullRadius();
        if (radius <= 0.1f) {
            return;
        }
        Ball ball = this.balls.get(entity);
        if (ball == null) {
            ball = this.buildBall(entity);
            if (ball == null) {
                // The client does not have the ground under the cast yet; try again next frame
                // instead of baking the fallback palette into the ball for its whole life.
                return;
            }
            this.balls.put(entity, ball);
        }
        float age = entity.renderAge(partialTicks);
        double centerY = entity.ballCenterY(partialTicks);
        float blockSize = radius / LATTICE_RADIUS;

        if (DEBUG_PALETTE && entity.getAge() % 20 == 0) {
            LOGGER.info("Chibaku render: age={} centerY={} entityY={} radius={}",
                    age, String.format("%.1f", centerY), String.format("%.1f", entity.getY()),
                    String.format("%.1f", radius));
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f toCamera = new Vector3f((float) (camera.x - entity.getX()),
                (float) (camera.y - centerY),
                (float) (camera.z - entity.getZ()));
        float cameraDistance = toCamera.length();
        float cullLimit = -0.35f * radius * cameraDistance;
        boolean cullBack = cameraDistance > radius * 1.5f;

        // The shell holds still while it is being built, so every block travels a dead straight line
        // into its cell; only once the ball is whole does it start to turn.
        double spin = Math.max(0.0d, age - 340.0d) * 0.35d;
        this.shellRotation.identity();
        this.shellRotation.rotateY((float) Math.toRadians(spin));
        this.shellRotation.rotateX((float) Math.toRadians(12.0d));

        // The entity sits on the ground, so the light handed in is the light down there. The ball
        // is up in the sky and has to be lit where it actually is, or a cast at night would come out
        // as a black lump.
        int light = LevelRenderer.getLightColor(entity.level(),
                BlockPos.containing(entity.getX(), centerY, entity.getZ()));

        // The whole ball drifts and swells rather than buzzing: a slow wobble, not a vibration.
        double shake = Math.min(0.25d, 0.03d + radius * 0.012d);
        poseStack.pushPose();
        // The entity itself never moves; the climb is a function of the age, evaluated here for
        // the exact frame. That is what keeps the ball sliding smoothly instead of stepping up a
        // position packet at a time.
        poseStack.translate(0.0d, centerY - entity.getY(), 0.0d);
        poseStack.translate(Math.sin(age * 0.12d) * shake,
                Math.cos(age * 0.09d) * shake * 0.5d,
                Math.sin(age * 0.14d + 1.7d) * shake);

        if (age >= ChibakuTenseiEntity.IMPACT_AGE) {
            this.drawScatter(poseStack, buffer, light, blockSize, age, centerY, entity, ball);
        } else {
            this.drawAssembly(entity, poseStack, buffer, light, blockSize, age, centerY,
                    cullBack, cullLimit, toCamera, ball);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** The ball being built: blocks drifting in from the field, then locking into the shell. */
    private void drawAssembly(ChibakuTenseiEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                              int light, float blockSize, float age, double centerY,
                              boolean cullBack, float cullLimit, Vector3f toCamera, Ball ball) {
        for (int i = 0; i < COUNT; i++) {
            double flight = (age - DELAY[i]) / TRAVEL[i];
            if (flight <= 0.0d) {
                // Still waiting its turn; not in the world yet.
                continue;
            }
            float ease = flight >= 1.0d ? 1.0f : (float) (flight * flight * (3.0d - 2.0d * flight));

            this.target.set(LAT_X[i] * blockSize, LAT_Y[i] * blockSize, LAT_Z[i] * blockSize);
            this.shellRotation.transformPosition(this.target);

            float x;
            float y;
            float z;
            if (ease >= 1.0f) {
                // Part of the ball: blocks on the far side are behind an opaque shell anyway.
                if (cullBack && this.target.dot(toCamera) < cullLimit) {
                    continue;
                }
                x = this.target.x;
                y = this.target.y;
                z = this.target.z;
            } else {
                // Straight up off the ground it was torn from, straight into its cell: no sideways
                // offset at either end, only the easing in time to start and stop gently.
                float startX = ball.startX[i] - (float) entity.getX();
                float startY = ball.startY[i] - (float) centerY;
                float startZ = ball.startZ[i] - (float) entity.getZ();
                x = Mth.lerp(ease, startX, this.target.x);
                y = Mth.lerp(ease, startY, this.target.y);
                z = Mth.lerp(ease, startZ, this.target.z);
                if (flight * TRAVEL[i] < 1.0d) {
                    // The moment it leaves the ground: a puff of dust where it was torn from.
                    entity.level().addParticle(ParticleTypes.CLOUD,
                            ball.startX[i], ball.startY[i] + 0.2d, ball.startZ[i],
                            0.0d, 0.03d, 0.0d);
                }
            }

            int index = ball.index(RANDOM[i]);
            this.drawBlock(poseStack, buffer, light, ball.states[index],
                    ball.tintR[index], ball.tintG[index], ball.tintB[index],
                    x, y, z, blockSize, i, 0.0f, 0.0f);
        }
    }

    /**
     * The ball coming apart on the ground: every block is thrown away from the centre and falls,
     * tumbling, so the technique ends in a pile of rubble rather than in a vanished entity.
     */
    private void drawScatter(PoseStack poseStack, MultiBufferSource buffer, int light, float blockSize,
                             float age, double centerY, ChibakuTenseiEntity entity, Ball ball) {
        float ground = (float) (entity.getStartY() + 0.5d - centerY);
        double t = age - ChibakuTenseiEntity.IMPACT_AGE;
        // In the last moments the rubble sinks away instead of vanishing in one frame.
        float fade = (float) Mth.clamp((ChibakuTenseiEntity.BURST_END - age) / 10.0d, 0.0f, 1.0f);

        for (int i = 0; i < COUNT; i++) {
            this.target.set(LAT_X[i] * blockSize, LAT_Y[i] * blockSize, LAT_Z[i] * blockSize);
            this.shellRotation.transformPosition(this.target);

            this.scratch.set(this.target).normalize();
            double spread = (0.30d + RANDOM[i] * 0.55d) * t;
            double lift = (0.10d + RANDOM[i] * 0.20d) * t;
            double fall = 0.006d * t * t;

            float x = (float) (this.target.x + this.scratch.x * spread);
            float y = (float) (this.target.y + this.scratch.y * spread * 0.6d + lift - fall);
            float z = (float) (this.target.z + this.scratch.z * spread);
            y = Math.max(y, ground);

            int index = ball.index(RANDOM[i]);
            this.drawBlock(poseStack, buffer, light, ball.states[index],
                    ball.tintR[index], ball.tintG[index], ball.tintB[index],
                    x, y, z, blockSize * fade, i,
                    TUMBLE_X[i] * 20.0f * (float) t, TUMBLE_Y[i] * 20.0f * (float) t);
        }
    }

    /**
     * One cube, oriented with the ball and spun by the given angles, in its own biome tint. The
     * piece's own quarter-turn keeps the block a clean cube while letting its faces point in any
     * direction, which is what makes grass and sandstone show up all over the ball.
     */
    private void drawBlock(PoseStack poseStack, MultiBufferSource buffer, int light, BlockState state,
                           float red, float green, float blue,
                           float x, float y, float z, float size, int piece,
                           float spinX, float spinY) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // The block is oriented with the ball, so landing on the shell never snaps it around.
        poseStack.mulPoseMatrix(this.shellRotation);
        poseStack.mulPose(Axis.YP.rotationDegrees(ORIENT_Y[piece]));
        poseStack.mulPose(Axis.XP.rotationDegrees(ORIENT_X[piece]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(ORIENT_Z[piece]));
        if (spinX != 0.0f || spinY != 0.0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(spinY));
            poseStack.mulPose(Axis.XP.rotationDegrees(spinX));
        }
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        BakedModel model = this.blockRenderer.getBlockModel(state);
        RenderType type = ItemBlockRenderTypes.getRenderType(state, false);
        this.blockRenderer.getModelRenderer().renderModel(poseStack.last(), buffer.getBuffer(type),
                state, model, red, green, blue, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * Works out what a particular ball is made of, and in what colour. The blocks are sampled from
     * the ground around the cast point - including the surface layer, so grass and sand count - and
     * each one remembers the biome tint of the place it was taken from.
     */
    @Nullable
    private Ball buildBall(ChibakuTenseiEntity entity) {
        Level level = entity.level();
        BlockPos base = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        RandomSource random = RandomSource.create(entity.getId() * 341873128712L + 132897987541L);

        Map<BlockState, Integer> counts = new HashMap<>();
        Map<BlockState, BlockPos> places = new HashMap<>();
        for (int i = 0; i < 200; i++) {
            int x = base.getX() + random.nextInt(41) - 20;
            int z = base.getZ() + random.nextInt(41) - 20;
            int ground = surfaceY(level, entity.getStartY(), x, z);
            // Half the samples are the surface itself - the block the place actually looks like,
            // whether it is grass, sand or snow - and half come from the layers just under it.
            int depth = random.nextBoolean() ? 0 : 1 + random.nextInt(3);
            BlockPos pos = new BlockPos(x, ground - 1 - depth, z);
            BlockState state = level.getBlockState(pos);
            if (!isGround(state, level, pos)) {
                continue;
            }
            counts.merge(state, 1, Integer::sum);
            places.putIfAbsent(state, pos.immutable());
        }
        if (counts.isEmpty()) {
            BlockPos under = BlockPos.containing(entity.getX(), entity.getStartY() - 1.0d, entity.getZ());
            boolean noChunk = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    under.getX(), under.getZ()) <= level.getMinBuildHeight();
            if (noChunk) {
                return null;
            }
        }

        List<BlockState> ordered = new ArrayList<>(counts.keySet());
        ordered.sort(Comparator.comparingInt((BlockState state) -> counts.get(state)).reversed());
        if (ordered.isEmpty()) {
            ordered.add(Blocks.STONE.defaultBlockState());
            ordered.add(Blocks.DIRT.defaultBlockState());
            ordered.add(Blocks.COBBLESTONE.defaultBlockState());
        }
        int size = Math.min(8, ordered.size());
        BlockState[] states = ordered.subList(0, size).toArray(new BlockState[0]);

        // How often each of those blocks actually occurs around the cast, so the ball keeps the mix
        // of the place instead of turning one third of everything into its rarest block.
        int total = 0;
        for (int i = 0; i < size; i++) {
            total += counts.getOrDefault(states[i], 1);
        }
        float[] cumulative = new float[size];
        float running = 0.0f;
        for (int i = 0; i < size; i++) {
            running += counts.getOrDefault(states[i], 1) / (float) total;
            cumulative[i] = running;
        }
        cumulative[size - 1] = 1.0f;

        BlockColors colors = Minecraft.getInstance().getBlockColors();
        float[] tintR = new float[size];
        float[] tintG = new float[size];
        float[] tintB = new float[size];
        for (int i = 0; i < size; i++) {
            BlockState state = states[i];
            int color = colors.getColor(state, level, places.getOrDefault(state, base), 0);
            tintR[i] = color == -1 ? 1.0f : (color >> 16 & 255) / 255.0f;
            tintG[i] = color == -1 ? 1.0f : (color >> 8 & 255) / 255.0f;
            tintB[i] = color == -1 ? 1.0f : (color & 255) / 255.0f;
        }

        // Where each block is torn from: the ground under its own launch spot, on a ring wider than
        // the ball. A block always starts on the actual terrain, never in the air.
        float radius = entity.getFullRadius();
        float[] startX = new float[COUNT];
        float[] startY = new float[COUNT];
        float[] startZ = new float[COUNT];
        for (int i = 0; i < COUNT; i++) {
            double distance = radius * START_DISTANCE[i];
            double x = entity.getX() + Math.cos(START_ANGLE[i]) * distance;
            double z = entity.getZ() + Math.sin(START_ANGLE[i]) * distance;
            int ground = surfaceY(level, entity.getStartY(), Mth.floor(x), Mth.floor(z));
            startX[i] = (float) x;
            startY[i] = ground + 0.5f;
            startZ[i] = (float) z;
        }

        if (DEBUG_PALETTE) {
            StringBuilder names = new StringBuilder();
            for (BlockState state : states) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            }
            float low = Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (float y : startY) {
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
            LOGGER.info("Chibaku Tensei: ground {}, launch ring {}..{} blocks, start heights {}..{}, palette: {}",
                    entity.getStartY(), MIN_GATHER * radius, (MIN_GATHER + GATHER_SPREAD) * radius,
                    low, high, names);
        }
        return new Ball(states, tintR, tintG, tintB, cumulative, startX, startY, startZ);
    }

    /**
     * The height of the ground at a column, with a fallback for the case the client does not have
     * the column loaded yet: {@code getHeight} answers with the bottom of the world there, which
     * used to send the whole ball - and its palette - under the bedrock.
     */
    private static int surfaceY(Level level, double fallback, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() || Math.abs(y - fallback) > 80.0d) {
            return Mth.floor(fallback);
        }
        return y;
    }

    /** Whether a block is the sort of thing a ball of ground would be made of. */
    private static boolean isGround(BlockState state, Level level, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        if (state.is(Blocks.BEDROCK) || !state.isSolidRender(level, pos)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness > 0.1f && hardness <= 50.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(ChibakuTenseiEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /**
     * The blocks a ball is made of, their biome tints, how often each occurs, and where each piece
     * is torn out of the ground.
     */
    private record Ball(BlockState[] states, float[] tintR, float[] tintG, float[] tintB,
                        float[] cumulative, float[] startX, float[] startY, float[] startZ) {

        /** Picks a block for a piece, weighted by how common it is in the ground around the cast. */
        int index(float roll) {
            for (int i = 0; i < this.cumulative.length; i++) {
                if (roll <= this.cumulative[i]) {
                    return i;
                }
            }
            return this.cumulative.length - 1;
        }
    }
}
