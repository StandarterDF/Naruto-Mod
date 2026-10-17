package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Passes the experience a clone earns on to its caster.
 *
 * <p>A clone is not a player, so the orbs a mob drops when a clone kills it would just lie where the
 * mob died - and a worker is usually sent far enough from its caster that they would time out
 * before anyone walked over. Instead of dropping the orbs at the kill, the experience is handed
 * straight to the clone's caster the moment the mob dies.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShadowCloneEvents {

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity killer = event.getEntity().getKillCredit();
        if (!(killer instanceof ShadowCloneEntity clone)) {
            return;
        }
        Player owner = clone.getOwner();
        int experience = event.getDroppedExperience();
        if (owner == null || experience <= 0) {
            // No caster to hand it to: let the orbs fall as usual, so nothing is lost.
            return;
        }
        event.setDroppedExperience(0);
        owner.giveExperiencePoints(experience);
    }
}
