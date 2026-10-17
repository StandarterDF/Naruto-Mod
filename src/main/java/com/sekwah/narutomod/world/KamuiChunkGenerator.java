package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The terrain of the Kamui pocket dimension: a field of square stone pillars standing in the void,
 * with nothing but a two block drop between them.
 *
 * <p>Obito's void is a field of grey blocks of about the same height, and this is that field told in
 * the smallest way possible: every {@link #PLATFORM_PITCH} blocks a {@link #PLATFORM_SIZE} by
 * {@link #PLATFORM_SIZE} square pillar is raised, all of them tall enough to stand on and read as
 * pillars rather than as plates, their tops one block apart so the whole field can be walked across.
 * The very bottom row is bedrock, so the field cannot be mined out from underneath the casters, and
 * everything above it is plain stone.</p>
 *
 * <p>Nothing else is generated - no ore, no caves, no creatures, and deliberately no biome
 * features either ({@link #applyBiomeDecoration} does nothing). Vanilla would otherwise decorate the
 * void biome, and the {@code minecraft:void_start_platform} feature it places is the stray slab of
 * cobblestone that used to hang over the arrival point like a ceiling.</p>
 *
 * <p>Everything is decided from the world coordinates alone, so the terrain is stable no matter in
 * what order chunks are visited.</p>
 */
public class KamuiChunkGenerator extends ChunkGenerator {

    public static final Codec<KamuiChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(KamuiChunkGenerator::getBiomeSource)
    ).apply(instance, KamuiChunkGenerator::new));

    /** Y of the bottom row of a pillar. */
    public static final int PLATFORM_Y = 0;

    /** Side length of one square pillar. */
    public static final int PLATFORM_SIZE = 5;

    /** Distance between the starts of two pillars; the gap is the difference. */
    public static final int PLATFORM_PITCH = 7;

    /**
     * Height of a pillar, counting its blocks. The two values are one apart on purpose: neighbouring
     * pillars differ by a single step, so the field looks like a field of pillars of roughly equal
     * height (as in the anime) and every gap can still be jumped.
     */
    public static final int PILLAR_HEIGHT_LOW = 7;
    public static final int PILLAR_HEIGHT_HIGH = 8;

    private static final int GEN_DEPTH = 256;
    private static final int MIN_Y = 0;

    public KamuiChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** @return whether the block column at (x, z) belongs to a pillar. */
    public static boolean isPlatform(int x, int z) {
        return Math.floorMod(x, PLATFORM_PITCH) < PLATFORM_SIZE
                && Math.floorMod(z, PLATFORM_PITCH) < PLATFORM_SIZE;
    }

    /** @return how many blocks tall the pillar at (x, z) is; only meaningful on a pillar. */
    public static int pillarHeight(int x, int z) {
        int pillarX = Math.floorDiv(x, PLATFORM_PITCH);
        int pillarZ = Math.floorDiv(z, PLATFORM_PITCH);
        return Math.floorMod(pillarX + pillarZ, 2) == 0 ? PILLAR_HEIGHT_LOW : PILLAR_HEIGHT_HIGH;
    }

    /**
     * @return the y of the first empty block above the terrain at (x, z), which is where an entity
     * standing on it has its feet; {@link #MIN_Y} where the column is empty void.
     */
    public static int surfaceY(int x, int z) {
        return isPlatform(x, z) ? pillarHeight(x, z) : MIN_Y;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
            BlockState stone = Blocks.STONE.defaultBlockState();
            Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = minX + x;
                    int worldZ = minZ + z;
                    if (!isPlatform(worldX, worldZ)) {
                        continue;
                    }
                    int height = pillarHeight(worldX, worldZ);
                    // The footing is bedrock so the field cannot be dug out from under the casters,
                    // the rest of the pillar is plain stone.
                    chunk.setBlockState(new BlockPos(worldX, PLATFORM_Y, worldZ), bedrock, false);
                    oceanFloor.update(x, PLATFORM_Y, z, bedrock);
                    worldSurface.update(x, PLATFORM_Y, z, bedrock);
                    for (int y = PLATFORM_Y + 1; y < height; y++) {
                        chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
                        oceanFloor.update(x, y, z, stone);
                        worldSurface.update(x, y, z, stone);
                    }
                }
            }
            return chunk;
        }, executor);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // Nothing to carve: the terrain is placed, not shaped.
    }

    /**
     * Deliberately does nothing.
     *
     * <p>This is where vanilla decorates a chunk with the placed features of its biomes. The pocket
     * uses the {@code the_void} biome, whose features include the starter platform meant for
     * superflat void worlds; that platform is what used to appear as a slab of cobblestone hanging
     * over the arrival point. The pillar field is the whole of the terrain here, so no decoration is
     * wanted at all.</p>
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // Nothing is ever decorated onto the pillars.
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random,
                             ChunkAccess chunk) {
        // Every block is already the block it should be.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // No wildlife in the void.
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return surfaceY(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[GEN_DEPTH];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        if (isPlatform(x, z)) {
            int height = pillarHeight(x, z);
            states[PLATFORM_Y - MIN_Y] = Blocks.BEDROCK.defaultBlockState();
            for (int y = PLATFORM_Y + 1; y < height; y++) {
                states[y - MIN_Y] = Blocks.STONE.defaultBlockState();
            }
        }
        return new NoiseColumn(MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Kamui pillars: " + PLATFORM_SIZE + "x" + PLATFORM_SIZE + " every " + PLATFORM_PITCH
                + ", " + PILLAR_HEIGHT_LOW + "-" + PILLAR_HEIGHT_HIGH + " tall"
                + (isPlatform(pos.getX(), pos.getZ()) ? " (pillar)" : " (void)"));
    }
}
