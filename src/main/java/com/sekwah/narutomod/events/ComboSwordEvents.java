package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.ComboSwordItem;
import com.sekwah.narutomod.item.weapons.SamehadaItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        LivingEntity victim = event.getEntity();
        if (sword instanceof SamehadaItem samehada) {
            biteChakra(samehada, victim, player);
            maybeFeed(samehada, victim, player);
        } else if (sword.getChakraPerHit() > 0.0f) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                    ninjaData.addChakra(sword.getChakraPerHit()));
        }
    }

    /**
     * Samehada takes chakra out of whatever it cuts. A victim with a pool of its own (another ninja)
     * is drained for real; a plain beast has no pool, so the blade pulls the chakra out of its flesh
     * and the amount follows its maximum health instead.
     */
    private static void biteChakra(SamehadaItem sword, LivingEntity victim, Player owner) {
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

    /**
     * A peaceful creature feeds the blade's caster. The caster's own pets are spared the indignity:
     * cutting your own dog should not hand out a snack.
     */
    private static void maybeFeed(SamehadaItem sword, LivingEntity victim, Player owner) {
        if (!SamehadaItem.isPeaceful(victim)) {
            return;
        }
        if (victim instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ownerData ->
                sword.feedCaster(owner, ownerData));
    }
}
