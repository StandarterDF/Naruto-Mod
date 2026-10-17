package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
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
 * The terrain of the Kamui pocket dimension: a grid of low, flat bedrock platforms of equal height
 * standing in the void, with nothing but a two block drop between them.
 *
 * <p>Obito's void is a field of grey blocks of about the same height, and this is that field told in
 * the smallest way possible: every {@link #PLATFORM_PITCH} blocks a {@link #PLATFORM_SIZE} by
 * {@link #PLATFORM_SIZE} square platform is placed, all of them one block thick with their top face
 * at y = 1. Nothing else is generated - no ore, no caves, no creatures - so the only things in there
 * are the ones a caster brought with them.</p>
 *
 * <p>Everything is decided from the world coordinates alone, so the terrain is stable no matter in
 * what order chunks are visited.</p>
 */
public class KamuiChunkGenerator extends ChunkGenerator {

    public static final Codec<KamuiChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(KamuiChunkGenerator::getBiomeSource)
    ).apply(instance, KamuiChunkGenerator::new));

    /** Y of the platform blocks; their top face is therefore at y = 1. */
    public static final int PLATFORM_Y = 0;

    /** Side length of one square platform. */
    public static final int PLATFORM_SIZE = 5;

    /** Distance between the starts of two platforms; the gap is the difference. */
    public static final int PLATFORM_PITCH = 7;

    private static final int GEN_DEPTH = 256;
    private static final int MIN_Y = 0;

    public KamuiChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** @return whether the block column at (x, z) belongs to a platform. */
    public static boolean isPlatform(int x, int z) {
        return Math.floorMod(x, PLATFORM_PITCH) < PLATFORM_SIZE
                && Math.floorMod(z, PLATFORM_PITCH) < PLATFORM_SIZE;
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
                    chunk.setBlockState(new BlockPos(worldX, PLATFORM_Y, worldZ), bedrock, false);
                    oceanFloor.update(x, PLATFORM_Y, z, bedrock);
                    worldSurface.update(x, PLATFORM_Y, z, bedrock);
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
        return isPlatform(x, z) ? PLATFORM_Y + 1 : MIN_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[GEN_DEPTH];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        if (isPlatform(x, z)) {
            states[PLATFORM_Y - MIN_Y] = Blocks.BEDROCK.defaultBlockState();
        }
        return new NoiseColumn(MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Kamui pillars: " + PLATFORM_SIZE + "x" + PLATFORM_SIZE + " every " + PLATFORM_PITCH
                + " at y=" + PLATFORM_Y + (isPlatform(pos.getX(), pos.getZ()) ? " (platform)" : " (void)"));
    }
}
