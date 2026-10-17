package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The shift a worker clone works: it walks to the nearest ripe crop around the spot it was put down
 * on, takes it and puts a fresh seed back in the ground.
 *
 * <p>Only crops in the vanilla {@code #minecraft:crops} tag are touched, so a worker is a farm hand
 * rather than a second ore excavation. The drops do not fall to the ground either: they go into the
 * clone's bag, which it hands to its caster when it pops (see
 * {@code ShadowCloneEntity#releaseLoot()}).</p>
 */
public class WorkerHarvestGoal extends Goal {

    /** How far from its post a worker will wander, in blocks. */
    private static final int RADIUS = 8;

    /** How much higher and lower than its post a worker will look for crops. */
    private static final int VERTICAL_REACH = 2;

    /** How close the worker has to get before it can reach the crop. */
    private static final double REACH_SQR = 4.0d;

    /** Ticks between one swing and the next, so a worker is not a threshing machine. */
    private static final int SWING_COOLDOWN = 15;

    /** Ticks between scans of the field when there is nothing ripe to go for. */
    private static final int EMPTY_COOLDOWN = 20;

    private final ShadowCloneEntity worker;

    private BlockPos crop;
    private int cooldown;

    public WorkerHarvestGoal(ShadowCloneEntity worker) {
        this.worker = worker;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.worker.isWorker() || this.worker.level().isClientSide) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.crop = this.findRipeCrop();
        this.cooldown = EMPTY_COOLDOWN;
        return this.crop != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.crop != null && this.worker.isWorker() && this.isRipe(this.crop);
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.walkTo(this.crop);
    }

    @Override
    public void stop() {
        this.crop = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.crop == null) {
            return;
        }
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(this.crop));
        if (this.worker.distanceToSqr(Vec3.atCenterOf(this.crop)) > REACH_SQR) {
            if (this.worker.getNavigation().isDone()) {
                this.walkTo(this.crop);
            }
            return;
        }
        this.reap(this.crop);
        this.crop = null;
        this.cooldown = SWING_COOLDOWN;
    }

    private void walkTo(BlockPos pos) {
        this.worker.getNavigation().moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 1.0d);
    }

    /** @return the closest ripe crop within reach of the worker's post, or null if there is none. */
    private BlockPos findRipeCrop() {
        BlockPos home = this.worker.getWorkerHome() != null
                ? this.worker.getWorkerHome() : this.worker.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-RADIUS, -VERTICAL_REACH, -RADIUS),
                home.offset(RADIUS, VERTICAL_REACH, RADIUS))) {
            if (!this.isRipe(pos)) {
                continue;
            }
            double distance = this.worker.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos.immutable();
            }
        }
        return nearest;
    }

    private boolean isRipe(BlockPos pos) {
        BlockState state = this.worker.level().getBlockState(pos);
        return state.is(BlockTags.CROPS)
                && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state);
    }

    /**
     * Takes the crop and replants it.
     *
     * <p>The drops are collected by hand rather than left in the world, and the block is put back at
     * age zero so the field keeps producing instead of being stripped bare.</p>
     */
    private void reap(BlockPos pos) {
        if (!(this.worker.level() instanceof ServerLevel level)) {
            return;
        }
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
