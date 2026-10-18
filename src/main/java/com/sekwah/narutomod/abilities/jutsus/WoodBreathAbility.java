package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A warm, living breath of chakra that feeds the plants in front of the caster.
 *
 * <p>It does nothing to creatures - it is meant for growing things - and works exactly like a spread
 * of bonemeal: any block in the cone that would accept bonemeal and would actually grow is grown on
 * the spot, so crops ripen, saplings shoot up, and bare grass comes alive. Only a few blocks are fed
 * per pulse so a single breath cannot turn one hillside into an instant forest, and mastery is spent
 * on how far the breath reaches.</p>
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
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        // A breath of growth feeds the ground, not the creatures standing on it.
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
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

    /** Bonemeals a single block, if it is something that would take it and would really grow. */
    private static boolean grow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
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
}
