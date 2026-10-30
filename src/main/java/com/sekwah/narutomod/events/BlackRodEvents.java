package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackRodEvents {
    private BlackRodEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        String untilKey = "narutomod:black_rod_drain_until";
        if (!data.contains(untilKey)) {
            return;
        }
        long until = data.getLong(untilKey);
        long now = entity.level().getGameTime();
        if (now >= until) {
            data.remove(untilKey);
            data.remove("narutomod:black_rod_drain_caster");
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        java.util.UUID casterId = data.getUUID("narutomod:black_rod_drain_caster");
        Player caster = level.getServer().getPlayerList().getPlayer(casterId);
        if (caster == null) {
            return;
        }
        // Drain a little chakra every 20 ticks.
        if (entity.tickCount % 20 != 0) {
            return;
        }
        entity.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(targetData -> {
            caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(casterData -> {
                float drain = 2.0f;
                float available = targetData.getChakra();
                if (available <= 0.0f) {
                    return;
                }
                float taken = Math.min(drain, available);
                targetData.useChakra(taken, 0);
                casterData.addChakra(taken);
            });
        });
    }
}