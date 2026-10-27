package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ShadowGrabAbility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives a shadow-held body its own mind back when the hold expires.
 *
 * <p>The caster's channel normally releases the mark, but a caster who logs out, dies or is
 * disconnected mid-hold leaves it behind. The mark carries its own deadline, so a single tick hook
 * is enough to clean up every victim without any list of holds to keep.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowGrabEvents {

    private ShadowGrabEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(ShadowGrabAbility.UNTIL_TAG)) {
            return;
        }
        if (data.getLong(ShadowGrabAbility.UNTIL_TAG) > mob.level().getGameTime()) {
            return;
        }
        data.remove(ShadowGrabAbility.UNTIL_TAG);
        data.remove(ShadowGrabAbility.SOURCE_TAG);
        mob.setNoAi(false);
    }
}
