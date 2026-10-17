package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The hoe shift of a worker clone: it walks to the nearest ripe crop around the spot it was put
 * down on, takes it and puts a fresh seed back in the ground.
 *
 * <p>Only crops in the vanilla {@code #minecraft:crops} tag are touched, so a farm hand is not a
 * second ore excavation. The drops go into the clone's bag, which it hands to its caster when it
 * pops (see {@code ShadowCloneEntity#releaseLoot()}).</p>
 */
public class WorkerHarvestGoal extends WorkerBlockGoal {

    public WorkerHarvestGoal(ShadowCloneEntity worker) {
        super(worker);
    }

    @Override
    protected WorkerJob job() {
        return WorkerJob.FARM;
    }

    @Override
    protected boolean matches(BlockState state) {
        return state.is(BlockTags.CROPS)
                && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state);
    }

    @Override
    protected List<BlockPos> plan(ServerLevel level, BlockPos pos) {
        return this.matches(level.getBlockState(pos)) ? List.of(pos.immutable()) : List.of();
    }

    /**
     * Takes the crop and replants it.
     *
     * <p>The drops are collected by hand rather than left in the world, and the block is put back at
     * age zero so the field keeps producing instead of being stripped bare.</p>
     */
    @Override
    protected void take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        for (ItemStack drop : Block.getDrops(state, level, pos, null, this.worker, ItemStack.EMPTY)) {
            this.worker.collect(drop);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.destroyBlock(pos, false);
        BlockState replanted = crop.getStateForAge(0);
        if (replanted.canSurvive(level, pos)) {
            level.setBlock(pos, replanted, 3);
        }
    }
}
