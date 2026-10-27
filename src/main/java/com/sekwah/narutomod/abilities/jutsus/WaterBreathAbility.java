package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.util.ElementalStates;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A cold jet of water chakra breathed out in front of the caster.
 *
 * <p>It is the quiet, practical counterpart to the fire breath: it burns nothing, but it puts things
 * out and waters things down. A creature caught in it has its fire put out and is nudged back a
 * little by the pressure; water-born creatures that fear water are hurt by it. A spread of flame is
 * snuffed out, a lit campfire is put out, a dry sponge is soaked back into a wet one, farmland is
 * watered, dirt turns to mud, cauldrons fill up, concrete powder sets, copper weathers faster and a
 * cauldron (or ice) never stays the way it was. In the Nether the water turns to steam before it can
 * touch the ground, so only the creatures feel it. Mastery is spent on how far the jet reaches and
 * how hard it presses.</p>
 */
public class WaterBreathAbility extends AbstractBreathAbility {

    /** C V B V */
    public static final long COMBO = 3321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "water_breath");

    /** How hard the jet presses a creature back, from untrained to mastered. */
    private static final float PUSH_MIN = 0.12f;
    private static final float PUSH_MAX = 0.3f;

    /** How many blocks one pulse may change, so a jet does not rewrite a whole field at once. */
    private static final int MAX_BLOCK_CHANGES = 32;

    /** Damage dealt to creatures that are naturally hurt by water (blazes, magma cubes, endermen). */
    private static final float WATER_DAMAGE = 2.0f;

    /** Chance for a block of copper in the jet to weather one step further. */
    private static final float COPPER_WEATHER_CHANCE = 0.12f;

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
    protected WorldEnvironment.Element element() {
        return WorldEnvironment.Element.WATER;
    }

    @Override
    protected void affectEntities(ServerLevel level, Player player, float mastery) {
        float push = Mth.lerp(mastery, PUSH_MIN, PUSH_MAX);
        double range = range(player, mastery);
        float damage = WATER_DAMAGE * WorldEnvironment.damageMultiplier(player, element());
        Vec3 look = player.getViewVector(1.0f);
        for (LivingEntity target : coneEntities(level, player, range)) {
            if (target.isSensitiveToWater()) {
                target.hurt(level.damageSources().drown(), damage);
                level.sendParticles(ParticleTypes.CLOUD,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        6, 0.3d, 0.3d, 0.3d, 0.02d);
            }
            if (target.isOnFire()) {
                target.clearFire();
                // Water meeting flame is a burst of steam, not a silent snuff: the hiss and the cloud
                // are the point, and the creature is left soaked rather than merely unlit.
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8f, 0.8f);
                level.sendParticles(ParticleTypes.CLOUD,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                        12, 0.4d, 0.4d, 0.4d, 0.03d);
            }
            ElementalStates.setWet(target, ElementalStates.wetTicksAt(level, target.blockPosition()));
            if (ElementalStates.freezingAt(level, target.blockPosition())) {
                ElementalStates.setChilled(target, ElementalStates.CHILLED_TICKS);
            }
            target.push(look.x * push, 0.0d, look.z * push);
            target.hurtMarked = true;
        }
    }

    @Override
    protected void affectBlocks(ServerLevel level, Player player, float mastery) {
        double range = range(player, mastery);
        // Water cannot reach the ground in the Nether: it boils away in the air before it lands, so
        // the breath still puts out burning creatures but changes no blocks there.
        boolean nether = level.dimension() == Level.NETHER;
        int[] remaining = {MAX_BLOCK_CHANGES};
        forEachConeBlock(player, range, pos -> {
            if (nether || remaining[0] <= 0) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.SPONGE)) {
                level.setBlockAndUpdate(pos, Blocks.WET_SPONGE.defaultBlockState());
                extinguishEffects(level, pos);
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
                extinguishEffects(level, pos);
                remaining[0]--;
                return;
            }
            if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
                extinguishEffects(level, pos);
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.CAULDRON)) {
                level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                level.setBlockAndUpdate(pos, state.setValue(LayeredCauldronBlock.LEVEL,
                        LayeredCauldronBlock.MAX_FILL_LEVEL));
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                level.setBlockAndUpdate(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
                remaining[0]--;
                return;
            }
            if (state.is(Blocks.DIRT)) {
                level.setBlockAndUpdate(pos, Blocks.MUD.defaultBlockState());
                remaining[0]--;
                return;
            }
            Optional<Block> concrete = concreteFor(state);
            if (concrete.isPresent()) {
                level.setBlockAndUpdate(pos, concrete.get().defaultBlockState());
                remaining[0]--;
                return;
            }
            if (state.getBlock() instanceof WeatheringCopper copper
                    && level.random.nextFloat() < COPPER_WEATHER_CHANCE) {
                Optional<BlockState> next = copper.getNext(state);
                if (next.isPresent()) {
                    level.setBlockAndUpdate(pos, next.get());
                    remaining[0]--;
                }
            }
        });
    }

    private static void extinguishEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                6, 0.3d, 0.3d, 0.3d, 0.02d);
    }

    /**
     * The concrete a block of powder would set into, or empty for anything else. Vanilla turns powder
     * into concrete on contact with water; the name is the only link between the two blocks, which
     * keeps every colour working without a table.
     */
    private static Optional<Block> concreteFor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        if (!path.endsWith("_concrete_powder")) {
            return Optional.empty();
        }
        ResourceLocation concreteId = new ResourceLocation(id.getNamespace(),
                path.substring(0, path.length() - "_powder".length()));
        return BuiltInRegistries.BLOCK.getOptional(concreteId);
    }

    /** The element this technique belongs to, for the caster's innate affinity. */
    @Override
    public com.sekwah.narutomod.abilities.Nature nature() {
        return com.sekwah.narutomod.abilities.Nature.WATER;
    }
}
