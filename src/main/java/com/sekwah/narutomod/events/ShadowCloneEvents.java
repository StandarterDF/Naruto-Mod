package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes what a clone earns at a kill to the clone: the experience to its caster, and the drops
 * into the caster's supply chest.
 *
 * <p>A clone is not a player, so the orbs a mob drops when a clone kills it would just lie where the
 * mob died - and a clone is usually sent far enough from its caster that they would time out before
 * anyone walked over. Instead of dropping the orbs at the kill, the experience is handed straight to
 * the clone's caster the moment the mob dies.</p>
 *
 * <p>The drops go the same way: rather than being left on the ground, where they would rot before
 * anyone walked over, they are handed to the clone and end up in the caster's supply chest (or in
 * the clone's own bag, handed over when it pops). Every clone does this, not only a hunting worker -
 * one that was summoned for a fight is just as able to pick up what it kills. Anything the clone
 * missed is still on the ground, and {@code ClonePickupLootGoal} walks it over to pick that up too.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShadowCloneEvents {

    /**
     * A caster's own swing pops their clone: it is the quickest way to dismiss one that is in the
     * way, and it is how clones were cleared in 1.7.10.
     *
     * <p>This runs off the click rather than off the damage so it cannot be swallowed on the way.
     * A clone copies the caster's offhand and keeps that shield up while it fights, and a blocked
     * blow deals no damage at all - so a caster trying to dismiss a clone that was busy would find
     * every swing stopped by their own shield. Weakness or a hit that works out to zero would skip
     * the damage call the same way. Popping the clone here means the gesture always works.</p>
     *
     * <p>The event also fires on the client; only the server may pop anything, or the smoke and the
     * poof sound would play twice.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackOwnClone(AttackEntityEvent event) {
        Player caster = event.getEntity();
        if (caster.level().isClientSide) {
            return;
        }
        if (!(event.getTarget() instanceof ShadowCloneEntity clone) || !clone.isOwnedBy(caster)) {
            return;
        }
        event.setCanceled(true);
        clone.dispel();
    }

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

    /** A clone bags its kill's drops instead of leaving them to rot in the field. */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity().getKillCredit() instanceof ShadowCloneEntity clone)) {
            return;
        }
        // Before the caster can open the supply chest there is nowhere to put a kill's drops, so
        // they are left on the ground for the caster to pick up themselves.
        if (!clone.canGatherLoot()) {
            return;
        }
        List<ItemEntity> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }
        for (ItemEntity drop : drops) {
            clone.collect(drop.getItem());
        }
        event.getDrops().clear();
    }
}
