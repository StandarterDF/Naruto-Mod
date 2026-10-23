package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * World generation registries. Chunk generator codecs are registered by hand because the dimension
 * that uses them is hand written too (datagen never runs for this project). The same is true of the
 * mod's structures: their block layouts are code, and only the placement lives in JSON.
 */
public final class NarutoWorldGen {

    private NarutoWorldGen() {
    }

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, NarutoMod.MOD_ID);

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, NarutoMod.MOD_ID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, NarutoMod.MOD_ID);

    /** The pillar grid of the Kamui pocket dimension. */
    public static final RegistryObject<Codec<? extends ChunkGenerator>> KAMUI_PILLARS =
            CHUNK_GENERATORS.register("kamui_pillars", () -> KamuiChunkGenerator.CODEC);

    /** A giant tree in a clearing where nature energy gathers faster. */
    public static final RegistryObject<StructureType<SacredTreeStructure>> SACRED_TREE_STRUCTURE =
            STRUCTURE_TYPES.register("sacred_tree", () -> () -> SacredTreeStructure.CODEC);

    /** The two ruined sites, chosen by the {@code variant} field of the structure JSON. */
    public static final RegistryObject<StructureType<NarutoDungeonStructure>> DUNGEON_STRUCTURE =
            STRUCTURE_TYPES.register("dungeon", () -> () -> NarutoDungeonStructure.CODEC);

    public static final RegistryObject<StructurePieceType> SACRED_TREE_PIECE =
            STRUCTURE_PIECES.register("sacred_tree",
                    () -> (context, tag) -> new SacredTreePiece(tag));

    public static final RegistryObject<StructurePieceType> DUNGEON_PIECE =
            STRUCTURE_PIECES.register("dungeon",
                    () -> (context, tag) -> new NarutoDungeonPiece(tag));

    /** Structure keys used by gameplay code, e.g. the faster nature energy of the sacred tree. */
    public static final ResourceKey<Structure> SACRED_TREE_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(NarutoMod.MOD_ID, "sacred_tree"));

    /** Tag holding every site a treasure map may point to, including the mod's own ruins. */
    public static final TagKey<Structure> TREASURE_MAP_TARGETS =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation(NarutoMod.MOD_ID, "treasure_maps"));

    public static void register(IEventBus eventBus) {
        CHUNK_GENERATORS.register(eventBus);
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECES.register(eventBus);
    }
}
