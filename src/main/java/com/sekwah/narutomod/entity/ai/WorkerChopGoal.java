package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The axe shift of a worker clone: it fells the trees around its post and carries the logs back in
 * its bag.
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

    @Override
    protected void work(ServerLevel level, BlockPos pos) {
        for (BlockPos logPos : this.connectedLogs(level, pos)) {
            BlockState state = level.getBlockState(logPos);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            ItemStack tool = this.worker.getMainHandItem();
            for (ItemStack drop : Block.getDrops(state, level, logPos, null, this.worker, tool)) {
                this.worker.collect(drop);
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, logPos, Block.getId(state));
            // Wears the axe down, and empties its slot when it finally snaps. (A clone is not a
            // Player, so ItemStack#mineBlock cannot be used here.)
            tool.hurtAndBreak(1, this.worker, entity -> { });
            level.destroyBlock(logPos, false);
        }
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
