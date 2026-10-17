package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The shift a worker clone works: it walks to the nearest block of its kind around the spot it was
 * put down on and takes it.
 *
 * <p>All three jobs (farming, mining, chopping) share the same loop of finding a target, walking to
 * it and breaking it; only what counts as a target and what happens when the worker reaches it
 * differ. The subclasses supply those two with {@link #matches(BlockState)} and
 * {@link #work(ServerLevel, BlockPos)}, and declare which {@link WorkerJob} they belong to.</p>
 *
 * <p>What is taken does not fall to the ground: it goes into the clone's bag, which it hands to its
 * caster when it pops (see {@code ShadowCloneEntity#releaseLoot()}).</p>
 */
public abstract class WorkerBlockGoal extends Goal {

    /** How far from its post a worker will wander, in blocks. */
    private static final int RADIUS = 8;

    /** How much higher and lower than its post a worker will look. */
    private static final int VERTICAL_REACH = 3;

    /** How close the worker has to get before it can reach the block. */
    private static final double REACH_SQR = 4.0d;

    /** Ticks between one swing and the next, so a worker is not a machine. */
    private static final int SWING_COOLDOWN = 15;

    /** Ticks between scans when there is nothing to go for. */
    private static final int EMPTY_COOLDOWN = 20;

    protected final ShadowCloneEntity worker;

    private BlockPos target;
    private int cooldown;

    protected WorkerBlockGoal(ShadowCloneEntity worker) {
        this.worker = worker;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The job this goal performs; a worker only ever does the one it was summoned for. */
    protected abstract WorkerJob job();

    /** @return whether a block is something this job takes. */
    protected abstract boolean matches(BlockState state);

    /** Performs the work on the block the worker has walked up to; the block is still there. */
    protected abstract void work(ServerLevel level, BlockPos pos);

    @Override
    public boolean canUse() {
        if (!this.worker.isWorker() || this.worker.getWorkerJob() != this.job()
                || this.worker.level().isClientSide) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.target = this.findTarget();
        this.cooldown = EMPTY_COOLDOWN;
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.worker.isWorker()
                && this.worker.getWorkerJob() == this.job()
                && this.matches(this.worker.level().getBlockState(this.target));
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.walkTo(this.target);
    }

    @Override
    public void stop() {
        this.target = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(this.target));
        if (this.worker.distanceToSqr(Vec3.atCenterOf(this.target)) > REACH_SQR) {
            if (this.worker.getNavigation().isDone()) {
                this.walkTo(this.target);
            }
            return;
        }
        if (this.worker.level() instanceof ServerLevel level) {
            this.work(level, this.target);
        }
        this.target = null;
        this.cooldown = SWING_COOLDOWN;
    }

    private void walkTo(BlockPos pos) {
        this.worker.getNavigation().moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 1.0d);
    }

    /** @return the closest block of this job's kind around the worker's post, or null. */
    private BlockPos findTarget() {
        BlockPos home = this.worker.getWorkerHome() != null
                ? this.worker.getWorkerHome() : this.worker.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-RADIUS, -VERTICAL_REACH, -RADIUS),
                home.offset(RADIUS, VERTICAL_REACH, RADIUS))) {
            if (!this.matches(this.worker.level().getBlockState(pos))) {
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
}
