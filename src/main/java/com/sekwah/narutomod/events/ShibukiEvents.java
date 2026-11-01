package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.item.weapons.ShibukiItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShibukiEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = victim.getLastHurtByMob();
        if (!(attacker instanceof Player player)) {
            return;
        }
        if (player.getLastHurtMob() != victim) {
            return;
        }
        if (victim == player) {
            return;
        }
        if (victim instanceof ShadowCloneEntity clone && clone.isOwnedBy(player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ShibukiItem)) {
            return;
        }
        ShibukiItem.addKill(held);
    }
}
