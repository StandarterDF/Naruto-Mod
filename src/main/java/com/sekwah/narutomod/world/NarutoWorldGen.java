package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * World generation registries. Chunk generator codecs are registered by hand because the dimension
 * that uses them is hand written too (datagen never runs for this project).
 */
public final class NarutoWorldGen {

    private NarutoWorldGen() {
    }

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, NarutoMod.MOD_ID);

    /** The pillar grid of the Kamui pocket dimension. */
    public static final RegistryObject<Codec<? extends ChunkGenerator>> KAMUI_PILLARS =
            CHUNK_GENERATORS.register("kamui_pillars", () -> KamuiChunkGenerator.CODEC);

    public static void register(IEventBus eventBus) {
        CHUNK_GENERATORS.register(eventBus);
    }
}
