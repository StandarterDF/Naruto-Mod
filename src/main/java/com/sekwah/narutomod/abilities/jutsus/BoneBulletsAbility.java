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
import java.util.List;

/**
 * Ten Finger Drilling Bullets: five shards of bone flicked out of the fingertips in a fan.
 *
 * <p>The Kaguya do not throw things, they grow them. A knuckle of bone pushed out of a finger and
 * sent on its way is quick, hard to see and fast enough that the fan cannot be stepped out of at
 * close range - which is what makes it the answer to being surrounded rather than a duel technique.
 * Five shards go out at once, and each finds the nearest body in the narrow wedge in front of the
 * caster; anything further round is left alone.</p>
 *
 * <p>The shards are not entities: they travel less than a bowshot and would be dodged if they could
 * be seen coming, so the wedge is resolved the moment the seal is made. The trail is drawn
 * afterwards, from the same hand, so what the target sees is where the bone went.</p>
 */
public class BoneBulletsAbility extends Ability implements Ability.Cooldown {

    /** Five pieces of bone leaving a hand are about as loud as a bowstring. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B C B B A V - the Kaguya seal with the thrown key. */
    public static final long COMBO = 132214L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "bone_bullets");

    /** How far the fan carries, and how wide it opens. */
    private static final double RANGE = 24.0d;
    private static final double CONE_DEGREES = 15.0d;

    /** One shard does not kill, but nothing in the wedge is spared one. */
    private static final float DAMAGE = 5.0f;

    /** How many shards leave the hand, and how far apart they fan out. */
    private static final int BULLETS = 5;
    private static final double BULLET_SPREAD_DEGREES = 2.5d;

    private static final float COST = 15.0f;
    private static final int COOLDOWN = 160;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How many motes are drawn along a shard's flight, and how many burst where it lands. */
    private static final int LINE_POINTS = 10;
    private static final int IMPACT_PARTICLES = 6;

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
        List<LivingEntity> struck = struck(player, level);
        Vec3 hand = JutsuEffects.handPosition(player);
        Vec3 look = player.getLookAngle();

        for (int i = 0; i < BULLETS; i++) {
            Vec3 end = i < struck.size()
                    ? centreOf(struck.get(i))
                    : hand.add(look.yRot((float) Math.toRadians(spread(i))).scale(RANGE));
            drawShard(level, hand, end);
        }
        for (LivingEntity target : struck) {
            // Magic: a shard that grew inside the body is not armour's business, and it breaks no block.
            target.hurt(player.damageSources().magic(), DAMAGE);
            Vec3 impact = centreOf(target);
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z,
                    IMPACT_PARTICLES, 0.15d, 0.15d, 0.15d, 0.02d);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9f, 1.5f);
    }

    /**
     * The bodies the fan can reach: inside the wedge, in sight, and not the caster's own side. Only
     * the closest {@link #BULLETS} are taken - there are five shards, so five bodies at most.
     */
    private static List<LivingEntity> struck(Player caster, ServerLevel level) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f).normalize();
        double minDot = Math.cos(Math.toRadians(CONE_DEGREES));

        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RANGE),
                        target -> canStrike(caster, target)
                                && inWedge(eye, look, target, minDot))
                .stream()
                .sorted(Comparator.comparingDouble(caster::distanceToSqr))
                .limit(BULLETS)
                .toList();
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

    /** Where the {@code index}-th shard of an empty fan would point, in degrees off the look line. */
    private static double spread(int index) {
        return (index - (BULLETS - 1) / 2.0d) * BULLET_SPREAD_DEGREES;
    }

    /** Draws one shard's flight from the hand to wherever it ended. */
    private static void drawShard(ServerLevel level, Vec3 hand, Vec3 end) {
        Vec3 span = end.subtract(hand);
        for (int i = 1; i <= LINE_POINTS; i++) {
            Vec3 at = hand.add(span.scale(i / (double) (LINE_POINTS + 1)));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    private static Vec3 centreOf(LivingEntity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }
}
