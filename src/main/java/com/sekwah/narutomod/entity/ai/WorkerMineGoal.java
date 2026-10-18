package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.List;

/**
 * The pickaxe shift of a worker clone: it looks for the ore in the rock around itself and carries
 * the spoils back in its bag.
 *
 * <p>It goes for ore, not for plain stone: a worker that dug whatever rock was under its feet just
 * made a hole, while a miner that works the ore it can feel is actually prospecting. What sets it
 * apart from the other shifts is that it does not need to reach the ore on foot - when the vein is
 * walled in by stone on every side, the miner digs its way in instead, opening a tunnel a block at a
 * time until the ore is close enough to break.</p>
 *
 * <p>Only what the pickaxe in its hand would actually take is broken, so a worker handed a wooden
 * pickaxe leaves the diamonds where they are instead of destroying them, and it will not waste a
 * shift tunnelling toward ore it could never mine. The pickaxe wears down as it works and the shift
 * simply ends when it breaks.</p>
 */
public class WorkerMineGoal extends WorkerBlockGoal {

    /** Where the miner last opened the tunnel, so it can step in after the swing. */
    private BlockPos tunnelStep;

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

    /**
     * Ore the pickaxe in hand would actually take, through the forge ore tags so modded ores count
     * too. Plain stone is deliberately not here: digging it out from under itself is not mining, and
     * the stone on the way to an ore is dealt with by {@link #clearPath} instead.
     */
    @Override
    protected boolean matches(BlockState state) {
        return state.is(Tags.Blocks.ORES)
                && this.worker.getMainHandItem().isCorrectToolForDrops(state);
    }

    /**
     * A miner does not have to stand beside the ore, because it digs to it. Buried ore with no
     * reachable side is exactly the case this goal exists for: the target is taken anyway and
     * {@link #clearPath} opens the way through the stone.
     */
    @Override
    protected boolean needsStand(BlockPos pos) {
        return false;
    }

    @Override
    protected List<BlockPos> plan(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!this.matches(state)) {
            return List.of();
        }
        return List.of(pos.immutable());
    }

    @Override
    protected void take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!this.matches(state)) {
            return;
        }
        this.breakBlock(level, pos, state);
    }

    /**
     * Tunnels through the rock toward the ore the miner is after.
     *
     * <p>The line from the miner's feet to the ore is walked and the first stone on it is broken, at
     * both the floor and the head height so the tunnel is tall enough to step into; the miner then
     * walks into what it opened (see {@link #afterClear}) and digs again, so it advances block by
     * block instead of swinging at a wall from the same spot forever. Only blocks the pickaxe would
     * take are broken, so bedrock and the like simply stop the dig, and the ore itself is left for
     * the normal swing that carries its drops to the bag.</p>
     */
    @Override
    protected boolean clearPath(ServerLevel level, BlockPos target) {
        Vec3 from = this.worker.position();
        Vec3 to = new Vec3(target.getX() + 0.5d, target.getY() + 0.5d, target.getZ() + 0.5d);
        Vec3 ray = to.subtract(from);
        double length = ray.length();
        if (length < 1.0e-4d) {
            return false;
        }
        Vec3 direction = ray.scale(1.0d / length);
        ItemStack tool = this.worker.getMainHandItem();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double travelled = 0.9d; travelled < length; travelled += 0.45d) {
            Vec3 point = from.add(direction.scale(travelled));
            BlockPos floor = null;
            boolean broke = false;
            for (int dy = 0; dy <= 1; dy++) {
                cursor.set(point.x, point.y + dy, point.z);
                if (cursor.equals(this.worker.blockPosition())) {
                    continue;
                }
                BlockState state = level.getBlockState(cursor);
                if (state.is(Tags.Blocks.ORES) || !tool.isCorrectToolForDrops(state)
                        || state.getCollisionShape(level, cursor).isEmpty()) {
                    continue;
                }
                if (floor == null) {
                    floor = cursor.immutable();
                }
                this.breakBlock(level, cursor, state);
                broke = true;
            }
            if (broke) {
                this.tunnelStep = floor;
                return true;
            }
        }
        return false;
    }

    /**
     * Steps into the tunnel just opened, if a clone fits there. If the way is still blocked the
     * miner stays put and swings again from where it is.
     */
    @Override
    protected void afterClear(ServerLevel level, BlockPos target) {
        BlockPos step = this.tunnelStep;
        this.tunnelStep = null;
        if (step == null || !this.isStandable(step)) {
            return;
        }
        BlockPos head = step.above();
        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            return;
        }
        this.worker.getNavigation().moveTo(step.getX() + 0.5d, step.getY(), step.getZ() + 0.5d, 1.0d);
    }

    /** Takes one block into the bag, plays the break effect, wears the pickaxe and removes it. */
    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = this.worker.getMainHandItem();
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
