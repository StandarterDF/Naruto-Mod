package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A worker's habit of lighting its own way: when the spot it is working in is dark and it is
 * carrying torches, it plants one underfoot and carries on.
 *
 * <p>The torches are copied from the caster when the shift starts (see
 * {@code ShadowCloneEntity#copyWorkerTorches}), so a worker sent down a mine with a stack of them
 * leaves a lit trail behind instead of groping about in the black - and a miner that has tunnelled
 * into solid rock lights the tunnel it just opened.</p>
 *
 * <p>The goal claims no {@link Flag}s on purpose: placing a torch is something a worker does
 * <em>while</em> it works, not a job of its own, so it rides along with whichever shift goal is
 * running instead of fighting it for movement or looking.</p>
 */
public class WorkerTorchGoal extends Goal {

    /** How often a worker stops to look at the light around it, in ticks. */
    private static final int CHECK_INTERVAL = 40;

    /** Raw brightness below which a worker decides it is too dark to keep working. */
    private static final int LIGHT_THRESHOLD = 7;

    private final ShadowCloneEntity worker;

    private int cooldown;
    private BlockPos placement;

    public WorkerTorchGoal(ShadowCloneEntity worker) {
        this.worker = worker;
    }

    @Override
    public boolean canUse() {
        if (!this.worker.isWorker() || this.worker.level().isClientSide) {
            return false;
        }
        if (this.worker.getWorkerTorches() <= 0) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = CHECK_INTERVAL;
        if (!this.isDark()) {
            return false;
        }
        this.placement = this.findPlacement();
        return this.placement != null;
    }

    @Override
    public boolean canContinueToUse() {
        // The torch is planted the moment the goal starts; there is nothing to keep doing.
        return false;
    }

    @Override
    public void start() {
        if (this.placement != null && this.worker.level() instanceof ServerLevel level) {
            level.setBlockAndUpdate(this.placement, Blocks.TORCH.defaultBlockState());
            level.playSound(null, this.placement, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
            this.worker.consumeTorch();
        }
        this.placement = null;
    }

    /** @return whether the spot the worker stands in is too dark to work by. */
    private boolean isDark() {
        Level level = this.worker.level();
        return level.getMaxLocalRawBrightness(this.worker.blockPosition()) < LIGHT_THRESHOLD;
    }

    /** The first free, torch-holding spot at the worker's feet or right beside it, or null. */
    private BlockPos findPlacement() {
        Level level = this.worker.level();
        BlockPos feet = this.worker.blockPosition();
        if (this.canPlaceAt(level, feet)) {
            return feet;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (this.canPlaceAt(level, side)) {
                return side;
            }
        }
        return null;
    }

    private boolean canPlaceAt(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        BlockState torch = Blocks.TORCH.defaultBlockState();
        return torch.canSurvive(level, pos);
    }
}
