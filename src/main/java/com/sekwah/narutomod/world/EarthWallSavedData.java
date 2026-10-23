package com.sekwah.narutomod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The blocks a raised earth wall borrowed, and when the ground wants them back.
 *
 * <p>An earth wall is not created stone but lifted ground, so every block it places has to remember
 * what was there before. That memory has to outlive a chunk unload or a restart - otherwise a wall
 * left standing at logout would become permanent terrain - so it lives in a {@link SavedData} rather
 * than in the ability or the block entity.</p>
 *
 * <p>The wall is animated rather than stamped down in one tick: a scheduled block waits for its
 * {@code riseAt} moment, then the ground gives way for its {@code expiresAt} moment. Both times are
 * staggered per block by the ability (bottom-up on the way in, top-down on the way out), so the wall
 * grows out of the floor and crumbles back into it instead of appearing and vanishing as one slab.</p>
 *
 * <p>Restoring is deliberately conservative: a block is only put back when the spot still holds the
 * very state the wall put there. If a player mined the wall, built over it or a creeper blew it up,
 * the world has moved on and the saved state is simply dropped. Entries for unloaded chunks wait
 * for the chunk instead of forcing a load.</p>
 */
public class EarthWallSavedData extends SavedData {

    private static final String DATA_NAME = "narutomod_earth_walls";
    private static final String ENTRIES_TAG = "entries";
    private static final String POS_TAG = "pos";
    private static final String PLACED_TAG = "placed";
    private static final String PREVIOUS_TAG = "previous";
    private static final String RISE_TAG = "rise";
    private static final String EXPIRES_TAG = "expires";
    private static final String RISEN_TAG = "risen";

    private final List<Entry> entries = new ArrayList<>();

    /**
     * One scheduled wall block: where it is, what will be put there, what it replaced, and when it
     * comes up and goes down. {@code previous} is only known once the block has actually risen.
     */
    private static final class Entry {
        private final BlockPos pos;
        private final BlockState placed;
        private final long riseAt;
        private final long expiresAt;
        @Nullable
        private BlockState previous;
        private boolean risen;

        private Entry(BlockPos pos, BlockState placed, @Nullable BlockState previous,
                      long riseAt, long expiresAt, boolean risen) {
            this.pos = pos;
            this.placed = placed;
            this.previous = previous;
            this.riseAt = riseAt;
            this.expiresAt = expiresAt;
            this.risen = risen;
        }
    }

    public static EarthWallSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(EarthWallSavedData::load,
                EarthWallSavedData::new, DATA_NAME);
    }

    /**
     * Schedules one wall block to rise and later sink back.
     *
     * @param riseAt    game time at which the block should be placed
     * @param expiresAt game time at which the block should be removed again
     */
    public void schedule(BlockPos pos, BlockState placed, long riseAt, long expiresAt) {
        this.entries.add(new Entry(pos.immutable(), placed, null, riseAt, expiresAt, false));
        this.setDirty();
    }

    /**
     * Advances the wall animation: places every block whose rise time has come and removes every
     * block whose time is up. Called every tick from {@code events/EarthWallEvents}.
     */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Entry> iterator = this.entries.iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!level.isLoaded(entry.pos)) {
                // Let the wall stand until its chunk is back; forcing a load for a bit of dirt would
                // be worse than a wall that lingers a little longer.
                continue;
            }
            if (!entry.risen) {
                if (now < entry.riseAt) {
                    continue;
                }
                if (!this.rise(level, entry)) {
                    iterator.remove();
                    changed = true;
                }
                continue;
            }
            if (now < entry.expiresAt) {
                continue;
            }
            if (entry.previous != null && level.getBlockState(entry.pos).equals(entry.placed)) {
                level.setBlockAndUpdate(entry.pos, entry.previous);
                level.playSound(null, entry.pos.getX() + 0.5d, entry.pos.getY() + 0.5d,
                        entry.pos.getZ() + 0.5d, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS,
                        0.35f, 0.5f + level.random.nextFloat() * 0.2f);
            }
            iterator.remove();
            changed = true;
        }
        if (changed) {
            this.setDirty();
        }
    }

    /**
     * Places one scheduled block if the spot is still free, remembering what was there.
     *
     * @return false when the block can never be placed and the entry can be forgotten
     */
    private boolean rise(ServerLevel level, Entry entry) {
        BlockState current = level.getBlockState(entry.pos);
        if (!current.canBeReplaced() || !level.getEntitiesOfClass(LivingEntity.class, new AABB(entry.pos)).isEmpty()) {
            // Somebody built here or stood in the wall while it was still coming up; dropping the
            // block is safer than stamping it over a player or over their construction.
            return false;
        }
        entry.previous = current;
        entry.risen = true;
        level.setBlockAndUpdate(entry.pos, entry.placed);
        level.playSound(null, entry.pos.getX() + 0.5d, entry.pos.getY() + 0.5d,
                entry.pos.getZ() + 0.5d, SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                0.4f, 0.5f + level.random.nextFloat() * 0.2f);
        if (level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    entry.pos.getX() + 0.5d, entry.pos.getY() + 0.5d, entry.pos.getZ() + 0.5d,
                    2, 0.35d, 0.2d, 0.35d, 0.01d);
        }
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag saved = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(POS_TAG, entry.pos.asLong());
            entryTag.put(PLACED_TAG, NbtUtils.writeBlockState(entry.placed));
            if (entry.previous != null) {
                entryTag.put(PREVIOUS_TAG, NbtUtils.writeBlockState(entry.previous));
            }
            entryTag.putLong(RISE_TAG, entry.riseAt);
            entryTag.putLong(EXPIRES_TAG, entry.expiresAt);
            entryTag.putBoolean(RISEN_TAG, entry.risen);
            saved.add(entryTag);
        }
        tag.put(ENTRIES_TAG, saved);
        return tag;
    }

    private static EarthWallSavedData load(CompoundTag tag) {
        EarthWallSavedData data = new EarthWallSavedData();
        ListTag saved = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entryTag = saved.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong(POS_TAG));
            BlockState placed = NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(), entryTag.getCompound(PLACED_TAG));
            BlockState previous = entryTag.contains(PREVIOUS_TAG, Tag.TAG_COMPOUND)
                    ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    entryTag.getCompound(PREVIOUS_TAG))
                    : null;
            // Walls saved before the rise animation existed are already standing in the world.
            boolean risen = !entryTag.contains(RISEN_TAG, Tag.TAG_BYTE) || entryTag.getBoolean(RISEN_TAG);
            long riseAt = entryTag.contains(RISE_TAG, Tag.TAG_LONG)
                    ? entryTag.getLong(RISE_TAG)
                    : Long.MIN_VALUE;
            data.entries.add(new Entry(pos, placed, previous, riseAt,
                    entryTag.getLong(EXPIRES_TAG), risen));
        }
        return data;
    }
}
