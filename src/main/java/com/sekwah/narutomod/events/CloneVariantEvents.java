package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ClayCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.MudCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterCloneAbility;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The clone variants that are more than a body with a face.
 *
 * <p>A water clone is cheap for a reason: it only takes part of a blow, it cannot be left behind to
 * work, and it smothers fire by standing in it. A mud clone is the opposite trade - slow, brittle,
 * and it leaves the ground sticky when it breaks. A clay clone is a charge with a face on it, and
 * the only reason it does not crater the ground is that the whole point of the technique is a
 * weapon the caster can afford to lose indoors.</p>
 *
 * <p>Everything here is driven by tags written at summon time, so a clone that is unloaded, reloaded
 * or handed to another tick still behaves. The one thing that cannot be a {@code LivingDeathEvent}
 * is the pop itself: {@link ShadowCloneEntity#die} does not call {@code super.die()}, so the death
 * event never fires for a clone. The lethal blow is caught on {@link LivingDamageEvent} instead.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class CloneVariantEvents {

    private static final String WATER = "narutomod:water_clone";
    private static final String WATER_UNTIL = "narutomod:water_clone_until";
    private static final String WATER_OWNER = "narutomod:water_clone_owner";

    private static final String MUD = "narutomod:mud_clone";
    private static final String MUD_UNTIL = "narutomod:mud_until";

    private static final String CLAY = "narutomod:clay_clone";

    private CloneVariantEvents() {
    }

    /**
     * A water clone that has been left too far behind, or whose time is up, goes back to water. The
     * fire it is standing in goes out with it, and so does the fire on anything it is touching.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getSource().getDirectEntity() instanceof ShadowCloneEntity clone
                && clone.getPersistentData().getBoolean(WATER)) {
            event.setAmount(event.getAmount() * WaterCloneAbility.DAMAGE_MULTIPLIER);
        }
        if (event.getSource().getEntity() instanceof ShadowCloneEntity attacker
                && attacker.getPersistentData().getBoolean(WATER)) {
            event.setAmount(event.getAmount() * WaterCloneAbility.DAMAGE_MULTIPLIER);
        }
    }

    /** The pop of a variant clone: mud leaves the ground sticky, clay leaves a hole in the air. */
    @SubscribeEvent
    public static void onLethalDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ShadowCloneEntity clone) || clone.level().isClientSide) {
            return;
        }
        boolean lethal = clone.getHealth() - event.getAmount() <= 0.0f;
        if (!lethal) {
            return;
        }
        var data = clone.getPersistentData();
        if (data.getBoolean(MUD)) {
            mudSplash(clone);
        }
        if (data.getBoolean(CLAY)) {
            clayBurst(clone);
        }
    }

    /** Pins the water clone to its owner and lets it smother fire while it lasts. */
    @SubscribeEvent
    public static void onCloneTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ShadowCloneEntity clone) || clone.level().isClientSide) {
            return;
        }
        var data = clone.getPersistentData();
        long now = clone.level().getGameTime();

        if (data.getBoolean(WATER)) {
            if (now >= data.getLong(WATER_UNTIL)) {
                clone.discard();
                return;
            }
            if (clone.tickCount % 10 == 0) {
                extinguishAround(clone);
                if (!withinLeash(clone)) {
                    clone.discard();
                }
            }
        }

        if (data.getBoolean(MUD) && now >= data.getLong(MUD_UNTIL)) {
            clone.discard();
        }
    }

    /** Whether a water clone is still close enough to the body it was made from. */
    private static boolean withinLeash(ShadowCloneEntity clone) {
        if (!(clone.level() instanceof ServerLevel level)) {
            return true;
        }
        String raw = clone.getPersistentData().getString(WATER_OWNER);
        if (raw.isEmpty()) {
            return true;
        }
        try {
            Player owner = level.getPlayerByUUID(java.util.UUID.fromString(raw));
            return owner == null
                    || owner.distanceToSqr(clone) <= WaterCloneAbility.LEASH_RANGE * WaterCloneAbility.LEASH_RANGE;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    /** Water smothers fire: the clone itself, and anything standing close enough to touch it. */
    private static void extinguishAround(ShadowCloneEntity clone) {
        clone.setRemainingFireTicks(0);
        List<LivingEntity> nearby = clone.level().getEntitiesOfClass(LivingEntity.class,
                clone.getBoundingBox().inflate(2.0d), LivingEntity::isOnFire);
        for (LivingEntity body : nearby) {
            body.setRemainingFireTicks(0);
        }
    }

    /** Broken mud drags at the feet of everything standing in it, except the one who made it. */
    private static void mudSplash(ShadowCloneEntity clone) {
        List<LivingEntity> nearby = clone.level().getEntitiesOfClass(LivingEntity.class,
                clone.getBoundingBox().inflate(4.0d));
        for (LivingEntity body : nearby) {
            if (body == clone || clone.getOwnerUUID().map(id -> id.equals(body.getUUID())).orElse(false)) {
                continue;
            }
            body.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    MudCloneAbility.DEATH_SLOW_TICKS, MudCloneAbility.DEATH_SLOW_AMPLIFIER, false, true));
        }
        if (clone.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    clone.getX(), clone.getY() + 0.2d, clone.getZ(), 24, 1.2d, 0.2d, 1.2d, 0.02d);
        }
    }

    /** Clay goes off without breaking a block: the blast hurts bodies, not the world. */
    private static void clayBurst(ShadowCloneEntity clone) {
        Level level = clone.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Player owner = clone.getOwner();
        server.explode(owner, clone.getX(), clone.getY(), clone.getZ(),
                ClayCloneAbility.BLAST_RADIUS, Level.ExplosionInteraction.NONE);
    }
}
