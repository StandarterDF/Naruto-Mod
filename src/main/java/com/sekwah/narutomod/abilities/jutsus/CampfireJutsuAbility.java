package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Sets the log under the caster's crosshair alight where it stands, leaving a campfire behind.
 *
 * <p>Only the one log is taken, so a trunk burns down block by block from wherever the caster is
 * pointing, and each cast is priced as the single fire it plants. It is the cheap way to get light,
 * cooking and warmth out of a forest without hauling flint and steel (or a whole campfire) around,
 * and it works on anything that would burn in a furnace - {@code #minecraft:logs_that_burn} - so a
 * warped stem is politely refused rather than turned into a fire that should not exist.</p>
 *
 * <p>The log is checked before the chakra is taken, so aiming at a leaf, a wall or thin air is a
 * failed cast that costs nothing.</p>
 */
public class CampfireJutsuAbility extends Ability {

    /** B C B */
    public static final long COMBO = 313L;

    /** How far the caster reaches for a log, in blocks. */
    private static final double REACH = 8.0d;

    /** Chakra for turning one log into one campfire. */
    private static final float CHAKRA_COST = 20.0f;

    /** How long natural chakra regeneration stays suppressed after a cast. */
    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (ninjaData.getChakra() < CHAKRA_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (this.lookedAtLog(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.campfire.notlog",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_COST, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Looked up again rather than remembered between the two calls: the cost and the effect are
        // separate steps, and the caster may have turned away or the log may be gone in between.
        BlockHitResult hit = this.lookedAtLog(player);
        if (hit == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, true)
                .setValue(CampfireBlock.FACING, player.getDirection().getOpposite());
        level.setBlockAndUpdate(pos, campfire);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5d, pos.getY() + 0.6d, pos.getZ() + 0.5d,
                24, 0.4d, 0.4d, 0.4d, 0.02d);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d,
                8, 0.3d, 0.3d, 0.3d, 0.01d);
    }

    /**
     * The block hit, but only when the block behind it is a log that would burn.
     *
     * <p>The tag is the burnable one rather than {@code #minecraft:logs} so that fire is only made of
     * wood that fire can actually eat.</p>
     */
    @Nullable
    private BlockHitResult lookedAtLog(Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, REACH);
        if (hit == null || !player.level().getBlockState(hit.getBlockPos()).is(BlockTags.LOGS_THAT_BURN)) {
            return null;
        }
        return hit;
    }
}
