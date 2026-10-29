package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.item.ComboSwordItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grows the great swords' chains and pays out the damage they are worth.
 *
 * <p>This has to happen on the hit itself rather than on the sword: the damage of a blow is worked
 * out before the sword is told it has landed, so the chain has to be read and extended from the
 * attacker's hand as the hit goes in. It runs before everything else so that a caster's chakra fist
 * and other bonuses stack on top of the great sword's weight rather than the other way round.</p>
 *
 * <p>The Kubikiribocho also keeps a tally of every kill it takes: each one makes it a little
 * sharper, with no ceiling, so a blade that has been through a war hits harder than a fresh one.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ComboSwordEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Player player)
                || event.getSource().getEntity() != player) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ComboSwordItem sword)) {
            return;
        }

        long gameTime = player.level().getGameTime();
        int combo = sword.extendCombo(held, gameTime);
        float bonus = sword.getBonusDamage(combo);
        if (bonus > 0.0f) {
            event.setAmount(event.getAmount() + bonus);
        }

        if (sword instanceof com.sekwah.narutomod.item.weapons.SamehadaItem samehada) {
            biteChakra(samehada, event.getEntity(), player);
            maybeFeed(samehada, event.getEntity(), player);
        } else if (sword.getChakraPerHit() > 0.0f) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                    ninjaData.addChakra(sword.getChakraPerHit()));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }
        if (victim == killer) {
            return;
        }
        // A caster's own clones are not kills worth a sharper blade.
        if (victim instanceof ShadowCloneEntity clone && clone.isOwnedBy(killer)) {
            return;
        }
        ItemStack held = killer.getMainHandItem();
        if (!(held.getItem() instanceof ComboSwordItem sword) || !sword.gainsKills()) {
            return;
        }
        ComboSwordItem.addKill(held);
    }

    private static void biteChakra(com.sekwah.narutomod.item.weapons.SamehadaItem sword,
                                   LivingEntity victim, Player owner) {
        float bite = sword.chakraBite(victim);
        if (bite <= 0.0f) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ownerData -> {
            if (victim instanceof Player victimPlayer) {
                victimPlayer.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(victimData -> {
                    float stolen = Math.min(bite, victimData.getChakra());
                    if (stolen > 0.0f) {
                        victimData.setChakra(victimData.getChakra() - stolen);
                        ownerData.addChakra(stolen);
                    }
                });
                return;
            }
            ownerData.addChakra(bite);
        });
    }

    private static void maybeFeed(com.sekwah.narutomod.item.weapons.SamehadaItem sword,
                                 LivingEntity victim, Player owner) {
        if (!com.sekwah.narutomod.item.weapons.SamehadaItem.isPeaceful(victim)) {
            return;
        }
        if (victim instanceof net.minecraft.world.entity.OwnableEntity ownable
                && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ownerData ->
                sword.feedCaster(owner, ownerData));
    }
}
