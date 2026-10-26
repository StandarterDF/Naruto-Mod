package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A warm, living breath of chakra that feeds the plants in front of the caster.
 *
 * <p>It does nothing to creatures - it is meant for growing things - and works exactly like a spread
 * of bonemeal: any block in the cone that would accept bonemeal and would actually grow is grown on
 * the spot, so crops ripen, saplings shoot up, bamboo rises, kelp and vines reach higher, glow berries
 * and cocoa fill out, chorus flowers unfold and bare grass comes alive. Sugar cane and cacti do not
 * take bonemeal, so the breath simply nudges their own growth; a composter in the cone is fed one
 * stage at a time. Only a few blocks are fed per pulse so a single breath cannot turn one hillside
 * into an instant forest, and mastery is spent on how far the breath reaches.</p>
 */
public class WoodBreathAbility extends AbstractBreathAbility {

    /** C V C C */
    public static final long COMBO = 1211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "wood_breath");

    /** How many plants one pulse may grow, so a breath cannot grow a whole forest in a second. */
    private static final int MAX_GROWTHS = 4;

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    protected SoundEvent breathSound() {
        return SoundEvents.BONE_MEAL_USE;
    }

    @Override
    protected ParticleOptions breathParticle() {
        return ParticleTypes.HAPPY_VILLAGER;
    }

    @Override
    protected ParticleOptions secondaryParticle() {
        return ParticleTypes.COMPOSTER;
    }

    @Override
    protected WorldEnvironment.Element element() {
        return WorldEnvironment.Element.WOOD;
    }

    @Override
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        // A breath of growth feeds the ground, not the creatures standing on it.
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = range(player, mastery);
        int[] grown = {0};
        forEachConeBlock(player, range, pos -> {
            if (grown[0] >= MAX_GROWTHS) {
                return;
            }
            if (grow(level, pos)) {
                grown[0]++;
            }
        });
    }

    /**
     * Feeds a single block: bonemeal for anything that takes it, a few of the plant's own growth
     * ticks for sugar cane and cacti (which are grown by time, not by bonemeal), and one stage of
     * compost for a composter. Returns true when the block actually changed.
     */
    private static boolean grow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ComposterBlock) {
            int fill = state.getValue(ComposterBlock.LEVEL);
            if (fill >= ComposterBlock.MAX_LEVEL) {
                return false;
            }
            level.setBlockAndUpdate(pos, state.setValue(ComposterBlock.LEVEL, fill + 1));
            return true;
        }
        if (state.getBlock() instanceof SugarCaneBlock || state.getBlock() instanceof CactusBlock) {
            // These two ignore bonemeal, so borrow their random tick a few times instead.
            for (int attempt = 0; attempt < 3; attempt++) {
                state.randomTick(level, pos, level.random);
            }
            return true;
        }
        if (!(state.getBlock() instanceof BonemealableBlock growable)) {
            return false;
        }
        if (!growable.isValidBonemealTarget(level, pos, state, false)) {
            return false;
        }
        if (!growable.isBonemealSuccess(level, level.random, pos, state)) {
            return false;
        }
        growable.performBonemeal(level, level.random, pos, state);
        return true;
    }

    /** The element this technique belongs to, for the caster's innate affinity. */
    @Override
    public com.sekwah.narutomod.abilities.Nature nature() {
        return com.sekwah.narutomod.abilities.Nature.WOOD;
    }
}
