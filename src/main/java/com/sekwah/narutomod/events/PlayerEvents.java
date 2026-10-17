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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    /**
     * Stamina paid for every point of health a landing would otherwise have taken off.
     */
    private static final float SOFT_LANDING_STAMINA_PER_DAMAGE = 2.0f;

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
    /**
     * Chakra no Ukemi: stamina spent so a landing is taken on the legs rather than the spine.
     *
     * <p>The price is the damage the ground was about to charge, doubled, so a short drop is a
     * couple of points and a lethal one is most of a full bar. There is nothing to cast, it simply
     * happens whenever it is affordable.</p>
     *
     * <p>It is all or nothing on purpose. Not being able to pay used to leave the caster with a
     * partially cushioned landing, which read as the stamina doing something random; now an
     * unaffordable fall is simply a fall, and the stamina is kept for the next one.</p>
     */
    @SubscribeEvent
    public static void livingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player){
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                if (!ninjaData.isNinjaModeEnabled()) {
                    return;
                }

                // The vanilla sum, minus the jump boost that lifted the caster in the first place:
                // a fall that would not have hurt has nothing to cushion.
                MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
                float jumpBoost = jump == null ? 0.0f : jump.getAmplifier() + 1;
                float damage = (float) Math.ceil(event.getDistance() - 3.0f - jumpBoost);
                if (damage <= 0.0f) {
                    return;
                }

                float staminaCost = damage * SOFT_LANDING_STAMINA_PER_DAMAGE;
                if (ninjaData.getStamina() < staminaCost) {
                    return;
                }

                ninjaData.useStamina(staminaCost, SOFT_LANDING_STAMINA_REGEN_DELAY);
                event.setDistance(0.0f);
                player.resetFallDistance();
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(),
                            player.getY() + 0.1d, player.getZ(),
                            20, 0.35d, 0.05d, 0.35d, 0.02d);
                }
            });
        }
    }

}
