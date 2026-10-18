package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A hard gust of chakra breathed out in front of the caster.
 *
 * <p>It does no harm - nothing in the cone is hurt - but everything alive in it is shoved back along
 * the breath and lifted off its feet, and anything the wind catches that should not be there is torn
 * away: cobwebs, hanging vines and tripwires are simply gone. It is the breath for opening a way
 * through a place rather than for fighting in one, and mastery is spent on how hard the gust blows
 * and how far it reaches.</p>
 */
public class WindBreathAbility extends AbstractBreathAbility {

    /** C V B B */
    public static final long COMBO = 1233L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "wind_breath");

    /** How hard a gust shoves, from untrained to mastered. */
    private static final float PUSH_MIN = 0.35f;
    private static final float PUSH_MAX = 0.8f;

    /** A touch of lift so a shoved creature skips rather than slides. */
    private static final double PUSH_LIFT = 0.25d;

    /** How often the gust shoves and tears, in ticks. */
    private static final int PUSH_INTERVAL = 10;

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    protected int affectInterval() {
        return PUSH_INTERVAL;
    }

    @Override
    protected SoundEvent breathSound() {
        return SoundEvents.PHANTOM_FLAP;
    }

    @Override
    protected ParticleOptions breathParticle() {
        return ParticleTypes.CLOUD;
    }

    @Override
    protected ParticleOptions secondaryParticle() {
        return ParticleTypes.SWEEP_ATTACK;
    }

    @Override
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        float push = Mth.lerp(mastery, PUSH_MIN, PUSH_MAX);
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        Vec3 look = player.getViewVector(1.0f);
        for (LivingEntity target : coneEntities(level, player, range)) {
            target.push(look.x * push, PUSH_LIFT, look.z * push);
            target.hurtMarked = true;
        }
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        forEachConeBlock(player, range, pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.VINE) || state.is(Blocks.TRIPWIRE)) {
                level.destroyBlock(pos, true, player);
            }
        });
    }
}
