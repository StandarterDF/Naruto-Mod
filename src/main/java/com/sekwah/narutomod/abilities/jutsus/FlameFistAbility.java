package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Three fists of fire, thrown at arm's length.
 *
 * <p>Fire release at its cheapest: no ball to throw, no breath to gather, only the hand wrapped in
 * flame and driven into a body three times before the first blow has finished landing. Each blow is
 * small; the count is what makes it worth the chakra, and the last one leaves the caster's fire
 * burning on the target rather than only on the knuckles.</p>
 *
 * <p>The blows are spread over a handful of ticks so the three of them read as a flurry rather than
 * one heavy hit, and the invulnerability window is cleared before each so none of the three is
 * swallowed by the one before it. The caster has to be inside reach already: a technique that
 * appears on a target two paces away is a fireball, and this is not a fireball.</p>
 */
public class FlameFistAbility extends Ability implements Ability.Cooldown {

    /** Three blows in a second is not thunder; it is a scuffle. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 1 3 2 2 3 2 - the kenjutsu seals and the two keys of the flame fist. */
    public static final long COMBO = 311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "flame_fist");

    /** How close the target has to be: the length of an arm and a step. */
    private static final double RANGE = 2.5d;

    /** What the flurry costs, and how long before the hand can be wrapped again. */
    private static final float COST = 20.0f;
    private static final int CHAKRA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 180;

    /** The flurry: how many blows, how far apart, and what each one is worth. */
    private static final int HITS = 3;
    private static final int HIT_INTERVAL = 4;
    private static final float DAMAGE = 2.0f;

    /** How long the last blow leaves the target alight, in ticks. */
    private static final int BURN_TICKS = 60;

    private static final int FLAME_PARTICLES = 10;

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
        if (findTarget(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.flame_fist.notarget",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        for (int hit = 0; hit < HITS; hit++) {
            boolean last = hit == HITS - 1;
            // The blows are spread over the next dozen ticks rather than resolved in one instant:
            // the flurry is the point, and a body that survives two of three should get the chance
            // to step out of the third.
            server.tell(new TickTask(server.getTickCount() + hit * HIT_INTERVAL,
                    () -> strike(level, player, target, last)));
        }
        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z,
                FLAME_PARTICLES, 0.15d, 0.15d, 0.15d, 0.02d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** One of the three blows: fire on the knuckles, and on the last one, fire on the target. */
    private static void strike(ServerLevel level, Player caster, LivingEntity target, boolean last) {
        if (target.isRemoved() || !target.isAlive() || target.level() != level
                || caster.distanceToSqr(target) > RANGE * RANGE * 4.0d) {
            return;
        }
        // The blows are four ticks apart, well inside the ordinary invulnerability window.
        target.invulnerableTime = 0;
        float mastery = caster.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .map(nd -> Mastery.level(nd, FlameFistAbility.ID)).orElse(0.0f);
        target.hurt(caster.damageSources().playerAttack(caster),
                DAMAGE * (1.0f + 0.5f * mastery));

        Vec3 at = target.position().add(0.0d, target.getBbHeight() * 0.6d, 0.0d);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, FLAME_PARTICLES, 0.25d, 0.3d, 0.25d, 0.01d);
        if (last && target.isAlive()) {
            target.setSecondsOnFire(BURN_TICKS / 20);
        }
    }

    /**
     * What the flurry is aimed at: whoever is in the crosshair, or failing that the nearest body in
     * the short arc in front of the caster, because at this distance a fist does not need a line.
     */
    private static LivingEntity findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity looked && canHit(player, looked)) {
            return looked;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            if (!(entity instanceof LivingEntity candidate) || !canHit(player, candidate)) {
                continue;
            }
            Vec3 to = candidate.position().add(0.0d, candidate.getBbHeight() * 0.5d, 0.0d).subtract(eye);
            double lengthSqr = to.lengthSqr();
            if (lengthSqr > RANGE * RANGE || lengthSqr < 1.0E-4d || to.normalize().dot(look) < 0.5d) {
                continue;
            }
            if (lengthSqr < best) {
                best = lengthSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canHit(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
