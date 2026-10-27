package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A line of chakra thrown out of the hand: latch it onto something and it reels the caster in, or
 * reels the thing it caught out towards the caster.
 *
 * <p>What it catches is whatever the caster is looking at, worked out the same way the rest of the
 * mod does it (see {@link Targeting}): a living thing first, otherwise the face of a block. The catch
 * is made once, at the start of the hold, and then held onto - looking away does not change what the
 * thread is tied to, which is the whole point of latching rather than dragging the aim around.</p>
 *
 * <p>Pulling the caster is a plain {@link net.minecraft.world.entity.Entity#setDeltaMovement(Vec3)}
 * per tick towards the anchor, at a speed that grows with the distance so a long throw does not crawl
 * and a short one does not slam. Pulling a target is the same the other way round, aimed at the
 * caster's chest. The thread lets go on its own when the caster arrives, when the caster runs into
 * something, when the caught creature is gone, or when the caster lets go; either way the fall it
 * caused is forgotten, so a hard landing after a pull is not a death sentence.</p>
 */
public class ChakraThreadAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** V V C */
    public static final long COMBO = 11111L;

    /** How far the thread can be thrown, in blocks. */
    private static final double RANGE = 32.0d;

    /** Chakra burnt for every tick the thread is held. */
    private static final float CHAKRA_PER_TICK = 0.25f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** Speed of a pull at its slowest and fastest, in blocks per tick. */
    private static final double MIN_PULL_SPEED = 0.35d;
    private static final double MAX_PULL_SPEED = 1.0d;

    /** Every block of distance adds this much speed, up to the ceiling. */
    private static final double PULL_SPEED_PER_BLOCK = 0.03d;

    /** How close to the anchor counts as having arrived. */
    private static final double ARRIVE_RANGE = 1.8d;

    /** The same two numbers for a creature being reeled in, which is a little slower than the caster. */
    private static final double MIN_ENTITY_PULL_SPEED = 0.3d;
    private static final double MAX_ENTITY_PULL_SPEED = 0.85d;
    private static final double ENTITY_PULL_SPEED_PER_BLOCK = 0.025d;

    /** A small lift so a dragged creature does not plough into every bump in the ground. */
    private static final double ENTITY_LIFT = 0.06d;

    /** How close a caught creature has to be before the thread is done with it. */
    private static final double ENTITY_ARRIVE_RANGE = 2.0d;

    /**
     * How far a caught creature may get from the caster before the thread loses it. Deliberately
     * wider than the throw so the caster can step back without the line snapping immediately.
     */
    private static final double LEASH_RANGE = 48.0d;

    /** Most motes drawn along the thread at once. */
    private static final int MAX_THREAD_POINTS = 10;

    /** The catch of one hold: either a creature or a point on a block, never both. */
    private static final class Pull {
        @Nullable
        private final LivingEntity entity;
        @Nullable
        private final Vec3 anchor;

        private Pull(@Nullable LivingEntity entity, @Nullable Vec3 anchor) {
            this.entity = entity;
            this.anchor = anchor;
        }
    }

    /**
     * The catch of every caster currently holding the thread. The ability is a registry singleton,
     * so this is the one place the state of a hold lives; it is removed when the hold ends.
     */
    private final Map<UUID, Pull> pulls = new HashMap<>();

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** The thread has to be held: a tap never has the time to catch anything. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The price is agreed before anything is caught, so a caster who cannot pay does not leave a
        // stale catch behind for the next attempt to pick up.
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeAmount == 0) {
            Pull pull = this.resolve(player);
            if (pull == null) {
                player.displayClientMessage(Component.translatable("jutsu.fail.thread.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            this.pulls.put(player.getUUID(), pull);
        } else if (!this.pulls.containsKey(player.getUUID())) {
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        Pull pull = this.pulls.get(player.getUUID());
        if (pull == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A pull is deliberate vertical travel; the ground it ends over is not a fall.
        player.fallDistance = 0.0f;

        if (pull.entity != null) {
            if (!stillLatched(player, pull.entity)) {
                this.finish(player, ninjaData, false);
                return;
            }
            if (this.reelIn(player, pull.entity)) {
                this.finish(player, ninjaData, true);
                return;
            }
            drawThread(level, player, centreOf(pull.entity));
        } else {
            Vec3 centre = centreOf(player);
            double distance = centre.distanceTo(pull.anchor);
            // Catching on a wall the caster runs into, or arriving, ends the pull. The collision
            // flag is only trusted once the pull has had a tick to move the caster, so a wall they
            // happened to be leaning on when the thread was thrown does not snap it at once.
            if (distance <= ARRIVE_RANGE || (ticksChanneled > 0 && player.horizontalCollision)) {
                this.finish(player, ninjaData, true);
                return;
            }
            double speed = Math.min(MAX_PULL_SPEED, MIN_PULL_SPEED + distance * PULL_SPEED_PER_BLOCK);
            player.setDeltaMovement(pull.anchor.subtract(centre).normalize().scale(speed));
            player.hurtMarked = true;
            drawThread(level, player, pull.anchor);
        }
    }

    /**
     * Reels a caught creature towards the caster's chest. Returns whether it has arrived, at which
     * point the hold is over and it is left standing in front of the caster.
     */
    private boolean reelIn(Player caster, LivingEntity target) {
        Vec3 chest = caster.position().add(0.0d, 1.0d, 0.0d);
        Vec3 to = chest.subtract(centreOf(target));
        double distance = to.length();
        if (distance <= ENTITY_ARRIVE_RANGE) {
            // Caught: stop the reel rather than carry the creature past the caster.
            target.setDeltaMovement(target.getDeltaMovement().scale(0.2d));
            target.hurtMarked = true;
            return true;
        }
        target.fallDistance = 0.0f;
        double speed = Math.min(MAX_ENTITY_PULL_SPEED,
                MIN_ENTITY_PULL_SPEED + distance * ENTITY_PULL_SPEED_PER_BLOCK);
        target.setDeltaMovement(to.normalize().scale(speed).add(0.0d, ENTITY_LIFT, 0.0d));
        target.hurtMarked = true;
        return false;
    }

    /** Works out what the thread catches: a living thing in the crosshair, otherwise a block face. */
    @Nullable
    private Pull resolve(Player player) {
        Entity looked = Targeting.lookedAtEntity(player, RANGE);
        if (looked instanceof LivingEntity living && canCatch(player, living)) {
            return new Pull(living, null);
        }
        BlockHitResult block = Targeting.lookedAtBlock(player, RANGE);
        if (block != null) {
            return new Pull(null, block.getLocation());
        }
        return null;
    }

    /** Anything alive is fair game except the caster, their allies and untouchable players. */
    private static boolean canCatch(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || target.isAlliedTo(caster)
                || Friends.areFriends(caster, target)) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private static boolean stillLatched(Player caster, LivingEntity target) {
        return !target.isRemoved()
                && !target.isSpectator()
                && target.level() == caster.level()
                && target.distanceToSqr(caster) <= LEASH_RANGE * LEASH_RANGE;
    }

    /**
     * Ends a hold that ran its course on its own: the caster arrived, hit a wall, or lost the catch.
     * Chakra stops being paid at once rather than waiting for the keys to come up, and the hold is
     * credited as a use if it actually pulled something.
     */
    private void finish(Player player, INinjaData ninjaData, boolean mastered) {
        this.pulls.remove(player.getUUID());
        player.fallDistance = 0.0f;
        // Do not keep the momentum of the pull, or arriving would throw the caster into the wall.
        player.setDeltaMovement(player.getDeltaMovement().scale(0.3d));
        player.hurtMarked = true;
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
        if (mastered) {
            this.recordMastery(player, ninjaData);
        }
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    /**
     * Runs when the keys come up or the chakra runs out, whichever happens first. The caller is
     * responsible for the channel state and for mastery on a normal release; all that is left here
     * is to drop the catch and to forget the fall that the pull would otherwise have left behind.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.pulls.remove(player.getUUID());
        player.fallDistance = 0.0f;
        if (ticksActive > 0) {
            player.setDeltaMovement(player.getDeltaMovement().scale(0.3d));
            player.hurtMarked = true;
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, player, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.4f, 1.4f);
            }
        }
    }

    /** A line of sparks from the caster's hand to whatever the thread is tied to. */
    private static void drawThread(ServerLevel level, Player caster, Vec3 end) {
        Vec3 hand = JutsuEffects.handPosition(caster);
        Vec3 span = end.subtract(hand);
        int points = Math.max(2, Math.min(MAX_THREAD_POINTS, (int) (span.length() * 1.5d)));
        for (int i = 1; i <= points; i++) {
            Vec3 at = hand.add(span.scale(i / (double) (points + 1)));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    private static Vec3 centreOf(Entity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }
}
