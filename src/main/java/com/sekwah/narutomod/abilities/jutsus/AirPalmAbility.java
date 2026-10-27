package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Eight Trigrams: Empty Palm - Hakke Kuushou - the palm that never has to arrive.
 *
 * <p>The gentle fist is a technique of reach, and reach is wasted on a body that has already closed
 * it. This is the palm thrown as pressure instead: the caster pushes a wedge of chakra out of the
 * hand, and everything standing in the cone in front of them is shoved back and left with its
 * channels half closed. It barely cuts - a single point of damage, no more than a shove should - but
 * it takes a ninja's chakra with it and leaves the body weak for three seconds, which is exactly the
 * three seconds the caster needs to walk the real palms in.</p>
 *
 * <p>Allies stand in the cone unharmed: a technique that a Hyuga cannot use beside a teammate is a
 * technique that never gets used. The eye has to be open, see {@link ByakuganAbility}: the wedge is
 * aimed at the channels the eye is already reading.</p>
 */
public class AirPalmAbility extends Ability implements Ability.Cooldown {

    /** The shove is heard as a hard breath. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** V B V V B B C C - the eye's seal, then two keys for the empty palm. */
    public static final long COMBO = 1111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "air_palm");

    /** How far the wedge of chakra reaches, in blocks. */
    private static final double RANGE = 6.0d;

    /**
     * Cosine of half the cone's opening: 45 degrees to either side of the gaze. A wider cone would
     * catch bodies the caster is not actually pushing at.
     */
    private static final double CONE_COS = Math.cos(Math.toRadians(45.0d));

    /** What it costs and how long the eye needs before it can push again. */
    private static final float COST = 35.0f;
    private static final int COOLDOWN = 120;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Chakra pushed out of a ninja caught in the cone, capped by what they actually have. */
    private static final float DRAIN = 15.0f;

    /** How hard a caught body is thrown away from the caster, and how high it is lifted off its feet. */
    private static final double PUSH_SPEED = 0.8d;
    private static final double PUSH_LIFT = 0.1d;

    /** The strike itself is only the shove: the weight left behind is the real cost of being caught. */
    private static final float DAMAGE = 1.0f;
    private static final int WEAKNESS_TICKS = 60;

    /** How many points down the cone the sweep is drawn, and how many motes at each. */
    private static final int VISUAL_STEPS = 4;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<LivingEntity> caught = BreathCone.entities(level, player, RANGE, CONE_COS);
        for (LivingEntity target : caught) {
            if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            push(player, target);
        }

        showWedge(level, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 0.9f);
    }

    /** One caught body: shoved, drained, left weak, and cut for one point of the shove. */
    private static void push(Player caster, LivingEntity target) {
        Vec3 away = target.position().subtract(caster.position());
        if (away.lengthSqr() < 0.01d) {
            away = caster.getLookAngle().reverse();
        }
        away = new Vec3(away.x, 0.0d, away.z).normalize().scale(PUSH_SPEED);
        target.push(away.x, PUSH_LIFT, away.z);
        target.hurtMarked = true;
        target.fallDistance = 0.0f;

        target.hurt(caster.damageSources().magic(), DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, false, false));
        drain(target);
    }

    /** Spills a ninja's chakra. Anything without a pool is left to the shove and the weakness. */
    private static void drain(LivingEntity target) {
        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(DRAIN, data.getChakra());
            if (lost > 0.0f) {
                data.useChakra(lost, CHAKRA_REGEN_DELAY);
            }
        });
    }

    /** The wedge itself: a sweep drawn down the cone, widest at the far end. */
    private static void showWedge(ServerLevel level, Player caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        for (int step = 1; step <= VISUAL_STEPS; step++) {
            Vec3 at = eye.add(look.scale(RANGE * step / (double) VISUAL_STEPS));
            double spread = 0.35d * step;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z,
                    1, spread, 0.25d, spread, 0.0d);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z,
                    2, spread * 0.6d, 0.15d, spread * 0.6d, 0.01d);
        }
    }
}
