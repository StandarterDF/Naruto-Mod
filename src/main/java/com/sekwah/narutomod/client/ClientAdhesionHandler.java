package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraAdhesionAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the chakra adhesion stance.
 *
 * <p>A player's own velocity is the client's to decide: the server may set it, but the next movement
 * packet the local game sends carries its own idea of where the player is going, and the nudge is
 * gone. So the climb is applied here as well, on the local player only, from the channel state that
 * the server syncs back. Remote players are moved by the server's own nudge and never touch this.</p>
 *
 * <p>It runs on {@link LivingEvent.LivingTickEvent} on purpose: that fires inside the player's tick
 * after vanilla has reset the transient flags but before the movement step, so the velocity actually
 * survives into the step. A plain player tick event is either too early or too late.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientAdhesionHandler {

    private ClientAdhesionHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player != Minecraft.getInstance().player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ChakraAdhesionAbility.ID)) {
                return;
            }
            if (!player.horizontalCollision) {
                return;
            }
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, ChakraAdhesionAbility.climbSpeed(ninjaData), motion.z);
            player.fallDistance = 0.0f;
        });
    }
}
