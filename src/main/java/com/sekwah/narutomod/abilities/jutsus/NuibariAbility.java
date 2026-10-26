package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * The sewing needle: a thin line of steel that goes through a rank and pulls it back on the thread.
 *
 * <p>Unlike the chakra thread, which ties itself to one thing and reels it in over a hold, Nuibari
 * is thrown and recovered in the same breath: everything standing in a cone in front of the caster
 * is caught at once - a needle that only caught one body would be no use on a formation - and every
 * one of them is dragged a couple of paces towards the caster. Where there is no body to catch, the
 * needle bites the wall instead and pulls the caster to it, stopping short of the stone so the
 * caster's face does not arrive before their feet.</p>
 *
 * <p>It deals no damage. The pull is the technique: bodies taken off their footing are bodies that
 * cannot dodge the next blow.</p>
 */
public class NuibariAbility extends Ability implements Ability.Cooldown {

    /** The throw itself is heard; the needle is quiet once it is in the air. */
    @Override
    public int noiseRadius() {
        return 10;
    }

    /** B C B A A C - the taijutsu branch's weapon seal. */
    public static final long COMBO = 132113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "nuibari");

    /** As far as the needle reaches, both for bodies and for walls. */
    private static final double RANGE = 10.0d;

    /** Stamina paid for the throw, and how long the arm needs before another. */
    private static final float COST = 18.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 200;

    /** A needle is a line, not a sweep: only what is genuinely in front is caught. */
    private static final double CONE_DOT = 0.5d;

    /** At most this many bodies are threaded at once, nearest first. */
    private static final int MAX_TARGETS = 3;

    /** How hard a caught body is dragged, and how fast the caster is pulled to a wall. */
    private static final double PULL_SPEED = 0.6d;
    private static final double WALL_PULL_SPEED = 0.85d;

    /** How far short of the block face the caster stops when the needle bites stone. */
    private static final double WALL_STANDOFF = 2.0d;

    /** Most motes drawn along the thread at once. */
    private static final int MAX_LINE_POINTS = 12;

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
        if (threadedTargets(player).isEmpty() && Targeting.lookedAtBlock(player, RANGE) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.nuibari.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The targets are looked for again: the world moved between the cost and the throw, and the
        // server acts on what is there now rather than on what it saw a moment ago.
        List<LivingEntity> targets = threadedTargets(player);
        if (!targets.isEmpty()) {
            for (LivingEntity target : targets) {
                reelTowardsCaster(player, target);
                drawThread(level, player, centreOf(target));
            }
            level.playSound(null, player, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9f, 1.2f);
            return;
        }

        BlockHitResult wall = Targeting.lookedAtBlock(player, RANGE);
        if (wall == null) {
            return;
        }
        // Two paces out from the face along its own normal, which is the direction the caster is
        // standing in: the needle stays in the stone behind them.
        Vec3 anchor = wall.getLocation().add(Vec3.atLowerCornerOf(wall.getDirection().getNormal())
                .scale(WALL_STANDOFF));
        Vec3 to = anchor.subtract(centreOf(player));
        if (to.lengthSqr() > 1.0E-4d) {
            player.setDeltaMovement(to.normalize().scale(WALL_PULL_SPEED));
            player.fallDistance = 0.0f;
            player.hurtMarked = true;
        }
        drawThread(level, player, anchor);
        level.playSound(null, player, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9f, 1.2f);
    }

    /** Drags one caught body a couple of paces towards the caster's chest. */
    private static void reelTowardsCaster(Player caster, LivingEntity target) {
        Vec3 to = caster.position().add(0.0d, 1.0d, 0.0d).subtract(centreOf(target));
        if (to.lengthSqr() < 1.0E-4d) {
            return;
        }
        target.setDeltaMovement(to.normalize().scale(PULL_SPEED));
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
    }

    /**
     * The bodies the needle goes through: everything living inside the range whose direction from the
     * caster's eyes falls within the cone, nearest first and capped at {@link #MAX_TARGETS}.
     */
    private static List<LivingEntity> threadedTargets(Player caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        return caster.level().getEntitiesOfClass(LivingEntity.class,
                        caster.getBoundingBox().inflate(RANGE), entity -> canThread(caster, entity))
                .stream()
                .filter(entity -> eye.distanceToSqr(centreOf(entity)) <= RANGE * RANGE)
                .filter(entity -> centreOf(entity).subtract(eye).normalize().dot(look) > CONE_DOT)
                .sorted(Comparator.comparingDouble(entity -> eye.distanceToSqr(centreOf(entity))))
                .limit(MAX_TARGETS)
                .toList();
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canThread(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** A line of motes from the caster's hand to whatever the needle is tied to. */
    private static void drawThread(ServerLevel level, Player caster, Vec3 end) {
        Vec3 hand = JutsuEffects.handPosition(caster);
        Vec3 span = end.subtract(hand);
        int points = Math.max(2, Math.min(MAX_LINE_POINTS, (int) (span.length() * 1.5d)));
        for (int i = 1; i <= points; i++) {
            Vec3 at = hand.add(span.scale(i / (double) points));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }

    private static Vec3 centreOf(LivingEntity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }
}
