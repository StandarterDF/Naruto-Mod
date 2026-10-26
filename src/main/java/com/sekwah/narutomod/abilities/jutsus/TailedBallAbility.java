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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The tailed beast bomb: the beast's chakra packed into a black sphere and thrown.
 *
 * <p>Nothing about it is shaped. There is no element, no seal and no technique on top of it - the
 * sphere is simply as much chakra as the caster can hold in one place, and it is thrown at whatever
 * they are looking at. Three seconds of gathering is the price of the sphere; it then crosses the
 * field faster than a tick and comes apart in a four block blast that leaves the ground exactly as it
 * found it. Only bodies are touched, which is the one discipline the technique has.</p>
 *
 * <p>It only exists on top of a cloak - either the settled cloak of an agreement or the crack of a
 * broken seal - because the caster needs the beast's chakra already in the body to have that much of
 * it to gather.</p>
 */
public class TailedBallAbility extends Ability implements Ability.Channeled {

    /** The blast is heard a long way off, which is fair warning. */
    @Override
    public int noiseRadius() {
        return 48;
    }

    /** C C B B C A - the seal, then the three keys that gather it. */
    public static final long COMBO = 332231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tailed_ball");

    /** Chakra paid to open the sphere, before mastery, and every tick it is gathered. */
    private static final float OPEN_COST = 80.0f;
    private static final float UPKEEP = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Three seconds of gathering before the sphere is worth throwing. */
    public static final int CHARGE_TICKS = 60;

    /** How far down the look the sphere can be thrown. A wall stops it, as a wall stops anything. */
    private static final double RANGE = 30.0d;

    /** What the blast reaches, and what it deals at the heart of it and at the rim. */
    private static final double BLAST_RADIUS = 4.0d;
    private static final float MIN_DAMAGE = 12.0f;
    private static final float MAX_DAMAGE = 30.0f;

    /** How much of the damage the rim of the blast loses, as a fraction of the whole. */
    private static final float FALLOFF = 0.6f;

    /** How hard the blast throws what it does not kill. */
    private static final double KNOCKBACK = 0.8d;

    /** How far out the gathering motes are drawn in from. */
    private static final double GATHER_RADIUS = 2.0d;

    /** How finely the path of the sphere is dotted, in blocks per mote. */
    private static final double PATH_STEP = 0.5d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A tap never gathers anything: the sphere has no instant form. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!PerfectCloakAbility.isActive(player) && !SealBreakAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.tailed_ball.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, chargeAmount == 0 ? OPEN_COST : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * The gathering. Motes are drawn in towards the hand from a ring that closes as the sphere fills,
     * and at the third second the sphere leaves and the channel ends where it stands - the caster does
     * not have to let go of the keys for the throw to happen.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z,
                5, 0.2d, 0.2d, 0.2d, 0.01d);
        drawGathering(level, player, ticksChanneled);

        if (ticksChanneled >= CHARGE_TICKS) {
            this.fire(player, level, ticksChanneled);
            ninjaData.setCurrentlyChanneledAbility(player, null);
        }
    }

    /**
     * Throws the sphere: the wall in front of the caster decides where it lands, and everything caught
     * in the blast that is not on the caster's side takes it. Nothing is broken, nothing is set alight.
     */
    private void fire(Player player, ServerLevel level, int ticksChanneled) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        BlockHitResult block = Targeting.lookedAtBlock(player, RANGE);
        Vec3 impact = block != null ? block.getLocation() : eye.add(look.scale(RANGE));

        drawPath(level, JutsuEffects.handPosition(player), impact);

        float charge = Mth.clamp(ticksChanneled / (float) CHARGE_TICKS, 0.0f, 1.0f);
        float power = Mth.lerp(charge, MIN_DAMAGE, MAX_DAMAGE);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(BLAST_RADIUS),
                target -> target.isAlive() && target != player && !target.isSpectator());
        for (LivingEntity target : caught) {
            if (Friends.isOwnSide(player, target)) {
                continue;
            }
            double distance = Math.sqrt(target.distanceToSqr(impact.x, impact.y, impact.z));
            // Full at the heart of the blast, twelve points at the rim: the sphere is a point of
            // chakra, so it is the centre that is meant to be stood in.
            float falloff = 1.0f - FALLOFF * (float) Math.min(1.0d, distance / BLAST_RADIUS);
            target.hurt(player.damageSources().magic(), power * falloff);
            target.knockback(KNOCKBACK, target.getX() - impact.x, target.getZ() - impact.z);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.SQUID_INK, impact.x, impact.y, impact.z,
                80, BLAST_RADIUS * 0.4d, BLAST_RADIUS * 0.4d, BLAST_RADIUS * 0.4d, 0.06d);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, impact.x, impact.y, impact.z,
                50, BLAST_RADIUS * 0.4d, BLAST_RADIUS * 0.4d, BLAST_RADIUS * 0.4d, 0.04d);
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.7f);
    }

    /** Motes drawn in from a ring that closes as the sphere fills. */
    private static void drawGathering(ServerLevel level, Player player, int ticksChanneled) {
        float filled = Mth.clamp(ticksChanneled / (float) CHARGE_TICKS, 0.0f, 1.0f);
        double radius = GATHER_RADIUS * (1.0d - filled * 0.8d);
        double y = player.getY() + player.getBbHeight() * 0.6d;
        int points = 6;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0d * i / points + ticksChanneled * 0.35d;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX() + Math.cos(angle) * radius,
                    y + Math.sin(ticksChanneled * 0.2d) * 0.3d,
                    player.getZ() + Math.sin(angle) * radius,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }

    /**
     * The path of the sphere, dotted from the hand to where it came apart. It crosses the field in
     * less than a tick, so there is no flight to animate - the line is the whole of its travel.
     */
    private static void drawPath(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 span = to.subtract(from);
        int points = Math.max(1, (int) (span.length() / PATH_STEP));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(span.scale(i / (double) points));
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 1, 0.05d, 0.05d, 0.05d, 0.0d);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.05d, 0.05d, 0.05d, 0.0d);
        }
    }

    /**
     * A sphere is not a cast, it is a hold: the gathering is credited per tick above and the release
     * is not a second cast.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** A short hold that never gathered anything leaves nothing behind but the chakra it burned. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive >= CHARGE_TICKS || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
    }
}
