package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.block.SentrySealTracker;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps hostile spawns out of a warded perimeter.
 *
 * <p>This is the optional half of the sentry seal, off by default: on a server where the ward is
 * meant to be a real base defence rather than a doorbell, turning it on makes the seal suppress
 * monster spawns in its radius. Spawner-driven spawns are left alone - a dungeon should stay a
 * dungeon even if a ward is planted next to it.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SentrySealEvents {

    private SentrySealEvents() {
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!NarutoConfig.sentrySealSuppressesSpawns) {
            return;
        }
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (SentrySealTracker.anyWithin(level, event.getX(), event.getY(), event.getZ(),
                NarutoConfig.sentrySealRadius)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
