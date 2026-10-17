package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.utility.ChakraFistAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two halves of the chakra fist stance that an ability cannot reach: mining speed and melee
 * damage. See {@link ChakraFistAbility} for what it costs.
 *
 * <p>Speed is adjusted on both sides on purpose. The client works out its own break speed to run the
 * digging animation, so changing it only on the server would make every block look like it is being
 * pulled apart in slow motion. The toggle state is synced, so the client knows whether the stance is
 * up, and nothing is charged here.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChakraFistEvents {

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (ChakraFistAbility.isActive(ninjaData)) {
                // getNewSpeed() is the speed vanilla has already worked out, which includes the
                // tool's own multiplier (a netherite pickaxe is far past a bare hand before the
                // stance touches it). Multiplying it scales the tool-boosted number, it does not
                // just add a flat "twice as fast".
                event.setNewSpeed(event.getNewSpeed() * ChakraFistAbility.BREAK_SPEED_MULTIPLIER);
            }
        });
    }

    /**
     * Charged per block that is broken, which is what the stance is really paying for. The player's
     * own break event only fires on the server.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ChakraFistAbility.isActive(ninjaData)) {
                return;
            }
            if (ninjaData.getChakra() >= ChakraFistAbility.CHAKRA_PER_BLOCK) {
                ninjaData.useChakra(ChakraFistAbility.CHAKRA_PER_BLOCK,
                        ChakraFistAbility.REGEN_DELAY_PER_BLOCK);
            } else {
                // Punch itself out rather than run into negative chakra; the stance ends on the next
                // tick when it finds it cannot pay the trickle any more.
                ninjaData.setChakra(0f);
            }
        });
    }

    /**
     * Melee hits land harder, for a few points of chakra each. Only hits with the caster's own body
     * count, an arrow or a thrown trident is a different thing entirely.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Player player)) {
            return;
        }
        if (event.getSource().getEntity() != player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ChakraFistAbility.isActive(ninjaData)
                    || ninjaData.getChakra() < ChakraFistAbility.CHAKRA_PER_HIT) {
                return;
            }
            ninjaData.useChakra(ChakraFistAbility.CHAKRA_PER_HIT, ChakraFistAbility.REGEN_DELAY_PER_HIT);
            event.setAmount(event.getAmount() + ChakraFistAbility.BONUS_MELEE_DAMAGE);
        });
    }
}
