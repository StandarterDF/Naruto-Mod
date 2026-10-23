package com.sekwah.narutomod.mixin.common;

import com.sekwah.narutomod.block.NarutoBlocks;
import com.sekwah.narutomod.block.weapons.PaperBombBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a paper bomb act as one end of a tripwire, and sets off every bomb hanging at an end of the
 * wire when the wire itself is broken.
 */
@Mixin(TripWireBlock.class)
public class TripWireBlockMixin {

    /**
     * How far along the wire a bomb may sit, the same reach vanilla uses when it walks the wire
     * towards its hooks.
     */
    private static final int WIRE_REACH = 42;

    @Inject(method = "shouldConnectTo", at = @At("HEAD"), cancellable = true)
    public void shouldConnectTo(BlockState state, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        boolean shouldCheck = callback.getReturnValueZ();
        if (!shouldCheck && state.is(NarutoBlocks.PAPER_BOMB.get())) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "updateSource", at = @At(value = "RETURN"))
    public void detonateAttachedBombs(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        // Vanilla only calls this when the wire is placed or removed; the removal path is the one
        // that matters here, and it marks the state as powered so the hooks learn the wire is gone.
        if (level.isClientSide || !state.getValue(TripWireBlock.POWERED)) {
            return;
        }
        // Look down the wire in all four directions rather than just south and west: the removed
        // block may sit anywhere on the line, so the bombs can be on either side of it.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i < WIRE_REACH; i++) {
                BlockPos checked = pos.relative(direction, i);
                BlockState checkedState = level.getBlockState(checked);
                if (checkedState.is(NarutoBlocks.PAPER_BOMB.get())) {
                    ((PaperBombBlock) NarutoBlocks.PAPER_BOMB.get())
                            .spawnPaperbomb(checkedState, level, checked, null, true);
                    level.removeBlock(checked, false);
                    break;
                }
                if (!checkedState.is(state.getBlock())) {
                    break;
                }
            }
        }
    }

}
