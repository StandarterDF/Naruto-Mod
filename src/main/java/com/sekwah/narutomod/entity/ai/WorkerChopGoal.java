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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The axe shift of a worker clone: it fells the trees around itself and carries the logs back in its
 * bag.
 *
 * <p>A single swing takes the whole trunk, not just the one log it chopped into: the logs connected
 * to it are collected too, so a worker is not left climbing a tree it has already started. The cap
 * keeps a worker from clearing a whole forest with one swing (or from wandering off the loaded
 * chunks).</p>
 */
public class WorkerChopGoal extends WorkerBlockGoal {

    /** Cap on one felling. High enough for a jungle giant, low enough not to eat a whole forest
     *  (and the loaded chunks around it) in a single swing. */
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

    @Override
    protected void work(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LOGS)) {
            // A leaf cleared to get at a trunk: take just the one block and move on.
            this.breakBlock(level, pos, state);
            return;
        }
        for (BlockPos logPos : this.connectedLogs(level, pos)) {
            BlockState logState = level.getBlockState(logPos);
            if (logState.is(BlockTags.LOGS)) {
                this.breakBlock(level, logPos, logState);
            }
        }
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
