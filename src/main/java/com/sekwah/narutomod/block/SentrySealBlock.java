package com.sekwah.narutomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A chakra seal laid flat on the ground to watch a perimeter.
 *
 * <p>It is an ordinary block with a block entity that scans for hostile mobs, so it needs a chunk
 * loaded to work - that is the honest limit of a static ward. When something hostile steps inside
 * the radius it chimes and throws sparks, which is enough to wake a base up without adding a GUI or
 * an entity that would be visible from across the map.</p>
 *
 * <p>The block is deliberately cheap to break: a ward is a warning, not a wall.</p>
 */
public class SentrySealBlock extends BaseEntityBlock {

    /** The seal is a plate a pixel or so thick, so it sits flush with the floor. */
    private static final VoxelShape SHAPE = Block.box(2.0d, 0.0d, 2.0d, 14.0d, 1.0d, 14.0d);

    public SentrySealBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SentrySealBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // Scanning mobs is server work; the client only draws the block.
        return level.isClientSide ? null
                : createTickerHelper(type, NarutoBlocks.SENTRY_SEAL_ENTITY.get(),
                SentrySealBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SentrySealBlockEntity seal) {
            seal.setOwner(placer);
        }
    }
}
