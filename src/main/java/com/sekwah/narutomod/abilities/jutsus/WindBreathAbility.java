package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.ElementalStates;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hard gust of chakra breathed out in front of the caster.
 *
 * <p>It does no harm - nothing in the cone is hurt - but everything alive in it is shoved back along
 * the breath and lifted off its feet, and anything the wind catches that should not be there is torn
 * away: cobwebs, hanging vines and tripwires are simply gone, candles and campfires are snuffed out,
 * and loose snow is blown off the ground. Items, boats, lit TNT and falling sand are carried along
 * with it, and anything that flies is knocked out of the air. Channelled while gliding, it gives the
 * caster a shove forward and up and keeps the fall from counting. It is the breath for opening a way
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

    /** Flapping creatures get a stronger shove: it is meant to ground them. */
    private static final double FLYING_PUSH_MULTIPLIER = 1.5d;
    private static final double FLYING_LIFT = 0.5d;

    /** How often the gust shoves and tears, in ticks. */
    private static final int PUSH_INTERVAL = 10;

    /** How many blocks one pulse may clear, so a single breath does not strip a whole thicket. */
    private static final int MAX_BLOCK_CHANGES = 32;

    /** The shove the caster gets while gliding (elytra), so the breath can be used as a jet. */
    private static final double GLIDE_PUSH = 0.35d;
    private static final double GLIDE_LIFT = 0.18d;

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
    protected WorldEnvironment.Element element() {
        return WorldEnvironment.Element.WIND;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        super.handleChannelling(player, ninjaData, ticksChanneled);
        // A gust under the wings: while gliding the caster can ride their own breath, and the fall
        // distance is kept at zero so the boost does not turn into fall damage on the way down.
        if (player.isFallFlying()) {
            Vec3 look = player.getViewVector(1.0f);
            player.push(look.x * GLIDE_PUSH, GLIDE_LIFT, look.z * GLIDE_PUSH);
            player.resetFallDistance();
            player.hurtMarked = true;
        }
    }

    @Override
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        float push = Mth.lerp(mastery, PUSH_MIN, PUSH_MAX);
        double range = range(player, mastery);
        Vec3 look = player.getViewVector(1.0f);
        for (LivingEntity target : coneEntities(level, player, range)) {
            // A gust dries what it touches: soaked creatures are blown dry, and a chill goes with it.
            ElementalStates.dry(target);
            ElementalStates.warm(target);
            if (target instanceof FlyingMob || target instanceof FlyingAnimal) {
                target.push(look.x * push * FLYING_PUSH_MULTIPLIER, FLYING_LIFT,
                        look.z * push * FLYING_PUSH_MULTIPLIER);
            } else {
                target.push(look.x * push, PUSH_LIFT, look.z * push);
            }
            target.hurtMarked = true;
        }
        pushLooseThings(level, player, look, push, range);
    }

    /** Carries items, boats, lit TNT and falling sand along with the gust. */
    private static void pushLooseThings(ServerLevel level, Player player, Vec3 look, float push,
                                        double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 centre = eye.add(look.scale(range * 0.5d));
        AABB area = new AABB(centre, centre).inflate(range);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
                candidate -> candidate.isAlive()
                        && (candidate instanceof ItemEntity
                        || candidate instanceof Boat
                        || candidate instanceof PrimedTnt
                        || candidate instanceof FallingBlockEntity)
                        && BreathCone.insideCone(eye, look, candidate.position(), range, CONE_COS))) {
            entity.push(look.x * push, PUSH_LIFT * 0.5d, look.z * push);
            entity.hurtMarked = true;
        }
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = range(player, mastery);
        int[] remaining = {MAX_BLOCK_CHANGES};
        forEachConeBlock(player, range, pos -> {
            if (remaining[0] <= 0) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.VINE) || state.is(Blocks.TRIPWIRE)) {
                level.destroyBlock(pos, true, player);
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
                level.removeBlock(pos, false);
                remaining[0]--;
                return;
            }
            if (state.getBlock() instanceof AbstractCandleBlock && AbstractCandleBlock.isLit(state)) {
                level.setBlockAndUpdate(pos, state.setValue(AbstractCandleBlock.LIT, false));
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
                remaining[0]--;
                return;
            }
            if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
                remaining[0]--;
            }
        });
    }
}
