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
import net.minecraftforge.common.Tags;

import java.util.List;

/**
 * The pickaxe shift of a worker clone: it digs the stone and the ore out of the ground around its
 * post and carries the spoils back in its bag.
 *
 * <p>Only what the pickaxe in its hand would actually take is broken, so a worker handed a wooden
 * pickaxe leaves the diamonds where they are instead of destroying them. The pickaxe wears down as
 * it works and the shift simply ends when it breaks.</p>
 */
public class WorkerMineGoal extends WorkerBlockGoal {

    public WorkerMineGoal(ShadowCloneEntity worker) {
        super(worker);
    }

    @Override
    protected WorkerJob job() {
        return WorkerJob.MINE;
    }

    /** A miner works a wider patch than a farmer: it is digging a hole, not weeding a field. */
    @Override
    protected int searchRadius() {
        return 12;
    }

    @Override
    protected boolean matches(BlockState state) {
        // Stone and the rock around it, plus everything the ore tags call an ore, which includes
        // modded ones through forge:ores.
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES);
    }

    @Override
    protected List<BlockPos> plan(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!this.matches(state) || !this.worker.getMainHandItem().isCorrectToolForDrops(state)) {
            return List.of();
        }
        return List.of(pos.immutable());
    }

    @Override
    protected void take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ItemStack tool = this.worker.getMainHandItem();
        if (!this.matches(state) || !tool.isCorrectToolForDrops(state)) {
            return;
        }
        for (ItemStack drop : Block.getDrops(state, level, pos, null, this.worker, tool)) {
            this.worker.collect(drop);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        // Wears the tool down, and empties its slot when it finally snaps. (A clone is not a Player,
        // so ItemStack#mineBlock cannot be used here.)
        tool.hurtAndBreak(1, this.worker, entity -> { });
        level.destroyBlock(pos, false);
    }
}
