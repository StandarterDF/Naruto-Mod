package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The shared body of the elemental breaths: a cone of chakra held open in front of the caster and
 * paid for tick by tick.
 *
 * <p>A breath is a channelled technique and the whole thing is the channel - each tick it costs
 * chakra, sprays its element down the cone and touches whatever is caught in it. Holding it longer
 * reaches further, up to a point, and it pays for its mastery by the time it is actually breathed
 * rather than by the act of starting it. A tap that never held the keys is a single short puff, so
 * the breath can still be used for one quick touch of the world.</p>
 *
 * <p>Subclasses only decide what their element does: which particle shows it, which sound opens it,
 * and what the cone does to creatures and to blocks. The cone itself, the cost, the timing and the
 * visual spray all live here, so the breaths read the same and cannot drift apart. The fire breath
 * predates this class and stays on its own for its damage type and furnace accessors, but it works
 * to the same numbers.</p>
 */
public abstract class AbstractBreathAbility extends Ability implements Ability.Channeled {

    /** Chakra burned for every tick a breath is held. */
    protected static final float CHAKRA_PER_TICK = 0.5f;

    /** A tap that never held costs a flat puff instead of a tick. */
    protected static final float TAP_COST = 10.0f;

    protected static final int CHAKRA_REGEN_DELAY = 20;

    /** How far a breath reaches, from untrained to mastered. */
    protected static final double RANGE_MIN = 4.0d;
    protected static final double RANGE_MAX = 7.0d;

    /** The cosine of half the cone a breath fills; wider than this is outside the breath. */
    protected static final double CONE_COS = 0.55d;

    private static final int VISUAL_STEPS = 6;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = chargeAmount < 0 ? TAP_COST : this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        float mastery = this.masteryLevel(ninjaData);
        if (ticksChanneled == 0) {
            level.playSound(null, player, breathSound(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }
        emitBreath(level, player, mastery);
        if (ticksChanneled % affectInterval() == 0) {
            affectEntities(level, player, mastery);
            affectBlocks(level, player, mastery);
        }
        // A breath is lived in, not cast: its mastery comes from the time it is actually on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to do on release: the breath has already worked tick by tick. A tap that never held
        // the keys is a single puff, enough for one touch of the world.
        if (ticksActive < 0 && player.level() instanceof ServerLevel level) {
            float mastery = this.masteryLevel(ninjaData);
            emitBreath(level, player, mastery);
            affectEntities(level, player, mastery);
            affectBlocks(level, player, mastery);
        }
    }

    /** How often the breath touches creatures and blocks, in ticks. */
    protected int affectInterval() {
        return 20;
    }

    /** The sound a breath makes when it opens. */
    protected abstract SoundEvent breathSound();

    /** The particle the breath is drawn with. */
    protected abstract ParticleOptions breathParticle();

    /** A second, sparser particle mixed into the spray, or null for none. */
    @Nullable
    protected ParticleOptions secondaryParticle() {
        return null;
    }

    /** What the breath does to everything alive in the cone. */
    protected abstract void affectEntities(ServerLevel level, Player player, float mastery);

    /** What the breath does to the blocks in the cone. */
    protected abstract void affectBlocks(ServerLevel level, Player player, float mastery);

    /** Which element this breath belongs to, for the world multipliers in {@link WorldEnvironment}. */
    protected abstract WorldEnvironment.Element element();

    /** How far this breath reaches, after the weather and biome have had their say. */
    protected double range(Player player, float mastery) {
        return Mth.lerp(mastery, RANGE_MIN, RANGE_MAX)
                * WorldEnvironment.rangeMultiplier(player, element());
    }

    /** The spray of particles itself, purely for looks. */
    protected void emitBreath(ServerLevel level, Player player, float mastery) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double range = range(player, mastery);
        ParticleOptions secondary = secondaryParticle();
        for (int step = 1; step <= VISUAL_STEPS; step++) {
            double dist = range * step / (double) VISUAL_STEPS;
            double spread = 0.08d * step;
            Vec3 point = eye.add(look.scale(dist));
            level.sendParticles(breathParticle(), point.x, point.y, point.z, 3, spread, spread, spread, 0.01d);
            if (secondary != null && step % 2 == 0) {
                level.sendParticles(secondary, point.x, point.y, point.z, 1, spread, spread, spread, 0.0d);
            }
        }
    }

    /** Everything alive caught in the cone, the caster and their allies excluded. */
    protected List<LivingEntity> coneEntities(ServerLevel level, Player player, double range) {
        return BreathCone.entities(level, player, range, CONE_COS);
    }

    /**
     * Runs the consumer over every block position inside the cone. Positions are collected through a
     * plain coordinate walk, so a consumer is free to change the world as it goes.
     */
    protected void forEachConeBlock(Player player, double range, Consumer<BlockPos> consumer) {
        BreathCone.forEachBlock(player, range, CONE_COS, consumer);
    }
}
