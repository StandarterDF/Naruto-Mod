package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.MomoshikiAbsorptionAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.jutsuprojectile.FireballJutsuEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.RasenshurikenJutsuEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.WaterBulletJutsuEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two halves of Momoshiki's absorption: catching a technique and handing it back.
 *
 * <p>Catching happens in two places. A thrown technique of the mod's that lands on a caster holding
 * the palm stance is swallowed on the projectile impact: it is cancelled, the projectile is discarded
 * and its nature is stored. A technique that lands as damage (fire breath, Amaterasu, ...) is swallowed
 * on the damage event instead, cancelling the hit. An explosion detonating near the caster is swallowed
 * whole. In every case the palm is fed chakra for what it ate.</p>
 *
 * <p>Handing back is done on the damage event instead of in any single technique, so every technique
 * of the same nature benefits without a line of change in its own class: the first one to land spends
 * everything held of that nature and hits half again as hard per charge.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AbsorptionEvents {

    /** Extra damage per charge held, as a fraction of the hit's own damage. */
    private static final float DAMAGE_PER_CHARGE = 0.5f;

    /** Chakra granted for swallowing a thrown projectile. */
    private static final float CHAKRA_PER_PROJECTILE = 10.0f;
    /** Chakra granted for swallowing an explosion. */
    private static final float CHAKRA_PER_EXPLOSION = 25.0f;
    /** Chakra granted for swallowing a technique hit. */
    private static final float CHAKRA_PER_TECHNIQUE = 15.0f;

    /**
     * A thrown technique reaches the caster and is swallowed instead of landing. Runs before the
     * projectile's own handler could deal any damage, which is the point of catching it here.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Projectile projectile = event.getProjectile();
        // Non-combat projectiles are never swallowed, even if something mapped them.
        if (projectile instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl
                || projectile instanceof net.minecraft.world.entity.projectile.FishingHook
                || projectile.getClass().getName().equals("net.minecraft.world.entity.projectile.EyeOfEnder")) {
            return;
        }
        // Every thrown thing is chakra in flight, so a plain arrow is eaten too - it simply leaves no
        // nature charge behind, only chakra.
        AbsorptionData.Nature nature = natureOf(projectile);
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!MomoshikiAbsorptionAbility.isActive(ninjaData)) {
                return;
            }
            event.setCanceled(true);
            projectile.discard();
            absorb(player, ninjaData, nature, projectile);
        });
    }

    /**
     * Two jobs, in order:
     * <ol>
     *     <li>If the hurt player is holding the palm open and the incoming damage is one of the mod's
     *         techniques, swallow it: cancel the damage, store a charge of its nature and grant chakra.</li>
     *     <li>Otherwise, if the caster of the hit is a player who has charges of that nature stored,
     *         spend them all and scale the hit (the "handing back" half).</li>
     * </ol>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        // 1. Swallow an incoming technique when the victim holds the palm open.
        if (event.getEntity() instanceof Player victim && !victim.level().isClientSide) {
            if (!event.isCanceled() && event.getAmount() > 0.0f) {
                DamageSource source = event.getSource();
                if (isModTechnique(source)) {
                    AbsorptionData.Nature incomingNature = natureOf(source);
                    victim.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                        if (!MomoshikiAbsorptionAbility.isActive(ninjaData)) {
                            return;
                        }
                        event.setCanceled(true);
                        if (incomingNature != null) {
                            ninjaData.getAbsorptionData().add(incomingNature);
                        }
                        ninjaData.addChakra(CHAKRA_PER_TECHNIQUE);
                        absorbTechnique(victim);
                    });
                }
            }
        }
        if (event.isCanceled()) {
            return;
        }
        // 2. Hand borrowed chakra back when the caster's own technique of that nature lands.
        if (!(event.getSource().getEntity() instanceof Player caster) || caster.level().isClientSide) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }
        AbsorptionData.Nature nature = natureOf(event.getSource());
        if (nature == null) {
            return;
        }
        caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            int charges = ninjaData.getAbsorptionData().take(nature);
            if (charges <= 0) {
                return;
            }
            event.setAmount(event.getAmount() * (1.0f + DAMAGE_PER_CHARGE * charges));
            release(caster, charges);
        });
    }

    /** Absorbs any explosion that detonates near a caster holding the palm stance. */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = event.getExplosion().getPosition();
        for (Player player : level.players()) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                if (!MomoshikiAbsorptionAbility.isActive(ninjaData)) {
                    return;
                }
                if (player.distanceToSqr(center) > 10.0d * 10.0d) {
                    return;
                }
                // Swallow the blast: nothing around the caster is touched.
                event.getAffectedEntities().clear();
                event.getAffectedBlocks().clear();
                // The swallowed blast feeds the palm.
                ninjaData.addChakra(CHAKRA_PER_EXPLOSION);
                // Show the absorbed radius.
                for (int i = 0; i < 32; i++) {
                    double angle = (Math.PI * 2.0d / 32.0d) * i;
                    double x = center.x + Math.cos(angle) * 10.0d;
                    double z = center.z + Math.sin(angle) * 10.0d;
                    level.sendParticles(ParticleTypes.ENCHANT, x, center.y + 0.5d, z,
                            1, 0.1d, 0.1d, 0.1d, 0.0d);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.7f);
            });
        }
    }

    /** Stores the technique and shows the palm eating it. */
    private static void absorb(Player player, INinjaData ninjaData, AbsorptionData.Nature nature,
                               Projectile projectile) {
        // A full palm still eats the technique - it has simply run out of room to keep it. A plain
        // projectile has no nature to store and is eaten for its chakra alone.
        if (nature != null) {
            ninjaData.getAbsorptionData().add(nature);
        }
        ninjaData.addChakra(CHAKRA_PER_PROJECTILE);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.ENCHANT,
                projectile.getX(), projectile.getY(), projectile.getZ(),
                24, 0.3d, 0.3d, 0.3d, 0.35d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** Visual feedback for swallowing a technique hit directly. */
    private static void absorbTechnique(Player victim) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.ENCHANT,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                24, 0.3d, 0.3d, 0.3d, 0.35d);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** The flare of borrowed chakra leaving the hand again. */
    private static void release(Player caster, int charges) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                caster.getX(), caster.getY() + caster.getBbHeight() * 0.6d, caster.getZ(),
                6 + 4 * charges, 0.35d, 0.35d, 0.35d, 0.05d);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /** Which nature a thrown technique carries, or null when it is not one the palm can catch. */
    private static AbsorptionData.Nature natureOf(Entity projectile) {
        if (projectile instanceof FireballJutsuEntity) {
            return AbsorptionData.Nature.FIRE;
        }
        if (projectile instanceof WaterBulletJutsuEntity) {
            return AbsorptionData.Nature.WATER;
        }
        if (projectile instanceof RasenshurikenJutsuEntity) {
            return AbsorptionData.Nature.WIND;
        }
        return null;
    }

    /** Whether this damage source is one of the mod's techniques, i.e. swallowable by the palm. */
    private static boolean isModTechnique(DamageSource source) {
        return source.is(NarutoDamageTypes.FIREBALL)
                || source.is(NarutoDamageTypes.FIRE_BREATH)
                || source.is(NarutoDamageTypes.AMATERASU)
                || source.is(NarutoDamageTypes.WATER_BULLET)
                || source.is(NarutoDamageTypes.WIND_RELEASE)
                || source.is(NarutoDamageTypes.CHIBAKU_TENSEI)
                || source.is(NarutoDamageTypes.TAILED_BALL)
                || source.is(NarutoDamageTypes.SENJUTSU);
    }

    /** Which nature a landed technique carries, by the damage type it was dealt with. */
    private static AbsorptionData.Nature natureOf(DamageSource source) {
        if (source.is(NarutoDamageTypes.FIREBALL)
                || source.is(NarutoDamageTypes.FIRE_BREATH)
                || source.is(NarutoDamageTypes.AMATERASU)) {
            return AbsorptionData.Nature.FIRE;
        }
        if (source.is(NarutoDamageTypes.WATER_BULLET)) {
            return AbsorptionData.Nature.WATER;
        }
        if (source.is(NarutoDamageTypes.WIND_RELEASE)) {
            return AbsorptionData.Nature.WIND;
        }
        return null;
    }
}
