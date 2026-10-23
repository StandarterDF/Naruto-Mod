package com.sekwah.narutomod.world;

import com.sekwah.narutomod.block.NarutoBlocks;
import com.sekwah.narutomod.block.weapons.PaperBombBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds one {@link NarutoDungeonStructure.Variant} out of vanilla blocks.
 *
 * <p>Nothing here uses structure templates: the buildings are simple enough to be drawn block by
 * block, and doing it in code keeps the mod free of {@code .nbt} assets that would have to be
 * authored in an editor. Every random choice goes through the piece's {@link RandomSource}, so the
 * same seed always produces the same ruin.</p>
 */
public class NarutoDungeonPiece extends StructurePiece {

    private static final int DEPTH = 4;
    private static final String VARIANT_TAG = "Variant";

    private static final ResourceLocation LOOT = new ResourceLocation("narutomod", "chests/dungeon");

    private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState SLAB = Blocks.DARK_OAK_SLAB.defaultBlockState();
    private static final BlockState ROOF = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState FENCE = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState TORCH = Blocks.TORCH.defaultBlockState();
    private static final BlockState CRAFTING = Blocks.CRAFTING_TABLE.defaultBlockState();
    private static final BlockState BARREL = Blocks.BARREL.defaultBlockState();
    private static final BlockState WEB = Blocks.COBWEB.defaultBlockState();
    private static final BlockState PUMPKIN = Blocks.CARVED_PUMPKIN.defaultBlockState();

    private final NarutoDungeonStructure.Variant variant;

    public NarutoDungeonPiece(BlockPos center, NarutoDungeonStructure.Variant variant) {
        super(NarutoWorldGen.DUNGEON_PIECE.get(), 0, boxFor(center, variant));
        this.variant = variant;
    }

    public NarutoDungeonPiece(CompoundTag tag) {
        super(NarutoWorldGen.DUNGEON_PIECE.get(), tag);
        this.variant = byName(tag.getString(VARIANT_TAG));
    }

    private static NarutoDungeonStructure.Variant byName(String name) {
        for (NarutoDungeonStructure.Variant value : NarutoDungeonStructure.Variant.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return NarutoDungeonStructure.Variant.RUINED_TRAINING_GROUNDS;
    }

    private static BoundingBox boxFor(BlockPos center, NarutoDungeonStructure.Variant variant) {
        int radius = variant == NarutoDungeonStructure.Variant.ABANDONED_OUTPOST ? 11 : 16;
        int height = variant == NarutoDungeonStructure.Variant.ABANDONED_OUTPOST ? 16 : 10;
        return new BoundingBox(center.getX() - radius, center.getY() - DEPTH, center.getZ() - radius,
                center.getX() + radius, center.getY() + height, center.getZ() + radius);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(VARIANT_TAG, this.variant.getSerializedName());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // A structure piece is generated once per intersecting chunk, and the worldgen random is not
        // shared between those passes. The layout is therefore drawn from a seed that depends only
        // on the piece, so every chunk sees the same ruin.
        RandomSource layout = RandomSource.create(this.boundingBox.getCenter().asLong());
        BlockPos center = this.boundingBox.getCenter();
        int groundY = this.boundingBox.minY() + DEPTH;
        if (this.variant == NarutoDungeonStructure.Variant.ABANDONED_OUTPOST) {
            this.buildOutpost(level, box, layout, center.getX(), groundY, center.getZ());
        } else {
            this.buildTrainingGrounds(level, box, layout, center.getX(), groundY, center.getZ());
        }
    }

    // ------------------------------------------------------------------ training grounds

    private void buildTrainingGrounds(WorldGenLevel level, BoundingBox box, RandomSource random,
                                      int cx, int groundY, int cz) {
        int radius = 14;
        this.clearAndFloor(level, box, random, cx, groundY, cz, radius);

        // A broken perimeter wall. Gaps are what makes it read as a ruin rather than a fort.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue;
                }
                boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                int height = corner ? 4 : random.nextInt(4);
                for (int y = 0; y < height; y++) {
                    this.set(level, box, cx + dx, groundY + y, cz + dz, this.weatheredBrick(random));
                }
                if (corner) {
                    this.set(level, box, cx + dx, groundY + 4, cz + dz, CHISELED);
                }
            }
        }

        // Two inner walls, as if the yard had been split into training pens.
        for (int dx = -6; dx <= 2; dx++) {
            this.set(level, box, cx + dx, groundY, cz - 5, this.weatheredBrick(random));
            if (random.nextBoolean()) {
                this.set(level, box, cx + dx, groundY + 1, cz - 5, this.weatheredBrick(random));
            }
        }
        for (int dz = -5; dz <= 3; dz++) {
            this.set(level, box, cx + 6, groundY, cz + dz, this.weatheredBrick(random));
        }

        // Training posts: a fence post with a carved pumpkin head.
        int[][] dummies = {{-8, 4}, {5, -1}, {8, 7}};
        for (int[] dummy : dummies) {
            int x = cx + dummy[0];
            int z = cz + dummy[1];
            this.set(level, box, x, groundY, z, FENCE);
            this.set(level, box, x, groundY + 1, z, FENCE);
            this.set(level, box, x, groundY + 2, z, PUMPKIN);
            this.set(level, box, x, groundY - 1, z, COBBLE);
        }

        // Rubble and a couple of cobwebs in the corners.
        for (int i = 0; i < 12; i++) {
            int x = cx + random.nextInt(radius * 2 - 4) - radius + 2;
            int z = cz + random.nextInt(radius * 2 - 4) - radius + 2;
            BlockState rubble = random.nextBoolean() ? COBBLE : MOSSY_COBBLE;
            this.set(level, box, x, groundY, z, rubble);
            if (random.nextBoolean()) {
                this.set(level, box, x, groundY + 1, z, rubble);
            }
        }
        for (int i = 0; i < 5; i++) {
            int x = cx + (random.nextBoolean() ? radius - 1 : -radius + 1);
            int z = cz + random.nextInt(radius * 2) - radius;
            this.set(level, box, x, groundY + 1 + random.nextInt(2), z, WEB);
        }

        // Paper bomb traps on the path to the chest.
        for (int i = 0; i < 3; i++) {
            int x = cx + 3 + i * 2;
            int z = cz + 6 + i;
            this.set(level, box, x, groundY - 1, z, COBBLE);
            this.set(level, box, x, groundY, z, this.paperBomb());
        }

        int chestX = cx + 10;
        int chestZ = cz + 10;
        this.buildChestAlcove(level, box, random, chestX, groundY, chestZ);
    }

    // ------------------------------------------------------------------ abandoned outpost

    private void buildOutpost(WorldGenLevel level, BoundingBox box, RandomSource random,
                              int cx, int groundY, int cz) {
        int radius = 6;
        this.clearAndFloor(level, box, random, cx, groundY, cz, radius + 1);

        // Stone footing under the wooden walls.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius;
                if (edge) {
                    this.set(level, box, cx + dx, groundY - 1, cz + dz, COBBLE);
                }
            }
        }

        // Walls of oak planks with log posts at the corners, a door to the north and two windows.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean xEdge = Math.abs(dx) == radius;
                boolean zEdge = Math.abs(dz) == radius;
                if (!xEdge && !zEdge) {
                    continue;
                }
                boolean corner = xEdge && zEdge;
                for (int y = 0; y < 4; y++) {
                    BlockState state = corner ? LOG : PLANKS;
                    this.set(level, box, cx + dx, groundY + y, cz + dz, state);
                }
                if (corner) {
                    this.set(level, box, cx + dx, groundY + 4, cz + dz, LOG);
                }
            }
        }
        // Door in the north wall (negative z), two blocks tall.
        this.set(level, box, cx, groundY, cz - radius, AIR);
        this.set(level, box, cx, groundY + 1, cz - radius, AIR);
        this.set(level, box, cx, groundY + 2, cz - radius, PLANKS);
        // Windows on the east and west walls.
        this.set(level, box, cx + radius, groundY + 2, cz - 2, GLASS);
        this.set(level, box, cx + radius, groundY + 2, cz + 2, GLASS);
        this.set(level, box, cx - radius, groundY + 2, cz - 2, GLASS);
        this.set(level, box, cx - radius, groundY + 2, cz + 2, GLASS);

        // Roof: a stepped pyramid of dark oak so it reads as a built roof from a distance.
        for (int layer = 0; layer <= 2; layer++) {
            int r = radius - layer;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    this.set(level, box, cx + dx, groundY + 4 + layer, cz + dz,
                            layer == 2 ? SLAB : ROOF);
                }
            }
        }

        // Interior furnishing.
        this.set(level, box, cx - 3, groundY, cz + 3, CRAFTING);
        this.set(level, box, cx + 3, groundY, cz + 3, BARREL);
        this.set(level, box, cx + 2, groundY, cz + 4, BARREL);
        this.set(level, box, cx - 4, groundY, cz - 4, TORCH);
        this.set(level, box, cx + 4, groundY, cz - 4, TORCH);
        this.set(level, box, cx + 4, groundY, cz + 4, TORCH);

        // The chest stands against the back wall; a paper bomb guards the door mat.
        this.set(level, box, cx, groundY - 1, cz + 4, COBBLE);
        this.chest(level, box, random, cx, groundY, cz + 4);
        this.set(level, box, cx, groundY, cz - radius + 2, this.paperBomb());
    }

    // ------------------------------------------------------------------ shared helpers

    private void clearAndFloor(WorldGenLevel level, BoundingBox box, RandomSource random,
                               int cx, int groundY, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                this.set(level, box, x, groundY - 2, z, COBBLE);
                BlockState floor = this.weatheredBrick(random);
                this.set(level, box, x, groundY - 1, z, floor);
                for (int dy = 0; dy < 6; dy++) {
                    this.set(level, box, x, groundY + dy, z, AIR);
                }
            }
        }
    }

    private void buildChestAlcove(WorldGenLevel level, BoundingBox box, RandomSource random,
                                  int x, int groundY, int z) {
        // Three walls of bricks around the chest, open to the yard.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (random.nextBoolean()) {
                    continue;
                }
                this.set(level, box, x + dx, groundY, z + dz, this.weatheredBrick(random));
                this.set(level, box, x + dx, groundY + 1, z + dz, this.weatheredBrick(random));
            }
        }
        this.set(level, box, x, groundY - 1, z, BRICKS);
        this.chest(level, box, random, x, groundY, z);
    }

    private BlockState weatheredBrick(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.18f) {
            return CRACKED;
        }
        if (roll < 0.32f) {
            return MOSSY;
        }
        return BRICKS;
    }

    /** A paper bomb lying flat on the floor, hidden until someone steps on it. */
    private BlockState paperBomb() {
        return NarutoBlocks.PAPER_BOMB.get().defaultBlockState()
                .setValue(PaperBombBlock.FACE, AttachFace.FLOOR)
                .setValue(PaperBombBlock.FACING, Direction.NORTH)
                .setValue(PaperBombBlock.HIDDEN, Boolean.TRUE);
    }

    private void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        if (!box.isInside(x, y, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

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
