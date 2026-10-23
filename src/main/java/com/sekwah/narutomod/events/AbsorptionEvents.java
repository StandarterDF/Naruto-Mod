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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two halves of Momoshiki's absorption: catching a technique and handing it back.
 *
 * <p>Catching is done on the projectile impact: a thrown technique of the mod's that lands on a caster
 * holding the palm stance never reaches its own impact handler - it is cancelled, the projectile is
 * discarded and its nature is stored. That also means whatever the technique would have done (the
 * fireball's spread, the water bullet's splash, the disc's carry) simply does not happen.</p>
 *
 * <p>Handing back is done on the damage event instead of in any single technique, so every technique
 * of the same nature benefits without a line of change in its own class: the first one to land spends
 * everything held of that nature and hits half again as hard per charge.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AbsorptionEvents {

    /** Extra damage per charge held, as a fraction of the hit's own damage. */
    private static final float DAMAGE_PER_CHARGE = 0.5f;

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
        AbsorptionData.Nature nature = natureOf(event.getProjectile());
        if (nature == null) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!MomoshikiAbsorptionAbility.isActive(ninjaData)) {
                return;
            }
            event.setCanceled(true);
            event.getProjectile().discard();
            absorb(player, ninjaData, nature, event.getProjectile());
        });
    }

    /**
     * The caster's own technique of a stored nature hands the borrowed chakra back: the charges are
     * spent and the hit grows with them. The hand that absorbed it is the hand that pays for it, so
     * only the caster's own damage is scaled - a reflected or shared hit is not.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player caster) || caster.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
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

    /** Stores the technique and shows the palm eating it. */
    private static void absorb(Player player, INinjaData ninjaData, AbsorptionData.Nature nature,
                               Projectile projectile) {
        // A full palm still eats the technique - it has simply run out of room to keep it.
        ninjaData.getAbsorptionData().add(nature);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.ENCHANT,
                projectile.getX(), projectile.getY(), projectile.getZ(),
                24, 0.3d, 0.3d, 0.3d, 0.35d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
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
