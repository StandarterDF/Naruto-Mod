package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.item.weapons.HiramekareiItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HiramekareiEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();

        // Charge the attacker's blade on a successful hit.
        if (event.getSource().getEntity() instanceof Player attacker
                && event.getSource().getDirectEntity() == attacker
                && victim != attacker) {
            ItemStack held = attacker.getMainHandItem();
            if (held.getItem() instanceof HiramekareiItem && !HiramekareiItem.isReleasing(attacker)) {
                HiramekareiItem.setChakra(held,
                        HiramekareiItem.getChakra(held) + HiramekareiItem.CHARGE_PER_HIT);
            }
        }

        // Blocking with the blade absorbs 90% of the blow and stores 90% of the absorbed amount.
        if (victim instanceof Player player) {
            if (player.isUsingItem() && player.getUseItem().getItem() instanceof HiramekareiItem) {
                if (event.getSource().getEntity() == victim) {
                    return;
                }
                if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                    return;
                }
                float original = event.getAmount();
                float absorbed = original * 0.9f;
                event.setAmount(original * 0.1f);
                ItemStack held = player.getUseItem();
                if (held.getItem() instanceof HiramekareiItem) {
                    float add = absorbed * 0.9f;
                    HiramekareiItem.setChakra(held, HiramekareiItem.getChakra(held) + add);
                }
            }
        }
    }

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
        if (!(held.getItem() instanceof HiramekareiItem)) {
            return;
        }
        HiramekareiItem.addKill(held);
    }
}