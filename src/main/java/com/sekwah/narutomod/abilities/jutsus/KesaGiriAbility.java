package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The kesa cut: one diagonal stroke from the shoulder down across the body.
 *
 * <p>It is the first cut every swordsman is taught and the last one they stop using, because it
 * costs nothing but the step into range and it opens a body from the collar to the hip. The stroke
 * is short - a pace of ground - but it is wide, a full hundred and twenty degrees, so anything that
 * has closed to sword distance is inside it.</p>
 *
 * <p>Only three bodies are opened by one pass. A blade that cut everyone standing in the arc would
 * make the sweep pointless; the stroke is a line through a crowd, and a crowd is what the fight is
 * for.</p>
 */
public class KesaGiriAbility extends Ability implements Ability.Cooldown {

    /** A cut across a body is heard at the edge of a courtyard. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** 1 3 2 2 3 1 - the kenjutsu seals and the two keys of the kesa cut. */
    public static final long COMBO = 213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kesa_giri");

    /** How far the stroke reaches, and how wide the arc in front of the caster is. */
    private static final double RANGE = 3.0d;
    private static final double ARC_DEGREES = 120.0d;

    /** Cosine of half the arc: a body is inside when the angle to it is within sixty degrees. */
    private static final double ARC_DOT = Math.cos(Math.toRadians(ARC_DEGREES / 2.0d));

    /** What the stroke costs, and how long before the arm can draw it again. */
    private static final float COST = 15.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 80;

    /** The cut itself, and how much of it is the chakra edge rather than the steel. */
    private static final float DAMAGE = 6.0f;
    private static final float MAGIC_SHARE = 0.25f;

    /** A single stroke opens at most this many bodies. */
    private static final int MAX_TARGETS = 3;

    /** How the stroke is drawn: three motes across the arc. */
    private static final int ARC_POINTS = 3;

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
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();

        List<LivingEntity> inArc = new ArrayList<>(MAX_TARGETS);
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            if (!(entity instanceof LivingEntity target) || !canHit(player, target)) {
                continue;
            }
            Vec3 to = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d).subtract(eye);
            double lengthSqr = to.lengthSqr();
            if (lengthSqr > RANGE * RANGE || lengthSqr < 1.0E-4d) {
                continue;
            }
            if (to.normalize().dot(look) < ARC_DOT) {
                continue;
            }
            inArc.add(target);
        }
        // The nearest bodies are the ones the stroke actually meets first.
        inArc.sort(Comparator.comparingDouble(target -> target.distanceToSqr(player)));
        for (int i = 0; i < Math.min(MAX_TARGETS, inArc.size()); i++) {
            cut(level, player, inArc.get(i));
        }

        drawArc(level, eye, look);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    /** One body in the arc: the steel first, then the chakra edge that no plate stops. */
    private static void cut(ServerLevel level, Player caster, LivingEntity target) {
        float physical = DAMAGE * (1.0f - MAGIC_SHARE);
        float magic = DAMAGE * MAGIC_SHARE;

        target.hurt(caster.damageSources().playerAttack(caster), physical);
        if (!target.isAlive()) {
            return;
        }
        // The chakra edge lands in the same instant as the steel, so the invulnerability window the
        // first half opened is cleared rather than letting the second half go missing.
        target.invulnerableTime = 0;
        target.hurt(caster.damageSources().magic(), magic);

        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                6, 0.2d, 0.3d, 0.2d, 0.05d);
    }

    /** A shallow fan of sweep motes, so the arc the stroke took is visible. */
    private static void drawArc(ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        if (flat.lengthSqr() < 1.0E-4d) {
            return;
        }
        flat = flat.normalize();
        for (int point = 0; point <= ARC_POINTS; point++) {
            double share = point / (double) ARC_POINTS;
            double angle = Math.toRadians((share - 0.5d) * ARC_DEGREES);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 direction = new Vec3(flat.x * cos - flat.z * sin, 0.0d, flat.x * sin + flat.z * cos);
            Vec3 at = eye.add(direction.scale(RANGE * 0.7d)).add(0.0d, -0.4d, 0.0d);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
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
