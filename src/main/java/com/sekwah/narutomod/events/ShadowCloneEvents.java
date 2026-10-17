package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes what a clone earns at a kill to the clone: the experience to its caster, and - for a hunter
 * worker - the drops into its bag.
 *
 * <p>A clone is not a player, so the orbs a mob drops when a clone kills it would just lie where the
 * mob died - and a worker is usually sent far enough from its caster that they would time out
 * before anyone walked over. Instead of dropping the orbs at the kill, the experience is handed
 * straight to the clone's caster the moment the mob dies.</p>
 *
 * <p>A hunting worker is out to bring food home, so the meat a mob drops goes into its bag rather
 * than on the ground, where it would rot away before the worker popped. Only a hunter does this: a
 * clone that happens to kill something in a fight leaves its drops in the world as before.</p>
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

    /** A hunting worker bags its kill's drops instead of leaving them to rot in the field. */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity().getKillCredit() instanceof ShadowCloneEntity clone)) {
            return;
        }
        if (!clone.isWorker() || clone.getWorkerJob() != WorkerJob.HUNT) {
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
