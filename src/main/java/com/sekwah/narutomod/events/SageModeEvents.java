package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SageModeAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The frog kata: a sage's fists do not end at the skin.
 *
 * <p>Nature energy reaches past the arm, so a landed blow carries a cone of it behind the target:
 * everything hostile standing in that cone takes a share of the hit as senjutsu, without any
 * knockback to throw it clear. There is no mark and no extra cast - the cone is read off the blow
 * itself, which is why this lives on the hurt event rather than in any technique.</p>
 *
 * <p>Only true melee triggers it: the striking body has to be the direct cause, and a hit that is
 * already senjutsu never triggers it again, which is what keeps the cone from chaining off itself.
 * Friends, the caster's own side and the primary victim are never in the cone.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class SageModeEvents {

    /** Half-angle of the kata cone: 45 degrees off the gaze, a 90 degree cone. */
    private static final double CONE_DOT = 0.7071d;

    /** How far the invisible strike reaches, wider as the mode is mastered. */
    private static final double RADIUS_BASE = 2.0d;
    private static final double RADIUS_WIDE = 2.5d;
    private static final double RADIUS_MASTER = 3.0d;

    /** Share of the landed blow the cone carries, larger once the mode is nearly mastered. */
    private static final float SHARE_BASE = 0.5f;
    private static final float SHARE_MASTER = 0.75f;

    private static final float RADIUS_UP_AT = 0.6f;
    private static final float MASTER_AT = 0.85f;

    private SageModeEvents() {
    }

    /** A sage's landed blow carries nature past the fist, into everything behind the target. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide
                || event.isCanceled()
                || event.getAmount() <= 0.0f
                || !(event.getSource().getEntity() instanceof Player attacker)
                || victim == attacker) {
            return;
        }
        // The kata rides on the fist, not on the technique: something thrown, breathed or already
        // senjutsu is not a punch, and the cone must never trigger off itself.
        if (event.getSource().getDirectEntity() != attacker
                || event.getSource().is(NarutoDamageTypes.SENJUTSU)) {
            return;
        }
        if (!NarutoConfig.sageKataEnabled) {
            return;
        }
        attacker.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isSageMode()) {
                return;
            }
            float mastery = Mastery.level(ninjaData, SageModeAbility.ID);
            double radius = mastery >= MASTER_AT ? RADIUS_MASTER
                    : mastery >= RADIUS_UP_AT ? RADIUS_WIDE : RADIUS_BASE;
            float share = mastery >= MASTER_AT ? SHARE_MASTER : SHARE_BASE;
            strikeCone(attacker, victim, event.getAmount() * share, radius);
        });
    }

    /**
     * Hurts everything hostile in the cone past the fist. The primary victim is excluded - it
     * already paid the full blow - and so is everything on the caster's own side.
     */
    private static void strikeCone(Player attacker, LivingEntity victim, float damage, double radius) {
        if (!(attacker.level() instanceof ServerLevel level) || damage <= 0.0f) {
            return;
        }
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getViewVector(1.0f);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                attacker.getBoundingBox().inflate(radius),
                candidate -> candidate != attacker
                        && candidate != victim
                        && candidate.isAlive()
                        && candidate.isPickable()
                        && !candidate.isSpectator()
                        && !(candidate instanceof Player other
                                && (other.isCreative() || other.isSpectator()))
                        && !Friends.isOwnSide(attacker, candidate)
                        && inCone(eye, look, candidate, radius));
        for (LivingEntity candidate : caught) {
            candidate.hurt(NarutoDamageTypes.getDamageSource(level,
                    NarutoDamageTypes.SENJUTSU, attacker, attacker), damage);
            level.sendParticles(ParticleTypes.CRIT,
                    candidate.getX(), candidate.getY() + candidate.getBbHeight() * 0.6d, candidate.getZ(),
                    4, 0.25d, 0.25d, 0.25d, 0.1d);
        }
    }

    /** @return whether the candidate's chest sits within the cone and within reach. */
    private static boolean inCone(Vec3 eye, Vec3 look, LivingEntity candidate, double radius) {
        Vec3 toTarget = new Vec3(
                candidate.getX() - eye.x,
                candidate.getY() + candidate.getBbHeight() * 0.5d - eye.y,
                candidate.getZ() - eye.z);
        double distance = toTarget.length();
        if (distance > radius || distance < 1.0E-4d) {
            return false;
        }
        return toTarget.normalize().dot(look) >= CONE_DOT;
    }
}
