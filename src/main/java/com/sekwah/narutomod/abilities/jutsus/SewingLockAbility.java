package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.JouganExtraEvents;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Jougan Sewing Lock: the eye stitches a door, a gate or a lid to its frame for a while.
 *
 * <p>A locked thing is still the thing it was - the caster can walk through their own door - but the
 * seam will not let anyone else move it. That is what makes the technique a guard rather than a
 * barricade: nothing is placed, nothing is broken, and when the seam lets go the door is exactly as
 * it was built.</p>
 *
 * <p>Doors and chests are sewn on both halves, because the upper half of a door opens it just as
 * well as the lower one and the far half of a double chest is the same chest. The owner is recorded
 * rather than the whole player, so the lock survives the caster walking out of range.</p>
 */
public class SewingLockAbility extends Ability implements Ability.Cooldown {

    /** A seam tightening is a small sound. */
    @Override
    public int noiseRadius() {
        return 4;
    }

    /** B V V V V V V. */
    public static final long COMBO = 12131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "sewing_lock");

    /** How far down the look ray a door or a lid can be sewn. */
    private static final double RANGE = 8.0d;

    private static final float COST = 25.0f;
    private static final int COOLDOWN = 300;

    /** How long the seam holds, in ticks. */
    public static final int LOCK_TICKS = 600;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null || !JouganExtraEvents.isLockable(player.level().getBlockState(hit.getBlockPos()))) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sewing_lock.nothing",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!JouganExtraEvents.isLockable(state)) {
            return;
        }

        long until = level.getGameTime() + LOCK_TICKS;
        UUID owner = player.getUUID();
        JouganExtraEvents.lock(level, pos, owner, until);
        sewSecondHalf(level, pos, state, owner, until);

        Vec3 centre = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y, centre.z,
                18, 0.4d, 0.4d, 0.4d, 0.02d);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    /**
     * Sews the half of a two-part block that the ray did not hit. A door opens from either half and a
     * double chest is one container with two blocks, so a lock on one of them alone would be a lock
     * on the wrong thing.
     */
    private static void sewSecondHalf(ServerLevel level, BlockPos pos, BlockState state, UUID owner, long until) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            Direction other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    ? Direction.UP
                    : Direction.DOWN;
            JouganExtraEvents.lock(level, pos.relative(other), owner, until);
            return;
        }
        if (!(block instanceof ChestBlock)) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).getBlock() == block) {
                JouganExtraEvents.lock(level, neighbour, owner, until);
            }
        }
    }
}
