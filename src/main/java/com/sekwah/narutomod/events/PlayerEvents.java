package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraMeditationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.abilities.jutsus.EightGatesAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerEvents {

    /** Stamina a cushioned landing costs before the height is even counted. */
    private static final float SOFT_LANDING_MIN_STAMINA = 10.0f;

    /** Stamina per block fallen once a landing is being cushioned. */
    private static final float SOFT_LANDING_STAMINA_PER_BLOCK = 6.0f;

    private static final int SOFT_LANDING_STAMINA_REGEN_DELAY = 20 * 3;

    @SubscribeEvent
    public static void onEntityUpdate(LivingEvent.LivingTickEvent event) {
        // Do chakra updates and other stuff here unless handled in capabilities
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        // Two clones sat down to meditate keep their caster topped up and their jutsu coming back
        // twice as fast, see ChakraMeditationJutsuAbility.
        ChakraMeditationJutsuAbility.tickAura(player);
        // The open gates have to be fed and refreshed every tick they are held, see EightGatesAbility.
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                EightGatesAbility.maintain(player, ninjaData));
    }

    /**
     * Respawning builds a brand new player with no attribute modifiers on it, so anything written
     * onto the caster's attributes has to be put back by hand. The chakra shield is the only one at
     * the moment, and death takes it with it.
     *
     * <p>Runs last on purpose: the capability is copied onto the new player by another handler, and
     * reading the pool before that has happened would see an empty one.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (event.isWasDeath()) {
                ninjaData.setShieldPoints(0.0f);
                ninjaData.setSecondBreathArmed(false);
                // The gates are the one thing a caster cannot switch off, so death is what shuts them.
                EightGatesAbility.close(player, ninjaData);
                ChakraShieldAbility.removeShield(player);
            } else if (ninjaData.getShieldPoints() > 0.0f) {
                ChakraShieldAbility.applyShield(player, ninjaData.getShieldPoints());
            }
        });
    }

    /**
     * The open gates do not let the body knit itself back together.
     *
     * <p>A well fed caster regenerates up to two points of health a second from their own saturation,
     * which is several times what the gates take from them, so without this the drain would be
     * quietly undone and the last gates would be a free buff. While any gate is open nothing heals
     * the caster at all: not food, not a regeneration potion, not the chakra healing technique.</p>
     */
    @SubscribeEvent
    public static void livingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (ninjaData.getGateCount() > 0) {
                event.setAmount(0.0f);
            }
        });
    }

    // Handle if they have some agility perk or leaps.
    //
    @SubscribeEvent
    public static void livingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player){
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                if (!ninjaData.isNinjaModeEnabled()) {
                    return;
                }

                // Nothing to catch: a fall this short would not have hurt in the first place, and
                // burning stamina on every hop off a single block is just a tax. Vanilla only starts
                // charging for falls past three blocks.
                if (event.getDistance() <= 3.0f) {
                    return;
                }

                // Chakra no Ukemi: bracing with chakra takes the whole fall off. There is nothing to
                // cast, it just happens whenever the caster can afford it, and it is priced to hurt:
                // a fall worth cushioning eats most of a full stamina bar.
                float staminaCost = SOFT_LANDING_MIN_STAMINA
                        + event.getDistance() * SOFT_LANDING_STAMINA_PER_BLOCK;
                if (ninjaData.getStamina() >= staminaCost) {
                    ninjaData.useStamina(staminaCost, SOFT_LANDING_STAMINA_REGEN_DELAY);
                    event.setDistance(0.0f);
                    player.resetFallDistance();
                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(),
                                player.getY() + 0.1d, player.getZ(),
                                20, 0.35d, 0.05d, 0.35d, 0.02d);
                    }
                    return;
                }

                float distance = event.getDistance();
                if(distance < 9){
                    distance *= 0.3f;
                }
                if(distance > 3) {
                    distance -= 5f;
                    distance *= 0.6f;
                }
                event.setDistance(distance);
            });
        }
    }

}
