package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * A natural place of power: one enormous tree standing in a clearing, with old stones and a chest at
 * its roots.
 *
 * <p>It is a worldgen structure rather than a feature because the tree is larger than a chunk, and
 * because a structure can be looked up from a position later: {@code SenjutsuChargingAbility} asks
 * the structure index whether the caster is standing inside this one and gathers nature energy twice
 * as fast there. That lookup is the whole mechanical meaning of the place - everything else is
 * decoration out of vanilla blocks, so no custom block or renderer is involved.</p>
 */
public class SacredTreeStructure extends Structure {

    /** Horizontal reach of the tree, in blocks from the trunk. */
    public static final int RADIUS = 9;

    /** How far the tree reaches above the ground. */
    public static final int HEIGHT = 24;

    public static final Codec<SacredTreeStructure> CODEC = simpleCodec(SacredTreeStructure::new);

    public SacredTreeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // The middle of the chunk, on the surface of the highest corner of the 16x16 area, so the
        // tree never starts inside a hill.
        int y = getLowestY(context, 16, 16);
        BlockPos center = context.chunkPos().getMiddleBlockPosition(y);
        return Optional.of(new GenerationStub(center, builder -> addPieces(builder, center)));
    }

    private static void addPieces(StructurePiecesBuilder builder, BlockPos center) {
        builder.addPiece(new SacredTreePiece(center));
    }

    @Override
    public StructureType<?> type() {
        return NarutoWorldGen.SACRED_TREE_STRUCTURE.get();
    }
}
