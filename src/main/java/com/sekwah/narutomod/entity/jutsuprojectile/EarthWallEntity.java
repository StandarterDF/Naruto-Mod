package com.sekwah.narutomod.entity.jutsuprojectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block of an earth wall climbing out of the ground.
 *
 * <p>The wall itself is made of ordinary blocks (see {@code world/EarthWallSavedData}); this entity
 * is only the part that cannot be done with blocks: an animation. A vanilla block cannot move, and a
 * {@code FallingBlockEntity} would fall, land and shatter, so the block is drawn a little below its
 * final spot and eased up into place, and the saved data stamps the real block down on the very tick
 * this entity disappears. The player sees the wall rise block by block out of the ground instead of a
 * slab appearing out of nowhere.</p>
 *
 * <p>The block state and the column stagger ride on synched data, so the client needs no custom
 * packet: the spawn packet already carries both, and the client animates from its own tick clock.</p>
 */
public class EarthWallEntity extends Entity {

    /** Ticks a block takes to climb into place. The saved data places the real block at the same time. */
    public static final int RISE_TICKS = 18;

    private static final EntityDataAccessor<BlockState> DATA_STATE =
            SynchedEntityData.defineId(EarthWallEntity.class, EntityDataSerializers.BLOCK_STATE);

    /** Ticks to sit underground before climbing, which staggers the columns of the wall. */
    private static final EntityDataAccessor<Integer> DATA_DELAY =
            SynchedEntityData.defineId(EarthWallEntity.class, EntityDataSerializers.INT);

    public EarthWallEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        this.entityData.define(DATA_DELAY, 0);
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(DATA_STATE, state);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setDelay(int delay) {
        this.entityData.set(DATA_DELAY, delay);
    }

    public int getDelay() {
        return this.entityData.get(DATA_DELAY);
    }

    @Override
    public void tick() {
        super.tick();
        // The block is placed by EarthWallSavedData at the same moment, so the animation simply ends
        // here; nothing is left behind that could be seen standing next to the real block.
        if (!this.level().isClientSide && this.tickCount >= this.getDelay() + RISE_TICKS) {
            this.discard();
        }
    }

    /**
     * Never saved. A wall animation lasts less than a second, and an entity reloaded from disk would
     * start its climb over the block the saved data has already placed.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Purely a picture: it must not be pickable or block arrows while the real block is missing. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Never saved, see shouldBeSaved.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Never saved, see shouldBeSaved.
    }
}
