package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.sounds.NarutoSounds;
import com.sekwah.narutomod.util.Inventories;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Summons the storage chest that this player's worker clones carry their harvest to.
 *
 * <p>A ninja only ever has one such chest: casting again packs it up and puts it down wherever the
 * caster is standing, taking its contents along. That is deliberate - workers deliver to the chest
 * wherever it is, so moving it is how a caster moves their whole operation. Once it stands, every
 * worker the caster summons drops what it gathers straight into it as it works, instead of carrying
 * a bag of its own (see {@code ShadowCloneEntity#collect}).</p>
 *
 * <p>The chest is placed where the caster is looking, like one placed by hand; if they are looking
 * at nothing it goes down in front of them instead.</p>
 */
public class StorageChestAbility extends Ability {

    /** B V C - kept short, since it is cast often. */
    public static final long COMBO = 321L;

    /** Chakra spent summoning or moving the chest. */
    private static final float COST = 20.0f;

    private static final int CHAKRA_REGEN_DELAY = 20 * 3;

    /** How far the caster can aim the chest when placing it. */
    private static final double REACH = 4.0d;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (ninjaData.getChakra() < COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level) || ninjaData.getChakra() < COST) {
            return;
        }

        BlockPos spot = this.findSpot(level, player);
        if (spot == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.storage_blocked",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ResourceLocation dimension = level.dimension().location();
        BlockPos old = ninjaData.getStashLoc();
        boolean alreadyHere = spot.equals(old) && dimension.equals(ninjaData.getStashDimension())
                && level.getBlockState(spot).is(Blocks.CHEST);
        if (alreadyHere) {
            player.displayClientMessage(Component.translatable("jutsu.storage.already")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        // Moving the chest: whatever it held goes to the new one, and the old block is cleared.
        List<ItemStack> carried = this.takeChest(player, old, ninjaData.getStashDimension(), spot);
        level.setBlockAndUpdate(spot, this.chestState(player));
        this.fill(level, spot, carried);

        ninjaData.useChakra(COST, CHAKRA_REGEN_DELAY);
        ninjaData.setStashLoc(spot, dimension);

        level.sendParticles(ParticleTypes.CLOUD, spot.getX() + 0.5d, spot.getY() + 0.6d, spot.getZ() + 0.5d,
                20, 0.35d, 0.35d, 0.35d, 0.02d);
        level.playSound(null, spot, NarutoSounds.SHADOW_CLONE_POOF.get(), SoundSource.PLAYERS, 0.7f, 1.1f);
        player.displayClientMessage(Component.translatable("jutsu.storage.placed")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** The chest faces back at the caster, the way one placed by hand would. */
    private BlockState chestState(Player player) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, player.getDirection().getOpposite());
    }

    /**
     * Picks where the chest goes: the block the caster is looking at, else the ground around them.
     *
     * @return a free spot to put the chest, or null when there is nowhere to put it.
     */
    private BlockPos findSpot(ServerLevel level, Player player) {
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos aimed = ((BlockHitResult) hit).getBlockPos().relative(((BlockHitResult) hit).getDirection());
            if (this.canPlaceAt(level, player, aimed)) {
                return aimed;
            }
        }
        BlockPos here = player.blockPosition();
        Direction front = player.getDirection();
        List<BlockPos> around = new ArrayList<>();
        around.add(here.relative(front));
        around.add(here.relative(front.getClockWise()));
        around.add(here.relative(front.getCounterClockWise()));
        around.add(here.relative(front.getOpposite()));
        for (BlockPos candidate : around) {
            if (this.canPlaceAt(level, player, candidate)) {
                return candidate;
            }
            if (this.canPlaceAt(level, player, candidate.above())) {
                return candidate.above();
            }
            if (this.canPlaceAt(level, player, candidate.below())) {
                return candidate.below();
            }
        }
        return null;
    }

    /** A spot is free when nothing is in it and the caster is not standing in it. */
    private boolean canPlaceAt(ServerLevel level, Player player, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        return !player.getBoundingBox().intersects(new AABB(pos));
    }

    /**
     * Empties the old chest and takes it away, so the stash can be moved without losing what it held.
     * Does nothing if the record points at the new spot, or if the old chunk is not loaded.
     */
    private List<ItemStack> takeChest(Player player, BlockPos old, ResourceLocation oldDimension, BlockPos newSpot) {
        if (old == null || old.equals(newSpot)) {
            return List.of();
        }
        ServerLevel level = this.otherLevel(player, oldDimension);
        if (level == null || !(level.getBlockEntity(old) instanceof Container container)) {
            return List.of();
        }
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        container.clearContent();
        level.removeBlock(old, false);
        return contents;
    }

    /** Puts what the old chest held into the new one, spilling any overflow on top of it. */
    private void fill(ServerLevel level, BlockPos spot, List<ItemStack> contents) {
        if (contents.isEmpty() || !(level.getBlockEntity(spot) instanceof Container container)) {
            return;
        }
        for (ItemStack stack : contents) {
            ItemStack leftover = Inventories.putInto(container, stack);
            if (!leftover.isEmpty()) {
                Block.popResource(level, spot, leftover);
            }
        }
    }

    private ServerLevel otherLevel(Player player, ResourceLocation dimension) {
        if (dimension == null || player.level().getServer() == null) {
            return null;
        }
        return player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
