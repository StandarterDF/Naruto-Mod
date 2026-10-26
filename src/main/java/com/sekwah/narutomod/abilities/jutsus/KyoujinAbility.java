package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
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
 * Magen: Kyoujin - the illusion that is not aimed at one mind but laid over an area.
 *
 * <p>Where the other illusions on this branch take a single victim, this one is spread across
 * everything the caster can see in front of them: the ground seems to tilt, the north stops being
 * the north, and legs that were carrying a charge a heartbeat ago refuse to move properly. Nobody
 * inside the cone is hurt by it. That is the point - it is the technique a ninja uses to stop a
 * group from closing at all, buy the seconds needed to finish one of them, and then leave.</p>
 *
 * <p>It is short and it is cheap, because an illusion that stops a crowd is worth nothing once the
 * crowd has worked out which way is up; the five seconds it lasts are for doing something else, not
 * for standing and watching.</p>
 */
public class KyoujinAbility extends Ability implements Ability.Cooldown {

    /** A genjutsu is cast in the mind, not into the air: it makes no sound of its own. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B C C - the Sharingan combo (C V V B), then the area illusion of the branch. */
    public static final long COMBO = 122322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kyoujin");

    /** How far the illusion is laid in front of the caster. */
    private static final double RANGE = 6.0d;

    /** Half of the cone's opening angle: the whole cone is 90 degrees wide. */
    private static final double HALF_ANGLE = 45.0d;

    /** cos(half angle), which is what the cone test compares the look direction against. */
    private static final double CONE_COS = Math.cos(Math.toRadians(HALF_ANGLE));

    /** What it costs and how long before the eye can lay it again. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 280;

    /** How long the caught bodies are left slow and disoriented - five seconds. */
    private static final int DURATION = 100;

    /** How many rays of witch-light are drawn to show the cone's shape. */
    private static final int RAYS = 7;

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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // An area illusion is aimed at the bodies in front of the caster, so there has to be at
        // least one: paying for an empty cone is never what the player meant to do.
        if (!(player.level() instanceof ServerLevel level) || caught(level, player).isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kyoujin.notarget",
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
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The cone is recomputed rather than remembered: the bodies in it have moved since the cost
        // was paid, and the illusion should catch whoever is inside it now, not who was there then.
        List<LivingEntity> victims = caught(level, player);
        if (victims.isEmpty()) {
            return;
        }

        for (LivingEntity target : victims) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 1, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DURATION, 0, false, true));
            level.sendParticles(ParticleTypes.WITCH, target.getX(),
                    target.getY() + target.getBbHeight() * 0.9d, target.getZ(),
                    6, 0.25d, 0.2d, 0.25d, 0.0d);
        }
        showCone(level, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    /** Everything alive inside the cone, the caster and their allies already filtered out. */
    private static List<LivingEntity> caught(ServerLevel level, Player player) {
        List<LivingEntity> inside = BreathCone.entities(level, player, RANGE, CONE_COS);
        // getEntitiesOfClass does not care about game mode, and a spectator on the edge of the cone
        // has no body for an illusion to take hold of.
        inside.removeIf(target -> target.isSpectator()
                || (target instanceof Player other && other.isCreative()));
        return inside;
    }

    /**
     * Draws the cone as a fan of witch-light rays.
     *
     * <p>The rays are rotated copies of the look vector, so the picture the player sees is the same
     * shape the hit test uses - including pitch, which is why the vertical part of the look is kept
     * rather than flattened to the horizon.</p>
     */
    private static void showCone(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = Math.toRadians(-HALF_ANGLE + 2.0d * HALF_ANGLE * ray / (RAYS - 1));
            Vec3 direction = rotate(look, angle, horizontal);
            for (double travelled = 1.0d; travelled <= RANGE; travelled += 1.0d) {
                level.sendParticles(ParticleTypes.WITCH,
                        eye.x + direction.x * travelled,
                        eye.y + direction.y * travelled,
                        eye.z + direction.z * travelled,
                        1, 0.12d, 0.12d, 0.12d, 0.0d);
            }
        }
    }

    /** Turns the look vector around the vertical axis. A straight-up look has no axis to turn on. */
    private static Vec3 rotate(Vec3 look, double angle, double horizontal) {
        if (horizontal < 1.0e-4d) {
            return look;
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(look.x * cos - look.z * sin, look.y, look.x * sin + look.z * cos);
    }
}
