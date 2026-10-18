package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The shift a worker clone works: it walks to the nearest block of its kind around itself and takes
 * it.
 *
 * <p>All three jobs (farming, mining, chopping) share the same loop of finding a target, walking to
 * it and taking it; only what counts as a target and what happens when the worker gets there differ.
 * The subclasses supply those two with {@link #matches(BlockState)} and
 * {@link #plan(ServerLevel, BlockPos)}, and declare which {@link WorkerJob} they belong to.</p>
 *
 * <p>A take is spread over several ticks: a lumberjack hands over the whole trunk at once and the
 * goal then brings it down a log at a time, so a tree falls piece by piece instead of blinking out.
 * While a worker is busy with a block the spot is claimed, so two workers in the same wood do not
 * pile onto one tree - each heads for its own.</p>
 *
 * <p>What is taken does not fall to the ground: it goes into the clone's bag, which it hands to its
 * caster when it pops (see {@code ShadowCloneEntity#releaseLoot()}).</p>
 */
public abstract class WorkerBlockGoal extends Goal {

    /** How much higher and lower than itself a worker will look for a target. */
    private static final int VERTICAL_REACH = 3;

    /** How close the worker has to get before it can reach the block. */
    private static final double REACH_SQR = 4.0d;

    /** Ticks between one scan and the next after a take, so a worker is not a machine. */
    private static final int SWING_COOLDOWN = 15;

    /** Ticks between two blocks of the same take, so a trunk comes down block by block. */
    private static final int TAKE_TICKS = 6;

    /** Ticks between scans when there is nothing to go for. */
    private static final int EMPTY_COOLDOWN = 20;

    /** Ticks between attempts to find a path, so a blocked worker does not path every tick. */
    private static final int REPATH_TICKS = 20;

    /** Hard cap on how long a worker tries to reach one target before giving up and looking elsewhere. */
    private static final int UNREACHABLE_TICKS = 300;

    /**
     * How long a worker stands with no path left before giving the target up. This is the case that
     * used to make workers freeze by a tree: they picked a block, walked as close as the terrain
     * allowed, the pathfinder found nothing more, and they stood there. A couple of seconds of that
     * is enough to conclude the block is out of reach.
     */
    private static final int STUCK_TICKS = 40;

    /** How long a target the worker could not reach is left alone so it is not picked again. */
    private static final int ABANDON_TICKS = 300;

    /** How long a worker walks toward a wander spot before it picks another one. */
    private static final int ROAM_TICKS = 200;

    /** How close to a wander spot counts as arrived. */
    private static final double ROAM_ARRIVED_SQR = 2.25d;

    /** How far from itself a worker will wander when there is nothing to do right here. */
    private static final int ROAM_RANGE = 8;

    /** How long a claimed block stays spoken for without being touched again. */
    private static final long CLAIM_TICKS = 1200L;

    /** Ticks between two swings at whatever is blocking the walk to the target. */
    private static final int PATH_CLEAR_TICKS = 5;

    /** Heights at which a block next to the target is tried, relative to the target. */
    private static final int[] STAND_HEIGHTS = {0, 1, -1};

    /** A block (or a whole tree) spoken for by one worker, so another does not join in. */
    private record Claim(int holder, long expiry) {}

    /** Claims held across every worker on the server, keyed by dimension and position. */
    private static final Map<GlobalPos, Claim> CLAIMS = new HashMap<>();

    protected final ShadowCloneEntity worker;

    private BlockPos target;
    private int cooldown;
    private int reachTicks;
    private int repathTicks;
    private int stuckTicks;
    private int roamTicks;
    private int clearCooldown;

    /**
     * The spots the worker may stand at to take the current target, nearest first, and which one it
     * is walking to. Walking at a single spot is fragile - the near side of a trunk may be walled
     * off while the far side is open - so the goal tries them in turn.
     */
    private List<BlockPos> standOptions = List.of();
    private int standIndex;

    /** True while {@link #target} is somewhere to wander to rather than a block to work on. */
    private boolean roaming;

    /** Blocks still to come down from the take in progress, and the countdown to the next one. */
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private int queueTicks;

    /** Offsets to probe, nearest ring first (x, y, z triples), and where each ring starts in it. */
    private int[] scanOffsets;
    private int[] scanShellStart;

    /**
     * Targets the worker could not get to, with the tick they may be picked again. Without this the
     * worker would give up on a block, re-scan, pick the very same nearest block and stand there for
     * another cycle - which looks like it simply froze by a tree.
     */
    private final Map<BlockPos, Integer> abandoned = new HashMap<>();

    protected WorkerBlockGoal(ShadowCloneEntity worker) {
        this.worker = worker;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The job this goal performs; a worker only ever does the one it was summoned for. */
    protected abstract WorkerJob job();

    /** @return whether a block is something this job takes. */
    protected abstract boolean matches(BlockState state);

    /**
     * Plans the take for the block the worker has walked up to: the blocks that should come down,
     * the one it reached first, the rest in the order they should follow.
     *
     * <p>A lumberjack hands over the whole trunk here and the base goal then takes it down a log at
     * a time; the others usually plan just the one block. The whole plan is scheduled at once, so
     * blocks higher up the tree do not have to be reachable in their own right.</p>
     *
     * @return the blocks to take, or an empty list when the block is no use after all.
     */
    protected abstract List<BlockPos> plan(ServerLevel level, BlockPos pos);

    /** Takes one block of the plan: breaks it, collects the drops and wears the tool. */
    protected abstract void take(ServerLevel level, BlockPos pos);

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

    /**
     * Extra blocks to claim together with the target. A lumberjack claims the whole trunk here, so a
     * second worker walking by sees the tree is taken and looks for one of its own.
     */
    protected Set<BlockPos> claimGroup(BlockPos pos) {
        return Set.of();
    }

    /**
     * Clears whatever of this job's kind stands between the worker and the block it is after, when
     * the walk has brought it as close as it can get. A lumberjack uses this for leaves: a trunk
     * wrapped in foliage leaves no path through, so the worker hacks the leaves out of the way and
     * tries again. The other jobs walk on open ground and have nothing to clear.
     *
     * @return whether a block was broken, in which case the worker sets off again.
     */
    protected boolean clearPath(ServerLevel level, BlockPos target) {
        return false;
    }

    /**
     * Whether a target has to have somewhere to stand beside it before it is taken.
     *
     * <p>Most jobs walk up to their block, so a block with no reachable side is no use to them. A
     * miner is the exception: it digs its way through the rock to the ore it can feel, so it is happy
     * to take a target no path reaches - {@link #clearPath(ServerLevel, BlockPos)} then opens the
     * way.</p>
     */
    protected boolean needsStand(BlockPos pos) {
        return true;
    }

    /**
     * What to do once a job has cleared a block out of the path to its target. By default the worker
     * simply sets off for its standing spot again; a miner that has just opened a tunnel overrides
     * this to step into it.
     */
    protected void afterClear(ServerLevel level, BlockPos target) {
        this.walkToStand();
    }

    @Override
    public boolean canUse() {
        if (!this.isMyJob()) {
            return false;
        }
        if (this.isDefending()) {
            // Something is on top of the worker. It drops the shift and hits back rather than
            // swinging at a block while it is being chewed on; the tool goes back to work once the
            // fight is over.
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.pruneBookkeeping();
        BlockPos found = this.findTarget();
        this.roaming = found == null;
        this.target = this.roaming ? this.findRoam() : found;
        if (!this.roaming && this.target != null) {
            this.claimAround(this.target);
        }
        this.cooldown = EMPTY_COOLDOWN;
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.isMyJob() || this.isDefending()) {
            return false;
        }
        if (!this.queue.isEmpty()) {
            return true;
        }
        if (this.target == null) {
            return false;
        }
        if (this.roaming) {
            return true;
        }
        BlockState state = this.worker.level().getBlockState(this.target);
        return this.matches(state) || this.secondary(state, this.target);
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.reachTicks = 0;
        this.stuckTicks = 0;
        this.roamTicks = ROAM_TICKS;
        this.standOptions = this.standOptions(this.target);
        this.standIndex = 0;
        this.walkToStand();
        this.repathTicks = REPATH_TICKS;
    }

    @Override
    public void stop() {
        this.queue.clear();
        this.target = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.clearCooldown > 0) {
            this.clearCooldown--;
        }
        if (!this.queue.isEmpty()) {
            this.takeFromQueue();
            return;
        }
        if (this.target == null) {
            return;
        }
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(this.target));

        if (this.roaming) {
            // Wander over: arrived, or took too long. Either way stop and look for real work again.
            if (this.worker.distanceToSqr(Vec3.atCenterOf(this.target)) <= ROAM_ARRIVED_SQR
                    || --this.roamTicks <= 0) {
                this.target = null;
                this.cooldown = EMPTY_COOLDOWN;
                this.worker.getNavigation().stop();
            }
            return;
        }

        if (this.worker.distanceToSqr(Vec3.atCenterOf(this.target)) > REACH_SQR) {
            if (++this.reachTicks > UNREACHABLE_TICKS) {
                this.abandon();
                return;
            }
            if (this.worker.getNavigation().isDone()) {
                // Already as close as the ground allows. Before writing the block off, let the job
                // clear whatever of its kind is in the way - a chopper hacks the leaves walling its
                // tree in - and then set off again.
                if (this.clearCooldown <= 0 && this.worker.level() instanceof ServerLevel level
                        && this.clearPath(level, this.target)) {
                    this.stuckTicks = 0;
                    this.clearCooldown = PATH_CLEAR_TICKS;
                    this.afterClear(level, this.target);
                    this.repathTicks = REPATH_TICKS;
                    return;
                }
                // This side did not get the worker there. Try the next spot to stand before giving
                // up: the near side of a trunk is often the awkward one, while the far side is open.
                if (this.nextStand()) {
                    this.stuckTicks = 0;
                    return;
                }
                // Every spot tried and still out of reach: retry a couple of times in case something
                // moved, then give it up so the worker does not freeze beside a tree it cannot get to.
                if (++this.stuckTicks > STUCK_TICKS) {
                    this.abandon();
                    return;
                }
                if (--this.repathTicks <= 0) {
                    this.repathTicks = REPATH_TICKS;
                    this.walkToStand();
                }
            } else {
                this.stuckTicks = 0;
            }
            return;
        }

        this.reachTicks = 0;
        this.stuckTicks = 0;
        if (this.worker.level() instanceof ServerLevel level) {
            List<BlockPos> planned = this.plan(level, this.target);
            if (!planned.isEmpty()) {
                this.claimAll(planned);
                this.queue.addAll(planned);
                this.queueTicks = 0;
                this.cooldown = SWING_COOLDOWN;
                this.target = null;
                this.worker.getNavigation().stop();
                return;
            }
        }
        // Nothing came of it (the tool would not take the block, or it was already gone): do not let
        // the worker pick the same spot again and swing at it in place.
        this.abandoned.put(this.target, this.worker.tickCount + ABANDON_TICKS);
        this.target = null;
        this.cooldown = SWING_COOLDOWN;
    }

    /** Takes the next planned block once the swing timer is up, so a take comes down block by block. */
    private void takeFromQueue() {
        BlockPos next = this.queue.peek();
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(next));
        if (--this.queueTicks > 0) {
            return;
        }
        this.queueTicks = TAKE_TICKS;
        this.queue.poll();
        if (this.worker.level() instanceof ServerLevel level) {
            this.take(level, next);
        }
    }

    /** Gives up on the current block: remember it for a while and stop walking at it. */
    private void abandon() {
        this.abandoned.put(this.target, this.worker.tickCount + ABANDON_TICKS);
        this.target = null;
        this.cooldown = EMPTY_COOLDOWN;
        this.worker.getNavigation().stop();
    }

    /** Whether this worker is this goal's kind of worker, and running on the server. */
    protected boolean isMyJob() {
        return this.worker.isWorker() && this.worker.getWorkerJob() == this.job()
                && !this.worker.level().isClientSide;
    }

    /**
     * Whether the worker is busy defending itself. Only a target it took in self defence ever lands
     * on a worker (see {@code ShadowCloneEntity#setTarget}), so a target means something is hitting
     * it and the shift has to wait.
     */
    private boolean isDefending() {
        return this.worker.getTarget() != null;
    }

    /** Drops expired claims and forgotten targets. */
    private void pruneBookkeeping() {
        if (!this.abandoned.isEmpty()) {
            this.abandoned.values().removeIf(until -> this.worker.tickCount >= until);
        }
        long now = this.worker.level().getGameTime();
        CLAIMS.values().removeIf(claim -> claim.expiry() < now);
    }

    private boolean isAbandoned(BlockPos pos) {
        Integer until = this.abandoned.get(pos);
        return until != null && this.worker.tickCount < until;
    }

    private void claimAround(BlockPos pos) {
        long expiry = this.claimExpiry();
        this.claim(pos, expiry);
        for (BlockPos extra : this.claimGroup(pos)) {
            this.claim(extra, expiry);
        }
    }

    private void claimAll(Collection<BlockPos> positions) {
        long expiry = this.claimExpiry();
        for (BlockPos pos : positions) {
            this.claim(pos, expiry);
        }
    }

    private void claim(BlockPos pos, long expiry) {
        CLAIMS.put(GlobalPos.of(this.worker.level().dimension(), pos.immutable()),
                new Claim(this.worker.getId(), expiry));
    }

    private long claimExpiry() {
        return this.worker.level().getGameTime() + CLAIM_TICKS;
    }

    /** Whether another worker has this block spoken for right now. */
    private boolean claimedByOther(BlockPos pos) {
        GlobalPos key = GlobalPos.of(this.worker.level().dimension(), pos);
        Claim claim = CLAIMS.get(key);
        if (claim == null) {
            return false;
        }
        long now = this.worker.level().getGameTime();
        // Expired, or a claim left over from before a world reload (game time went backwards).
        if (claim.expiry() < now || claim.expiry() - now > CLAIM_TICKS) {
            CLAIMS.remove(key);
            return false;
        }
        return claim.holder() != this.worker.getId();
    }

    private void walkToStand() {
        if (this.standOptions.isEmpty()) {
            return;
        }
        BlockPos stand = this.standOptions.get(Math.min(this.standIndex, this.standOptions.size() - 1));
        this.worker.getNavigation().moveTo(stand.getX() + 0.5d, stand.getY(), stand.getZ() + 0.5d, 1.0d);
    }

    /**
     * Steps to the next spot to stand and sets off for it.
     *
     * @return whether there was another spot to try.
     */
    private boolean nextStand() {
        if (this.standIndex + 1 >= this.standOptions.size()) {
            return false;
        }
        this.standIndex++;
        this.walkToStand();
        this.repathTicks = REPATH_TICKS;
        return true;
    }

    /**
     * The spots the worker could stand at to take a block, nearest first.
     *
     * <p>One spot is not enough: the nearest side of a trunk is often the awkward one (walled in by
     * the tree's own trunk on a wide one, or hemmed by a slope) while the far side is open, so the
     * goal walks to them in turn instead of writing the block off when the first try does not
     * arrive. The block itself is kept as the last resort - aiming straight at it makes the
     * pathfinder end the path at the closest spot it can actually reach, which tucks the worker in
     * against the trunk.</p>
     */
    private List<BlockPos> standOptions(BlockPos pos) {
        Level level = this.worker.level();
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return List.of(pos);
        }
        List<BlockPos> options = new ArrayList<>();
        for (int dy : STAND_HEIGHTS) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = pos.relative(dir).above(dy);
                if (this.isStandable(candidate)) {
                    options.add(candidate);
                }
            }
        }
        options.sort(Comparator.comparingDouble(candidate ->
                this.worker.distanceToSqr(Vec3.atCenterOf(candidate))));
        options.add(pos);
        return options;
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

    /**
     * A spot nearby to walk to when there is nothing to work on right here.
     *
     * <p>Workers are not tied to the block they were summoned over: when the ground around them is
     * picked clean they drift on and look for more, which is how a forest actually gets cleared
     * instead of a hole being dug around one post. This is a deliberate roaming goal rather than
     * leaning on the vanilla stroll, whose random walk can fall silent once a mob has stood still
     * for a while (it checks {@code getNoActionTime()}).</p>
     *
     * @return a standable spot to walk to, or null if there is nowhere to go.
     */
    private BlockPos findRoam() {
        BlockPos here = this.worker.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = this.worker.getRandom().nextInt(ROAM_RANGE * 2 + 1) - ROAM_RANGE;
            int dz = this.worker.getRandom().nextInt(ROAM_RANGE * 2 + 1) - ROAM_RANGE;
            if (dx == 0 && dz == 0) {
                continue;
            }
            for (int dy = 2; dy >= -4; dy--) {
                BlockPos candidate = here.offset(dx, dy, dz);
                if (this.isStandable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** @return the closest block matching {@code wanted} that the worker can walk to, or null. */
    private BlockPos findNearest(Predicate<BlockPos> wanted) {
        // A worker looks around wherever it is rather than around a fixed post: a forest it walks
        // through keeps offering work, and when the ground around it is picked clean it drifts on to
        // the next stand of trees by itself (see the roaming below).
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
                if (this.isAbandoned(cursor) || this.claimedByOther(cursor)
                        || !wanted.test(cursor) || (this.needsStand(cursor) && !this.hasStand(cursor))) {
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
    protected boolean isStandable(BlockPos pos) {
        Level level = this.worker.level();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
