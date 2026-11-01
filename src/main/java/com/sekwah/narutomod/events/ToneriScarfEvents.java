package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.ToneriScarfItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Toneri's scarf: a 15% reduction on incoming damage while it is worn in the chest slot. Fall damage and
 * self-inflicted damage are left untouched, and carrying several scarves does not stack.
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ToneriScarfEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        // Leave fall damage and self-inflicted damage alone.
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }
        if (event.getSource().getDirectEntity() == player || event.getSource().getEntity() == player) {
            return;
        }
        if (!ToneriScarfItem.hasScarf(player)) {
            return;
        }
        event.setAmount(event.getAmount() * 0.85f);
    }
}
