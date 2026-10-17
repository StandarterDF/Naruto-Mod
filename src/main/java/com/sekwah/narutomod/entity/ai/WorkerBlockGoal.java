package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * The shift a worker clone works: it walks to the nearest block of its kind around itself and takes
 * it.
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

    /** How much higher and lower than itself a worker will look for a target. */
    private static final int VERTICAL_REACH = 3;

    /** How close the worker has to get before it can reach the block. */
    private static final double REACH_SQR = 4.0d;

    /** Ticks between one swing and the next, so a worker is not a machine. */
    private static final int SWING_COOLDOWN = 15;

    /** Ticks between scans when there is nothing to go for. */
    private static final int EMPTY_COOLDOWN = 20;

    /** How long a worker tries to reach one target before it gives up and looks elsewhere. */
    private static final int UNREACHABLE_TICKS = 300;

    /** Heights at which a block next to the target is tried, relative to the target. */
    private static final int[] STAND_HEIGHTS = {0, 1, -1};

    protected final ShadowCloneEntity worker;

    private BlockPos target;
    private int cooldown;
    private int reachTicks;

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

    /**
     * Blocks a worker may clear when nothing of its own kind is within reach.
     *
     * <p>A lumberjack uses this for leaves: a trunk walled in by foliage has no reachable side at
     * all, so the worker first clears the leaves in its way and only then gets at the wood.</p>
     */
    protected boolean secondary(BlockState state) {
        return false;
    }

    /** How far around itself a worker looks for its kind of block, in blocks. */
    protected int searchRadius() {
        return 8;
    }

    @Override
    public boolean canUse() {
        if (!this.isMyJob()) {
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
        if (this.target == null || !this.isMyJob()) {
            return false;
        }
        BlockState state = this.worker.level().getBlockState(this.target);
        return this.matches(state) || this.secondary(state);
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.reachTicks = 0;
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
            // No progress for too long usually means the way is walled off (foliage, terrain). Give
            // the target up instead of pressing against the wall forever - another scan may find a
            // leaf or a different block the worker can actually get to.
            if (++this.reachTicks > UNREACHABLE_TICKS) {
                this.target = null;
                this.cooldown = EMPTY_COOLDOWN;
                this.worker.getNavigation().stop();
                return;
            }
            if (this.worker.getNavigation().isDone()) {
                this.walkTo(this.target);
            }
            return;
        }
        this.reachTicks = 0;
        if (this.worker.level() instanceof ServerLevel level) {
            this.work(level, this.target);
        }
        this.target = null;
        this.cooldown = SWING_COOLDOWN;
    }

    private boolean isMyJob() {
        return this.worker.isWorker() && this.worker.getWorkerJob() == this.job()
                && !this.worker.level().isClientSide;
    }

    private void walkTo(BlockPos pos) {
        BlockPos stand = this.standPosition(pos);
        this.worker.getNavigation().moveTo(stand.getX() + 0.5d, stand.getY(), stand.getZ() + 0.5d, 1.0d);
    }

    /** @return the closest block of this job's kind around the worker that it can walk to. */
    private BlockPos findTarget() {
        BlockPos primary = this.findNearest(this::matches);
        if (primary != null) {
            return primary;
        }
        // Nothing of its own kind can be reached; a secondary block (foliage in a chopper's way) may
        // still be worth taking, because clearing it can open a path to the real thing.
        return this.findNearest(this::secondary);
    }

    /** @return the closest block matching {@code wanted} around the worker that it can walk to. */
    private BlockPos findNearest(Predicate<BlockState> wanted) {
        // A worker looks around wherever it is rather than around a fixed post: a forest it walks
        // through keeps offering work, and when the ground around it is picked clean it drifts on to
        // the next stand of trees by itself (see the strolling goal it falls back to).
        BlockPos here = this.worker.blockPosition();
        int radius = this.searchRadius();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-radius, -VERTICAL_REACH, -radius),
                here.offset(radius, VERTICAL_REACH, radius))) {
            if (!wanted.test(this.worker.level().getBlockState(pos)) || !this.hasStand(pos)) {
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

    /**
     * Whether there is anywhere to stand by the block. A solid block (a log, a lump of stone) has no
     * path node of its own, so the worker has to stand next to it; if no side works there is no point
     * picking that block at all.
     */
    private boolean hasStand(BlockPos pos) {
        Level level = this.worker.level();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || this.standPosition(pos) != pos;
    }

    /**
     * The spot to walk to for a block. A crop can be walked right up to, but a solid block cannot -
     * the pathfinder has no node inside it, and navigating at it simply leaves the worker standing
     * where it is. For those the nearest walkable neighbour is used instead.
     */
    private BlockPos standPosition(BlockPos pos) {
        Level level = this.worker.level();
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return pos;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy : STAND_HEIGHTS) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = pos.relative(dir).above(dy);
                if (!this.isStandable(candidate)) {
                    continue;
                }
                double distance = this.worker.distanceToSqr(Vec3.atCenterOf(candidate));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best != null ? best : pos;
    }

    /** A block with room for the clone and solid ground underneath it. */
    private boolean isStandable(BlockPos pos) {
        Level level = this.worker.level();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
