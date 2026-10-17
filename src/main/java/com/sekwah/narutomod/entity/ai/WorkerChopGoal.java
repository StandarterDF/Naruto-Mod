package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The axe shift of a worker clone: it fells the trees around itself and carries the logs back in its
 * bag.
 *
 * <p>Reaching a trunk plans the whole thing, but the goal brings it down a log at a time (from the
 * stump up), so a tree visibly falls apart instead of vanishing in one swing. The cap keeps a worker
 * from clearing a whole forest, or wandering off the loaded chunks, in a single take.</p>
 */
public class WorkerChopGoal extends WorkerBlockGoal {

    /** Cap on one felling. High enough for a jungle giant, low enough not to eat a whole forest
     *  (and the loaded chunks around it) in a single take. */
    private static final int MAX_LOGS = 128;

    public WorkerChopGoal(ShadowCloneEntity worker) {
        super(worker);
    }

    @Override
    protected WorkerJob job() {
        return WorkerJob.CHOP;
    }

    /**
     * A lumberjack sweeps a much wider patch than the other jobs: the whole point of it is to strip
     * the woods around it, so it looks a good couple of dozen blocks out for the next tree.
     */
    @Override
    protected int searchRadius() {
        return 24;
    }

    @Override
    protected boolean matches(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    /**
     * Leaves are not wood, but they can wall a trunk in so that no log has a reachable side. When no
     * log can be got at, the chopper clears the leaves in its way and only then reaches the trunk.
     * Only leaves touching a log count, so a worker is not turned into a leaf mower once the wood is
     * gone.
     */
    @Override
    protected boolean secondary(BlockState state, BlockPos pos) {
        if (!state.is(BlockTags.LEAVES)) {
            return false;
        }
        Level level = this.worker.level();
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The whole trunk, hung from the stump up. The base goal then takes them one at a time, which is
     * why the upper logs do not need a way to walk to them.
     */
    @Override
    protected List<BlockPos> plan(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LOGS)) {
            List<BlockPos> trunk = new ArrayList<>(this.connectedLogs(level, pos));
            trunk.sort(Comparator.comparingInt(BlockPos::getY));
            return trunk;
        }
        if (this.secondary(state, pos)) {
            // A leaf cleared to get at a trunk: take just the one block and move on.
            return List.of(pos.immutable());
        }
        return List.of();
    }

    @Override
    protected void take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LOGS) || this.secondary(state, pos)) {
            this.breakBlock(level, pos, state);
        }
    }

    /**
     * Hacks the leaves out of the way when they wall a tree in so that no path to it opens.
     *
     * <p>The worker samples the straight line from itself to the trunk and takes the first leaf on
     * it, one swing at a time: a tunnel through the canopy opens up instead of the whole thing
     * coming down, and the leaves it clears are genuinely the ones between it and the wood.</p>
     */
    @Override
    protected boolean clearPath(ServerLevel level, BlockPos target) {
        Vec3 from = this.worker.position();
        Vec3 to = new Vec3(target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d);
        Vec3 ray = to.subtract(from);
        double length = ray.length();
        if (length < 1.0e-4d) {
            return false;
        }
        Vec3 direction = ray.scale(1.0d / length);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double travelled = 0.5d; travelled < length; travelled += 0.45d) {
            Vec3 point = from.add(direction.scale(travelled));
            // Check the block the line passes through and the one right above it, since a body
            // standing in the way is usually two leaves tall.
            for (int dy = 0; dy <= 1; dy++) {
                cursor.set(point.x, point.y + dy, point.z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(BlockTags.LEAVES)) {
                    this.breakBlock(level, cursor, state);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A whole trunk is spoken for the moment a chopper picks it, so the other workers in the wood
     * move on to trees of their own instead of all hacking at the same one.
     */
    @Override
    protected Set<BlockPos> claimGroup(BlockPos pos) {
        Level level = this.worker.level();
        if (level instanceof ServerLevel server && level.getBlockState(pos).is(BlockTags.LOGS)) {
            return this.connectedLogs(server, pos);
        }
        return Set.of();
    }

    /** Takes one block into the bag, plays the break effect, wears the axe and removes it. */
    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = this.worker.getMainHandItem();
        for (ItemStack drop : Block.getDrops(state, level, pos, null, this.worker, tool)) {
            this.worker.collect(drop);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        // Wears the axe down, and empties its slot when it finally snaps. (A clone is not a
        // Player, so ItemStack#mineBlock cannot be used here.)
        tool.hurtAndBreak(1, this.worker, entity -> { });
        level.destroyBlock(pos, false);
    }

    /** @return the trunk connected to the first log, capped at {@link #MAX_LOGS} blocks. */
    private Set<BlockPos> connectedLogs(ServerLevel level, BlockPos start) {
        Set<BlockPos> logs = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        logs.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos current = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1),
                    current.offset(1, 1, 1))) {
                BlockPos candidate = next.immutable();
                if (logs.contains(candidate)) {
                    continue;
                }
                if (level.getBlockState(candidate).is(BlockTags.LOGS)) {
                    logs.add(candidate);
                    queue.add(candidate);
                }
            }
        }
        return logs;
    }
}
