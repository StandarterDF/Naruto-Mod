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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * A crescent of wind thrown off the edge of the blade.
 *
 * <p>Every sword in the mod is a short weapon, and the answer to a spear, a bow or simply a body
 * that will not close the gap is to cut the air itself. The caster draws the edge across in front of
 * them and the cut carries: everything standing in the shallow cone in front of the blade is opened,
 * and the wound keeps bleeding after the blade has moved on.</p>
 *
 * <p>A quarter of the cut is not the edge at all but the vacuum behind it, which no plate stops.
 * The technique needs the sword as much as it needs the arm; without one there is no edge to draw
 * the air from.</p>
 */
public class VacuumBladeAbility extends Ability implements Ability.Cooldown {

    /** A cut of this length is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** 1 3 2 3 1 2 - the kenjutsu guard seal with the cut added. */
    public static final long COMBO = 132312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "vacuum_blade");

    /** How far the crescent carries, and the full width of the cone it opens. */
    private static final double RANGE = 12.0d;
    private static final double CONE_DEGREES = 30.0d;

    /** Cosine of half the cone: a body is inside when the angle to it is within fifteen degrees. */
    private static final double CONE_DOT = Math.cos(Math.toRadians(CONE_DEGREES / 2.0d));

    /** What the cut costs, and how long the arm needs before it can be drawn again. */
    private static final float COST = 25.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 160;

    /** The cut itself: a quarter of it is vacuum, which passes through armour. */
    private static final float DAMAGE = 6.0f;
    private static final float MAGIC_SHARE = 0.25f;

    /** How long the wound keeps bleeding, on the same mark the bone blade leaves. */
    private static final int BLEED_TICKS = 60;

    /** How the crescent is drawn: three rings of sweep motes across the cone. */
    private static final int RINGS = 3;
    private static final int ARC_POINTS = 5;

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
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.vacuum_blade.sword",
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
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();

        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            if (!(entity instanceof LivingEntity target) || !canHit(player, target)) {
                continue;
            }
            Vec3 to = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d).subtract(eye);
            if (to.lengthSqr() > RANGE * RANGE || to.lengthSqr() < 1.0E-4d) {
                continue;
            }
            if (to.normalize().dot(look) < CONE_DOT) {
                continue;
            }
            cut(level, player, target);
        }

        drawCrescent(level, eye, look);
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** One body in the cone: the edge, the vacuum behind it, and the wound that stays open. */
    private static void cut(ServerLevel level, Player caster, LivingEntity target) {
        float physical = DAMAGE * (1.0f - MAGIC_SHARE);
        float magic = DAMAGE * MAGIC_SHARE;

        target.hurt(caster.damageSources().playerAttack(caster), physical);
        if (!target.isAlive()) {
            return;
        }
        // The vacuum is a separate, unblockable hit taken in the same instant. A body still inside
        // the invulnerability window of the edge would shrug it off, so the window is cleared rather
        // than letting the second half of the cut go missing.
        target.invulnerableTime = 0;
        target.hurt(caster.damageSources().magic(), magic);

        target.getPersistentData().putLong(CamelliaDanceAbility.BLEED_TAG,
                level.getGameTime() + BLEED_TICKS);
        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                6, 0.2d, 0.3d, 0.2d, 0.05d);
    }

    /** A fan of sweep motes across the cone, so the space the cut took is visible. */
    private static void drawCrescent(ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        if (flat.lengthSqr() < 1.0E-4d) {
            return;
        }
        flat = flat.normalize();
        for (int ring = 1; ring <= RINGS; ring++) {
            double distance = RANGE * ring / RINGS;
            for (int point = 0; point <= ARC_POINTS; point++) {
                double share = point / (double) ARC_POINTS;
                double angle = Math.toRadians((share - 0.5d) * CONE_DEGREES);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vec3 direction = new Vec3(flat.x * cos - flat.z * sin, 0.0d, flat.x * sin + flat.z * cos);
                Vec3 at = eye.add(direction.scale(distance)).add(0.0d, -0.3d, 0.0d);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z,
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
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
