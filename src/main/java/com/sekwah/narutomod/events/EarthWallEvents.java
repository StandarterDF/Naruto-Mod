package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.world.EarthWallSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the rise and the unmaking of earth walls.
 *
 * <p>The walls themselves are ordinary blocks; all this does is ask the saved data every tick
 * whether a scheduled block should come up or sink back. The saved data is usually empty, so the
 * per-tick cost is one hash lookup until a wall exists - worth it for an animation that has to look
 * smooth rather than step once a second.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EarthWallEvents {

    private EarthWallEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        EarthWallSavedData.get(level).tick(level);
    }
}
