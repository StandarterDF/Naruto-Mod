package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
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

import java.util.Comparator;

/**
 * Ten Finger Drilling Bullets: a stream of bone shards flicked out of the fingertips.
 *
 * <p>The Kaguya do not throw things, they grow them. A knuckle of bone pushed out of a finger and
 * sent on its way is quick, hard to see and cheap - which is what makes it a suppression technique
 * rather than a duel one. The first shards leave slowly, about one a second; the longer the seal is
 * held the faster the hand learns the rhythm, up to a burst of several a second. Every shard is paid
 * for on its own, so the stream is limited by the caster's pool rather than by a cooldown: a novice
 * gets a short burst, a master with a deep reserve can keep the line open.</p>
 *
 * <p>The shards are not entities: they travel less than a bowshot and would be dodged if they could
 * be seen coming, so each shot is resolved the moment it is fired. The trail is drawn afterwards,
 * from the same hand, so what the target sees is where the bone went.</p>
 */
public class BoneBulletsAbility extends Ability implements Ability.Channeled {

    /** A stream of bone leaving a hand is about as loud as a bowstring. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B C B B A V - the Kaguya seal with the thrown key. */
    public static final long COMBO = 133L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "bone_bullets");

    /** How far each shard carries, and how wide its wedge opens. */
    private static final double RANGE = 24.0d;
    private static final double CONE_DEGREES = 9.0d;

    /** One shard does not kill, but the stream adds up. */
    private static final float DAMAGE = 3.0f;

    /** Chakra paid for every shard, before mastery. */
    private static final float SHOT_COST = 7.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Ticks between the first shots, and the fastest the hand ever gets. */
    private static final int START_INTERVAL = 20;
    private static final int MIN_INTERVAL = 4;

    /** How many ticks it takes to reach the fastest rhythm. */
    private static final int ACCELERATION_TICKS = 120;

    /** Where the next shot of the current stream is due. */
    private static final String NEXT_SHOT_TAG = "narutomod:bone_bullets_next";

    /** How many motes are drawn along a shard's flight, and how many burst where it lands. */
    private static final int LINE_POINTS = 10;
    private static final int IMPACT_PARTICLES = 6;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // Each shard pays for itself in handleChannelling; this only ends the stream when the pool
        // can no longer afford the next one. A tap is allowed and fires a single shard.
        float cost = this.masteryCost(ninjaData, SHOT_COST);
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        if (chargeAmount < 0) {
            // A tap is a single shard: the minimum activation path never reaches handleChannelling.
            ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        }
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ticksChanneled == 0) {
            player.getPersistentData().putInt(NEXT_SHOT_TAG, 0);
            this.recordMastery(player, ninjaData);
        }
        int next = player.getPersistentData().getInt(NEXT_SHOT_TAG);
        if (ticksChanneled < next) {
            return;
        }
        float cost = this.masteryCost(ninjaData, SHOT_COST);
        if (ninjaData.getChakra() < cost) {
            return;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        fireShard(player, level);
        player.getPersistentData().putInt(NEXT_SHOT_TAG, ticksChanneled + intervalAt(ticksChanneled));
    }

    /** The rhythm: one shard a second at first, down to one every four ticks once the hand is warm. */
    private static int intervalAt(int ticksChanneled) {
        float progress = Math.min(1.0f, ticksChanneled / (float) ACCELERATION_TICKS);
        return Math.round(START_INTERVAL - (START_INTERVAL - MIN_INTERVAL) * progress);
    }

    /** Fires one shard at the nearest body in the narrow wedge, or draws it into the air. */
    private static void fireShard(Player player, ServerLevel level) {
        LivingEntity target = struck(player, level);
        Vec3 hand = JutsuEffects.handPosition(player);
        Vec3 look = player.getLookAngle();
        Vec3 end = target != null ? centreOf(target) : hand.add(look.scale(RANGE));
        drawShard(level, hand, end);

        if (target != null) {
            // Magic: a shard that grew inside the body is not armour's business, and it breaks no block.
            target.hurt(player.damageSources().magic(), DAMAGE);
            Vec3 impact = centreOf(target);
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z,
                    IMPACT_PARTICLES, 0.15d, 0.15d, 0.15d, 0.02d);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** The closest body inside the wedge, in sight, and not the caster's own side. */
    private static LivingEntity struck(Player caster, ServerLevel level) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f).normalize();
        double minDot = Math.cos(Math.toRadians(CONE_DEGREES));

        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RANGE),
                        target -> canStrike(caster, target)
                                && inWedge(eye, look, target, minDot))
                .stream()
                .min(Comparator.comparingDouble(caster::distanceToSqr))
                .orElse(null);
    }

    /** @return whether the target's body sits inside the wedge in front of the caster. */
    private static boolean inWedge(Vec3 eye, Vec3 look, LivingEntity target, double minDot) {
        Vec3 to = centreOf(target).subtract(eye);
        double length = to.length();
        if (length < 0.001d) {
            return true;
        }
        return to.scale(1.0d / length).dot(look) >= minDot;
    }

    /** Anything alive but the caster, their allies, and the untouchable, and only through open air. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        if (target.isAlliedTo(caster) || Friends.areFriends(caster, target)) {
            return false;
        }
        return caster.hasLineOfSight(target);
    }

    /** Draws one shard's flight from the hand to wherever it ended. */
    private static void drawShard(ServerLevel level, Vec3 hand, Vec3 end) {
        Vec3 span = end.subtract(hand);
        for (int i = 1; i <= LINE_POINTS; i++) {
            Vec3 at = hand.add(span.scale(i / (double) (LINE_POINTS + 1)));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    /** A tap fires one shard; a hold is the stream. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive < 0 && player.level() instanceof ServerLevel level) {
            fireShard(player, level);
        }
    }

    private static Vec3 centreOf(LivingEntity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }
}
