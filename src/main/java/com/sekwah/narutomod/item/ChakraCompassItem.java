package com.sekwah.narutomod.item;

import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A compass that points not at the world spawn but at a place the ninja chose.
 *
 * <p>It is the answer to "where was that dungeon": right-click while looking at a block to bind the
 * compass to it, and the needle will keep pointing there from then on, through chests, death and
 * even other dimensions (a needle cannot point across worlds, so it spins instead of lying). Sneak
 * and right-click wipes the binding.</p>
 *
 * <p>The needle itself is handled by the vanilla compass model and vanilla's own angle maths, see
 * {@code client/NarutoClientItems}; this class only owns the binding.</p>
 */
public class ChakraCompassItem extends Item {

    /** How far away a block can be looked at while binding. */
    private static final double BIND_REACH = 32.0d;

    private static final String POS_TAG = "TargetPos";
    private static final String DIM_TAG = "TargetDim";

    public ChakraCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (player.isShiftKeyDown()) {
            clearTarget(stack);
            player.displayClientMessage(Component.translatable("item.narutomod.chakra_compass.cleared")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, BIND_REACH);
        if (hit == null || !(level instanceof ServerLevel serverLevel)) {
            player.displayClientMessage(Component.translatable("item.narutomod.chakra_compass.noblock")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        BlockPos pos = hit.getBlockPos();
        setTarget(stack, GlobalPos.of(serverLevel.dimension(), pos.immutable()));
        player.displayClientMessage(Component.translatable("item.narutomod.chakra_compass.bound",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = getTarget(stack);
        if (target == null) {
            tooltip.add(Component.translatable("item.narutomod.chakra_compass.unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            BlockPos pos = target.pos();
            tooltip.add(Component.translatable("item.narutomod.chakra_compass.target",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal(target.dimension().location().toString())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getTarget(stack) != null;
    }

    // ------------------------------------------------------------------------------------------
    // Binding data
    // ------------------------------------------------------------------------------------------

    /** Wipes the binding from a compass. */
    public static void clearTarget(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(POS_TAG);
        tag.remove(DIM_TAG);
    }

    /** @return the place this compass points at, or null if it has not been bound. */
    @Nullable
    public static GlobalPos getTarget(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(POS_TAG) || !tag.contains(DIM_TAG)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(DIM_TAG));
        if (dimension == null) {
            return null;
        }
        BlockPos pos = BlockPos.of(tag.getLong(POS_TAG));
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), pos);
    }

    private static void setTarget(ItemStack stack, GlobalPos target) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(POS_TAG, target.pos().asLong());
        tag.putString(DIM_TAG, target.dimension().location().toString());
    }
}
