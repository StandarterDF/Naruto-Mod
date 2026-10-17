package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.RasenshurikenJutsuEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps a blast away from its own side.
 *
 * <p>A clone made out of a paper bomb is meant to be thrown at the enemy, not to blow the caster off
 * their feet, and a clone wall should not take itself out. Everything that counts as an ally of the
 * clone (its caster and the caster's other clones) is dropped from the blast, which also spares them
 * the knockback.</p>
 *
 * <p>The storm of blasts a rasenshuriken sets off over whoever it caught gets the same treatment,
 * for the same reason: it is thrown into a fight the caster is standing next to, and the disc is a
 * bigger explosion than anything else in the mod. Only the caster's own side is spared - whoever is
 * being held in the middle of it very much is not.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExplosionEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof ShadowCloneEntity clone && clone.isExplosive()) {
            event.getAffectedEntities().removeIf(clone::isAlliedTo);
        } else if (source instanceof RasenshurikenJutsuEntity disc) {
            Entity owner = disc.getOwner();
            if (owner != null) {
                event.getAffectedEntities().removeIf(entity -> entity == owner || entity.isAlliedTo(owner));
            }
        }
    }
}
