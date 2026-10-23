package com.sekwah.narutomod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * Two hand-made ruins that generate out of vanilla blocks: a collapsed training ground and an
 * abandoned outpost. Both are entered through their chests (one is guarded by paper bomb traps) and
 * both are meant to be found with a treasure map as much as by wandering.
 *
 * <p>They are {@link Structure}s rather than tree features because the layout is a building that has
 * to sit on flat ground and be recorded in the structure index - that index is what
 * {@code ServerLevel#findNearestMapStructure} reads when a treasure map is used.</p>
 */
public class NarutoDungeonStructure extends Structure {

    /** Which ruin this entry describes. The two share all the code, only the layout differs. */
    public enum Variant implements StringRepresentable {
        RUINED_TRAINING_GROUNDS("ruined_training_grounds"),
        ABANDONED_OUTPOST("abandoned_outpost");

        public static final Codec<Variant> CODEC = StringRepresentable.fromEnum(Variant::values);

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<NarutoDungeonStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            Variant.CODEC.fieldOf("variant").forGetter(structure -> structure.variant)
    ).apply(instance, NarutoDungeonStructure::new));

    private final Variant variant;

    public NarutoDungeonStructure(StructureSettings settings, Variant variant) {
        super(settings);
        this.variant = variant;
    }

    public Variant variant() {
        return this.variant;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int y = getLowestY(context, 16, 16);
        BlockPos center = context.chunkPos().getMiddleBlockPosition(y);
        return Optional.of(new GenerationStub(center,
                builder -> addPieces(builder, center, this.variant)));
    }

    private static void addPieces(StructurePiecesBuilder builder, BlockPos center, Variant variant) {
        builder.addPiece(new NarutoDungeonPiece(center, variant));
    }

    @Override
    public StructureType<?> type() {
        return NarutoWorldGen.DUNGEON_STRUCTURE.get();
    }
}
