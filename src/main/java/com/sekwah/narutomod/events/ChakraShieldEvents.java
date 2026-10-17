package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spends the caster's chakra shield as damage comes in.
 *
 * <p>Damage is taken out of the shield pool before it ever reaches the caster, point for point, and
 * the armour display is kept in step so the caster can watch it being eaten away. See
 * {@link ChakraShieldAbility}.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChakraShieldEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (event.getSource().is(NarutoDamageTypes.GATE_BACKLASH)) {
            // The gate backlash is the caster's own body tearing itself apart. There is no shielding
            // it, or eight gates would be no more dangerous than any other buff.
            return;
        }

        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            float pool = ninjaData.getShieldPoints();
            if (pool <= 0.0f) {
                return;
            }

            float absorbed = Math.min(pool, event.getAmount());
            ninjaData.setShieldPoints(pool - absorbed);
            event.setAmount(event.getAmount() - absorbed);

            if (ninjaData.getShieldPoints() <= 0.0f) {
                ChakraShieldAbility.removeShield(player);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.CRIT, player.getX(),
                            player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                            30, 0.5d, 0.7d, 0.5d, 0.1d);
                    level.playSound(null, player, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
                }
            } else {
                // Keep the armour display in step with what is left.
                ChakraShieldAbility.applyShield(player, ninjaData.getShieldPoints());
            }
        });
    }
}
