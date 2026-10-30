package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraTransferAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChakraSealEvents {
    private ChakraSealEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        String partnerId = player.getPersistentData().getString("narutomod:chakra_seal_partner");
        if (partnerId.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Player partner = level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(partnerId));
        if (partner == null) {
            player.getPersistentData().remove("narutomod:chakra_seal_partner");
            return;
        }
        INinjaData data = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        INinjaData partnerData = partner.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null || partnerData == null) {
            return;
        }
        float mastery = Mastery.level(data, com.sekwah.narutomod.abilities.NarutoAbilities.CHAKRA_TRANSFER.get());
        double radius = 12.0d + 24.0d * mastery;
        if (player.distanceTo(partner) > radius) {
            // Out of range: break the seal.
            player.getPersistentData().remove("narutomod:chakra_seal_partner");
            partner.getPersistentData().remove("narutomod:chakra_seal_partner");
            return;
        }
        // Shared chakra: equalize between the two.
        if (player.tickCount % 5 == 0) {
            float total = data.getChakra() + partnerData.getChakra();
            float avg = total / 2.0f;
            data.setChakra(avg);
            partnerData.setChakra(avg);
        }
    }
}