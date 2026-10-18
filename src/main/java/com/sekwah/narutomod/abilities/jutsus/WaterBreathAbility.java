package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A cold jet of water chakra breathed out in front of the caster.
 *
 * <p>It is the quiet, practical counterpart to the fire breath: it hurts nothing and burns nothing,
 * but it puts things out and waters things down. A creature caught in it has its fire put out and is
 * nudged back a little by the pressure, a spread of flame is snuffed out, a lit campfire is put out,
 * and - the reverse of what the fire breath does - a dry sponge is soaked back into a wet one. It is
 * the breath for undoing fire and for preparing a sponge, and mastery is spent on how far it reaches
 * and how hard the jet presses.</p>
 */
public class WaterBreathAbility extends AbstractBreathAbility {

    /** C V B V */
    public static final long COMBO = 1232L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "water_breath");

    /** How hard the jet presses a creature back, from untrained to mastered. */
    private static final float PUSH_MIN = 0.12f;
    private static final float PUSH_MAX = 0.3f;

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    protected SoundEvent breathSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected ParticleOptions breathParticle() {
        return ParticleTypes.SPLASH;
    }

    @Override
    protected ParticleOptions secondaryParticle() {
        return ParticleTypes.BUBBLE;
    }

    @Override
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        float push = Mth.lerp(mastery, PUSH_MIN, PUSH_MAX);
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        Vec3 look = player.getViewVector(1.0f);
        for (LivingEntity target : coneEntities(level, player, range)) {
            if (target.isOnFire()) {
                target.clearFire();
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.5f, 1.0f);
            }
            target.push(look.x * push, 0.0d, look.z * push);
            target.hurtMarked = true;
        }
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        forEachConeBlock(player, range, pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.SPONGE)) {
                level.setBlockAndUpdate(pos, Blocks.WET_SPONGE.defaultBlockState());
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d,
                        8, 0.4d, 0.2d, 0.4d, 0.02d);
                return;
            }
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                        6, 0.3d, 0.3d, 0.3d, 0.02d);
                return;
            }
            if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                        6, 0.3d, 0.3d, 0.3d, 0.02d);
            }
        });
    }
}
