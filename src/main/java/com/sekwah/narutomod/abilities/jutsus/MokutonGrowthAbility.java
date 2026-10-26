package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Wood Release: growth, the gentle half of the element.
 *
 * <p>A wood user does not have to make plants grow one at a time. The caster lays a hand on the earth
 * they are looking at and the ground itself hurries: every crop, sapling and patch of grass in a
 * small square around that point is pushed along a stage at once. It is the wood release that feeds a
 * village rather than fights for it - cheap, quick to learn and useless in a duel.</p>
 *
 * <p>What can be grown is exactly what bonemeal can grow, because the technique <em>is</em> bonemeal
 * with a wider hand: the growth code is the block's own, so crops ripen, saplings take root and grass
 * spreads flowers the same way they would from a handful of powder. Nothing is placed or replaced,
 * which is also why it can never be used to grief a build.</p>
 */
public class MokutonGrowthAbility extends Ability implements Ability.Cooldown {

    /** B A V B A - the wood release seal, simplest of the elemental forms. */
    public static final long COMBO = 12111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mokuton_growth");

    /** How far the hand can be laid on the ground. */
    private static final double RANGE = 6.0d;

    /** Half the side of the square that grows: two blocks out is a five by five patch. */
    private static final int HALF_SIZE = 2;

    private static final float COST = 25.0f;
    private static final int COOLDOWN = 300;

    private static final int CHAKRA_REGEN_DELAY = 20;

    @Override
    public int noiseRadius() {
        return 16;
    }

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
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mokuton_growth.noblock",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Nothing in the square would answer: charging for it would teach the caster that the
        // technique is broken rather than that the ground is bare.
        if (!hasGrowth(player.level(), hit.getBlockPos())) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mokuton_growth.nogrowth",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
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

        int grown = 0;
        for (BlockPos pos : area(hit.getBlockPos())) {
            BlockPos target = pos.immutable();
            BlockState state = level.getBlockState(target);
            if (!(state.getBlock() instanceof BonemealableBlock bonemealable)
                    || !bonemealable.isValidBonemealTarget(level, target, state, false)) {
                continue;
            }
            bonemealable.performBonemeal(level, level.getRandom(), target, state);
            grown++;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5d,
                    target.getY() + 0.5d, target.getZ() + 0.5d, 4, 0.3d, 0.3d, 0.3d, 0.0d);
        }

        if (grown > 0) {
            level.playSound(null, hit.getBlockPos(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** The square the technique reaches, one layer deep at the block that was touched. */
    private static Iterable<BlockPos> area(BlockPos centre) {
        return BlockPos.betweenClosed(
                centre.offset(-HALF_SIZE, 0, -HALF_SIZE),
                centre.offset(HALF_SIZE, 0, HALF_SIZE));
    }

    /** Whether anything in the square could grow at all, asked before the caster is charged. */
    private static boolean hasGrowth(LevelReader level, BlockPos centre) {
        for (BlockPos pos : area(centre)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, pos, state, false)) {
                return true;
            }
        }
        return false;
    }
}
