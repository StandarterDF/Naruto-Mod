package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps an exploding clone's blast away from its own side.
 *
 * <p>A clone made out of a paper bomb is meant to be thrown at the enemy, not to blow the caster off
 * their feet, and a clone wall should not take itself out. Everything that counts as an ally of the
 * clone (its caster and the caster's other clones) is dropped from the blast, which also spares them
 * the knockback.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExplosionEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof ShadowCloneEntity clone) || !clone.isExplosive()) {
            return;
        }
        event.getAffectedEntities().removeIf(clone::isAlliedTo);
    }
}
