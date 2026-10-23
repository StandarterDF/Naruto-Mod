package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The upkeep of the elemental states the breaths leave behind.
 *
 * <p>Wetness is mostly denial: while a creature is soaked it cannot catch fire, and standing in
 * water or rain keeps it soaked. A chill is the cold version of the same idea and needs a visible
 * reminder, so it slows the creature and drops frost particles; fire or lava warms it away.</p>
 *
 * <p>Both states live in persistent data (see {@link ElementalStates}) and run only on the server:
 * particles are sent from here, so the client does not have to know the rule, only the picture.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElementalStateEvents {

    /** How often the chilled slow is refreshed, in ticks. */
    private static final int CHILL_INTERVAL = 20;

    /** Length of the slow handed out by a chill; refreshed while the chill lasts. */
    private static final int CHILL_EFFECT_TICKS = 40;

    private ElementalStateEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!NarutoConfig.elementalStatesEnabled || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (ElementalStates.isWet(entity)) {
            if (entity.isInLava()) {
                ElementalStates.dry(entity);
            } else {
                // Rain or standing water keeps a creature soaked, so a wet fight stays wet.
                if (entity.isInWaterRainOrBubble()) {
                    ElementalStates.refreshWet(entity, 40);
                }
                // Wet things do not burn: any fire that tries to catch is put out at once.
                if (entity.getRemainingFireTicks() > 0) {
                    entity.setRemainingFireTicks(0);
                    if (entity.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.CLOUD,
                                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6d, entity.getZ(),
                                4, 0.25d, 0.25d, 0.25d, 0.01d);
                    }
                }
            }
        }
        if (ElementalStates.isChilled(entity)) {
            long now = entity.level().getGameTime();
            if (entity.isInLava() || entity.getRemainingFireTicks() > 0) {
                ElementalStates.warm(entity);
                return;
            }
            if (now % CHILL_INTERVAL == 0L) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHILL_EFFECT_TICKS,
                        0, false, false));
                if (entity.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.7d, entity.getZ(),
                            6, 0.3d, 0.3d, 0.3d, 0.0d);
                }
            }
        }
    }
}
