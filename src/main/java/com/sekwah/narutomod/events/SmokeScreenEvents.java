package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.SmokeCloudEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps mobs from finding anything to chase while they are standing in smoke.
 *
 * <p>The cloud clears a mob's current target on its own tick, but a target is picked again almost
 * immediately by the mob's targeting goals, which run every tick. There is no point racing that from
 * the cloud; it is refused at the source instead - every attempt to take a target while the mob is
 * inside smoke is cancelled, so it cannot lock onto anything until it walks out. Letting a target go
 * (a null one) is always allowed, or a mob could never be freed.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SmokeScreenEvents {

    private SmokeScreenEvents() {
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (event.getNewTarget() == null) {
            return;
        }
        if (SmokeCloudEntity.isHiddenBySmoke(mob)) {
            event.setCanceled(true);
        }
    }
}
