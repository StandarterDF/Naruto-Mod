package com.sekwah.narutomod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The blocks of the sacred tree: one huge trunk, a handful of broad branches, the clearing it stands
 * in, and the chest between its roots.
 *
 * <p>Everything is vanilla blocks - dark oak for the trunk, oak leaves for the crown, mossy
 * cobblestone for the old stones around it, and flowers for the ground. Leaves are placed
 * {@code persistent} so the crown does not rot away when a player chops a branch, and the whole tree
 * is generated from one seed, so it looks the same when a chunk is revisited.</p>
 */
public class SacredTreePiece extends StructurePiece {

    /** How far the bounding box reaches below the ground, for roots and the buried stones. */
    private static final int DEPTH = 4;

    /** Radius of the clearing. */
    private static final int CLEARING = 7;

    /** Loot found between the roots. */
    private static final ResourceLocation LOOT = new ResourceLocation("narutomod", "chests/sacred_tree");

    private static final BlockState LOG = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState STONE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();

    /** The flower palette of the clearing. */
    private static final BlockState[] FLOWERS = {
            Blocks.POPPY.defaultBlockState(),
            Blocks.DANDELION.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState()
    };

    public SacredTreePiece(BlockPos center) {
        super(NarutoWorldGen.SACRED_TREE_PIECE.get(), 0, new BoundingBox(
                center.getX() - SacredTreeStructure.RADIUS,
                center.getY() - DEPTH,
                center.getZ() - SacredTreeStructure.RADIUS,
                center.getX() + SacredTreeStructure.RADIUS,
                center.getY() + SacredTreeStructure.HEIGHT,
                center.getZ() + SacredTreeStructure.RADIUS));
    }

    public SacredTreePiece(CompoundTag tag) {
        super(NarutoWorldGen.SACRED_TREE_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // The bounding box is the whole state: the tree is regenerated from the seed.
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // postProcess is called once per intersecting chunk with the same piece, so the random
        // numbers of the worldgen pass are different every time. A layout drawn from those would
        // place a different branch in every chunk; a seed taken from the piece position makes the
        // tree identical in all of them.
        RandomSource layout = RandomSource.create(this.boundingBox.getCenter().asLong());
        BlockPos center = this.boundingBox.getCenter();
        int groundY = this.boundingBox.minY() + DEPTH;
        int cx = center.getX();
        int cz = center.getZ();

        this.prepareClearing(level, box, layout, cx, groundY, cz);
        int height = 15 + layout.nextInt(4);
        this.buildTrunk(level, box, layout, cx, groundY, cz, height);
        this.buildCrown(level, box, layout, cx, groundY + height, cz);
        this.placeStones(level, box, layout, cx, groundY, cz);
        this.placeFlowers(level, box, layout, cx, groundY, cz);
        this.placeChest(level, box, layout, cx + 3, groundY, cz);
    }

    /** Flattens and opens up a round clearing so the tree is not buried in the forest. */
    private void prepareClearing(WorldGenLevel level, BoundingBox box, RandomSource random,
                                 int cx, int groundY, int cz) {
        for (int dx = -CLEARING; dx <= CLEARING; dx++) {
            for (int dz = -CLEARING; dz <= CLEARING; dz++) {
                if (dx * dx + dz * dz > CLEARING * CLEARING) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                this.set(level, GRASS, x, groundY - 1, z, box);
                this.set(level, DIRT, x, groundY - 2, z, box);
                // Clear the lower forest so the trunk is visible from the ground up.
                for (int dy = 0; dy < 5; dy++) {
                    this.set(level, Blocks.AIR.defaultBlockState(), x, groundY + dy, z, box);
                }
            }
        }
    }

    private void buildTrunk(WorldGenLevel level, BoundingBox box, RandomSource random,
                            int cx, int groundY, int cz, int height) {
        // A 3x3 trunk for most of its height, then a narrower 2x2 top: the wide base reads as an
        // old tree rather than a column of logs.
        int wide = height - 5;
        for (int y = 0; y < height; y++) {
            int half = y < wide ? 1 : 0;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    this.set(level, LOG, cx + dx, groundY + y, cz + dz, box);
                }
            }
        }
        // Roots crawling out of the base.
        for (int i = 0; i < 7; i++) {
            int dx = random.nextInt(9) - 4;
            int dz = random.nextInt(9) - 4;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            this.set(level, LOG, cx + dx, groundY - 1, cz + dz, box);
            this.set(level, COARSE, cx + dx + Integer.signum(dx), groundY - 1,
                    cz + dz + Integer.signum(dz), box);
        }
    }

    /** Branches out from the top of the trunk, each ending in its own clump of leaves. */
    private void buildCrown(WorldGenLevel level, BoundingBox box, RandomSource random,
                            int cx, int topY, int cz) {
        int branches = 4 + random.nextInt(2);
        for (int i = 0; i < branches; i++) {
            double angle = (Math.PI * 2.0d / branches) * i + random.nextDouble() * 0.7d;
            int length = 3 + random.nextInt(3);
            int x = cx;
            int y = topY - 4 + random.nextInt(3);
            int z = cz;
            for (int step = 0; step < length; step++) {
                x += (int) Math.round(Math.cos(angle));
                z += (int) Math.round(Math.sin(angle));
                if (step > 0 && step % 2 == 0) {
                    y++;
                }
                this.set(level, LOG, x, y, z, box);
            }
            this.growLeafBlob(level, box, random, x, y + 1, z, 3 + random.nextInt(2));
        }
        // The crown itself, in three overlapping clumps so the top is broad and uneven.
        this.growLeafBlob(level, box, random, cx, topY, cz, 6);
        this.growLeafBlob(level, box, random, cx - 4, topY - 2, cz, 4);
        this.growLeafBlob(level, box, random, cx + 4, topY - 2, cz + 2, 4);
        this.growLeafBlob(level, box, random, cx, topY - 2, cz - 4, 4);
    }

    private void growLeafBlob(WorldGenLevel level, BoundingBox box, RandomSource random,
                              int cx, int cy, int cz, int radius) {
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > radiusSq) {
                        continue;
                    }
                    // The outer shell is thinned out, which is what makes a leaf blob look like a
                    // crown instead of a ball.
                    if (distanceSq > radiusSq - 3 && random.nextFloat() < 0.35f) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    BlockState current = this.stateAt(level, x, y, z, box);
                    if (current.isAir() || current.canBeReplaced()) {
                        this.set(level, LEAVES, x, y, z, box);
                    }
                }
            }
        }
    }

    /** Old stones at the edge of the clearing, some bare, some under the moss. */
    private void placeStones(WorldGenLevel level, BoundingBox box, RandomSource random,
                             int cx, int groundY, int cz) {
        int stones = 5 + random.nextInt(3);
        for (int i = 0; i < stones; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int radius = 5 + random.nextInt(2);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int z = cz + (int) Math.round(Math.sin(angle) * radius);
            int size = 1 + random.nextInt(2);
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    for (int dy = 0; dy <= size - Math.abs(dx) - Math.abs(dz); dy++) {
                        if (random.nextFloat() < 0.25f) {
                            continue;
                        }
                        this.set(level, STONE, x + dx, groundY - 1 + dy, z + dz, box);
                    }
                }
            }
        }
    }

    private void placeFlowers(WorldGenLevel level, BoundingBox box, RandomSource random,
                              int cx, int groundY, int cz) {
        for (int i = 0; i < 18; i++) {
            int dx = random.nextInt(CLEARING * 2 + 1) - CLEARING;
            int dz = random.nextInt(CLEARING * 2 + 1) - CLEARING;
            if (dx * dx + dz * dz > (CLEARING - 1) * (CLEARING - 1)) {
                continue;
            }
            BlockPos pos = new BlockPos(cx + dx, groundY, cz + dz);
            if (!box.isInside(pos)) {
                continue;
            }
            if (!this.stateAt(level, pos.getX(), pos.getY(), pos.getZ(), box).isAir()) {
                continue;
            }
            if (!level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            if (random.nextFloat() < 0.2f) {
                this.set(level, Blocks.TALL_GRASS.defaultBlockState(), pos.getX(), pos.getY(),
                        pos.getZ(), box);
            } else {
                this.set(level, FLOWERS[random.nextInt(FLOWERS.length)], pos.getX(), pos.getY(),
                        pos.getZ(), box);
            }
        }
    }

    private void placeChest(WorldGenLevel level, BoundingBox box, RandomSource random,
                            int x, int y, int z) {
        // A small root-flanked hollow: the chest sits on the grass and is framed by two logs so it
        // is noticed when the tree is approached from that side.
        this.set(level, Blocks.AIR.defaultBlockState(), x, y, z, box);
        this.set(level, Blocks.AIR.defaultBlockState(), x, y + 1, z, box);
        this.set(level, Blocks.AIR.defaultBlockState(), x, y + 2, z, box);
        this.chest(level, box, random, x, y, z);
        this.set(level, LOG, x, y, z - 1, box);
        this.set(level, LOG, x, y, z + 1, box);
        this.set(level, Blocks.DARK_OAK_SLAB.defaultBlockState(), x, y + 2, z, box);
    }

    /**
     * Places one block at absolute world coordinates.
     *
     * <p>{@link net.minecraft.world.level.levelgen.structure.StructurePiece#placeBlock} expects
     * coordinates relative to the piece's bounding box. This piece lays itself out in world
     * coordinates instead, so it writes through {@link WorldGenLevel#setBlock} directly; the box is
     * checked first so nothing spills outside the reserved area.</p>
     */
    private void set(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
        if (!box.isInside(x, y, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private BlockState stateAt(WorldGenLevel level, int x, int y, int z, BoundingBox box) {
        return box.isInside(x, y, z)
                ? level.getBlockState(new BlockPos(x, y, z))
                : Blocks.AIR.defaultBlockState();
    }

    /** A chest with the mod's loot table, placed at absolute coordinates. */
    private void chest(WorldGenLevel level, BoundingBox box, RandomSource random,
                       int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!box.isInside(pos)) {
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setLootTable(LOOT, random.nextLong());
        }
    }
}
