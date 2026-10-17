package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SecondBreathAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Undoes the killing blow for a caster who armed the second breath trick.
 *
 * <p>It runs at the lowest priority so that everything else has had its say on the damage first: if
 * some other effect has already saved the caster, or cancelled the hit, this has no business
 * spending their second life on it.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SecondBreathEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }

        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isSecondBreathArmed()) {
                return;
            }
            if (player.getHealth() - event.getAmount() > 0.0f) {
                // Not a killing blow, the trick stays armed for the one that is.
                return;
            }

            event.setCanceled(true);
            SecondBreathAbility.spendSecondLife(player, ninjaData);
            // Only ever raise health, never lower it: the caster keeps whatever they had left if
            // that happens to be more than the trick leaves them with.
            player.setHealth(Math.max(player.getHealth(), SecondBreathAbility.SECOND_LIFE_HEALTH));

            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(),
                        player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                        60, 0.6d, 0.8d, 0.6d, 0.3d);
                level.playSound(null, player, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        });
    }
}
