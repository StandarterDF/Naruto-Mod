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

    /** Ticks between attempts to find a path, so a blocked worker does not path every tick. */
    private static final int REPATH_TICKS = 20;

    /** How long a worker tries to reach one target before it gives up and looks elsewhere. */
    private static final int UNREACHABLE_TICKS = 300;

    /** Heights at which a block next to the target is tried, relative to the target. */
    private static final int[] STAND_HEIGHTS = {0, 1, -1};

    protected final ShadowCloneEntity worker;

    private BlockPos target;
    private int cooldown;
    private int reachTicks;
    private int repathTicks;

    /** Offsets to probe, nearest ring first (x, y, z triples), and where each ring starts in it. */
    private int[] scanOffsets;
    private int[] scanShellStart;

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
     * all, so the worker first clears the leaves in its way and only then gets at the wood. The
     * position is given so a subclass can require the block to be worth clearing (a leaf touching a
     * trunk, say) instead of every block of that kind in sight.</p>
     */
    protected boolean secondary(BlockState state, BlockPos pos) {
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
        return this.matches(state) || this.secondary(state, this.target);
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.reachTicks = 0;
        this.walkTo(this.target);
        this.repathTicks = REPATH_TICKS;
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
            // A failed path search leaves the navigation "done", so trying again every tick would
            // run A* from scratch sixty times a second; only retry every so often instead.
            if (this.worker.getNavigation().isDone() && --this.repathTicks <= 0) {
                this.repathTicks = REPATH_TICKS;
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
        Level level = this.worker.level();
        BlockPos primary = this.findNearest(pos -> this.matches(level.getBlockState(pos)));
        if (primary != null) {
            return primary;
        }
        // Nothing of its own kind can be reached; a secondary block (foliage in a chopper's way) may
        // still be worth taking, because clearing it can open a path to the real thing.
        return this.findNearest(pos -> this.secondary(level.getBlockState(pos), pos));
    }

    /** @return the closest block matching {@code wanted} that the worker can walk to, or null. */
    private BlockPos findNearest(Predicate<BlockPos> wanted) {
        // A worker looks around wherever it is rather than around a fixed post: a forest it walks
        // through keeps offering work, and when the ground around it is picked clean it drifts on to
        // the next stand of trees by itself (see the strolling goal it falls back to).
        BlockPos here = this.worker.blockPosition();
        Level level = this.worker.level();
        int radius = this.searchRadius();
        this.buildScanOrder(radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Probe ring by ring so the first hit is the closest one and a forest yields a target within
        // a couple of blocks: the far corners of a 24-block box are never touched while work is near.
        for (int ring = 0; ring <= radius; ring++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int i = this.scanShellStart[ring]; i < this.scanShellStart[ring + 1]; i++) {
                int base = i * 3;
                cursor.set(here.getX() + this.scanOffsets[base],
                        here.getY() + this.scanOffsets[base + 1],
                        here.getZ() + this.scanOffsets[base + 2]);
                if (!wanted.test(cursor) || !this.hasStand(cursor)) {
                    continue;
                }
                double distance = this.worker.distanceToSqr(Vec3.atCenterOf(cursor));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cursor.immutable();
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * Builds the list of offsets a scan walks through, nearest horizontal ring first. Done once,
     * since the radius never changes; a plain box scan would re-touch the whole volume every time.
     */
    private void buildScanOrder(int radius) {
        if (this.scanOffsets != null && this.scanShellStart.length > radius) {
            return;
        }
        int entries = 0;
        for (int ring = 0; ring <= radius; ring++) {
            entries += (ring == 0 ? 1 : 8 * ring) * (2 * VERTICAL_REACH + 1);
        }
        this.scanOffsets = new int[entries * 3];
        this.scanShellStart = new int[radius + 2];
        int entry = 0;
        for (int ring = 0; ring <= radius; ring++) {
            this.scanShellStart[ring] = entry;
            for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
                int ringSize = ring == 0 ? 1 : 8 * ring;
                for (int i = 0; i < ringSize; i++) {
                    int dx;
                    int dz;
                    if (ring == 0) {
                        dx = 0;
                        dz = 0;
                    } else {
                        int side = i / (2 * ring);
                        int step = i % (2 * ring);
                        switch (side) {
                            case 0 -> { dx = -ring + step; dz = -ring; }
                            case 1 -> { dx = ring; dz = -ring + step; }
                            case 2 -> { dx = ring - step; dz = ring; }
                            default -> { dx = -ring; dz = ring - step; }
                        }
                    }
                    this.scanOffsets[entry * 3] = dx;
                    this.scanOffsets[entry * 3 + 1] = dy;
                    this.scanOffsets[entry * 3 + 2] = dz;
                    entry++;
                }
            }
        }
        this.scanShellStart[radius + 1] = entry;
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
