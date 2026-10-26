package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Step Behind: the step the eye takes when the target has already stopped looking.
 *
 * <p>The Sharingan reads a body so completely that the shortest way past it stops being a fight.
 * The caster leaves from where they stand and arrives at the target's back, a pace and a bit behind
 * its heels, standing exactly as high as they were before. Nothing is granted for the trip: no
 * shield, no blow, no moment of invulnerability - the technique only changes where the caster is,
 * and the exchange starts again from an angle the target cannot see.</p>
 *
 * <p>It only works at fighting distance. Too close and there is no step to take, too far and the eye
 * cannot read the body well enough; and there has to be room for a whole body at the far end, or the
 * step refuses rather than putting the caster inside a wall.</p>
 */
public class StepBehindAbility extends Ability implements Ability.Cooldown {

    /** A body arriving behind another one is heard, even if the eye that sent it was not. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** C V V B B C B C - the Sharingan combo (C V V B), then the step of the branch. */
    public static final long COMBO = 122313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "step_behind");

    /** The step is a fighting move: closer than this and there is nothing to step past. */
    private static final double MIN_DISTANCE = 3.0d;

    /** Farther than this and the eye cannot read the body well enough to land behind it. */
    private static final double MAX_DISTANCE = 8.0d;

    /**
     * The look ray is allowed a little more than the band, so a target standing on the far edge is
     * still found by the eye - and then judged by the band, which is what has the message for it.
     */
    private static final double LOOK_RANGE = MAX_DISTANCE + 0.5d;

    /** How far behind the target's heels the caster lands, in blocks. */
    private static final double BEHIND = 1.2d;

    /** What it costs and how long before the eye can step again. */
    private static final float COST = 45.0f;
    private static final int COOLDOWN = 240;

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
        LivingEntity target = lookedAt(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.step_behind.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        double distance = player.distanceTo(target);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            player.displayClientMessage(Component.translatable("jutsu.fail.step_behind.range",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (arrival(player, target) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.step_behind.space",
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
        LivingEntity target = lookedAt(player);
        if (target == null) {
            return;
        }
        // The target has had a moment to turn since the cost was paid, so the spot behind it is
        // worked out again here rather than being the one that was checked.
        Vec3 arrival = arrival(player, target);
        if (arrival == null) {
            return;
        }

        level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                24, 0.3d, 0.5d, 0.3d, 0.3d);
        player.teleportTo(arrival.x, arrival.y, arrival.z);
        level.sendParticles(ParticleTypes.PORTAL, arrival.x,
                arrival.y + player.getBbHeight() * 0.5d, arrival.z,
                24, 0.3d, 0.5d, 0.3d, 0.3d);
        level.playSound(null, arrival.x, arrival.y, arrival.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** @return the living body under the look ray, or null when the eye has nothing to read. */
    private LivingEntity lookedAt(Player player) {
        if (Targeting.lookedAtEntity(player, LOOK_RANGE) instanceof LivingEntity target
                && isValidTarget(player, target)) {
            return target;
        }
        return null;
    }

    /**
     * Where the step would put the caster, or null when there is no room for a body there.
     *
     * <p>The landing spot is a pace behind the target's heels on the ground the caster is already
     * standing on: the step does not climb or fall, so the height is the caster's, not the target's.
     * The move is then tested as one displaced hitbox, which is the same test the game uses before
     * it lets anything be placed.</p>
     */
    private static Vec3 arrival(Player player, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        double offsetX;
        double offsetZ;
        if (horizontal < 1.0e-4d) {
            // A body looking straight up or down has no horizontal facing to stand behind, so its
            // yaw is used instead of pretending the look vector has a floor direction.
            double yaw = Math.toRadians(target.getYRot());
            offsetX = Math.sin(yaw) * BEHIND;
            offsetZ = -Math.cos(yaw) * BEHIND;
        } else {
            offsetX = look.x / horizontal * BEHIND;
            offsetZ = look.z / horizontal * BEHIND;
        }

        Vec3 destination = new Vec3(target.getX() - offsetX, player.getY(), target.getZ() - offsetZ);
        Vec3 delta = destination.subtract(player.position());
        if (!player.level().noCollision(player, player.getBoundingBox().move(delta))) {
            return null;
        }
        return destination;
    }

    /** A step needs a living body, and never one the caster is allied with or friends with. */
    private boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
