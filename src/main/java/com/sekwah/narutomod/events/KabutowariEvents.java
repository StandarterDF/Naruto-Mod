package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.NarutoItems;
import com.sekwah.narutomod.item.weapons.KabutowariItem;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kabutowari: 75% armour bypass on a normal hit, and a guaranteed pass through any shield/block.
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KabutowariEvents {

    /**
     * 75% of the damage goes past the target's armour: only 25% of it is reduced by armour.
     *
     * <p>{@code reduced} is the amount already reduced by armour. {@code armorReduction} is the
     * vanilla-ish armour factor {@code min(0.8, armor * 0.04)}. The new amount is computed so that
     * armour only shaves 25% of the bypassed share:</p>
     * <pre>
     * newAmount = reduced / (1 - armorReduction) * 0.25 + reduced * 0.75
     * </pre>
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof Player attacker)) {
            return;
        }
        ItemStack held = attacker.getMainHandItem();
        if (!(held.getItem() instanceof KabutowariItem)) {
            return;
        }
        float reduced = event.getAmount();
        int armor = victim.getArmorValue();
        double armorReduction = Math.min(0.8d, armor * 0.04d);
        float newAmount = (float) (reduced / (1.0d - armorReduction) * 0.25d + reduced * 0.75d);
        event.setAmount(newAmount);
    }

    /**
     * Shields and blocks do not stop Kabutowari: if the target is blocking the blow, re-issue the
     * damage through a magic source that bypasses the guard.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof Player attacker)) {
            return;
        }
        ItemStack held = attacker.getMainHandItem();
        if (!(held.getItem() instanceof KabutowariItem)) {
            return;
        }
        if (!victim.isDamageSourceBlocked(event.getSource())) {
            return;
        }
        event.setCanceled(true);
        victim.hurt(victim.level().damageSources().magic(), event.getAmount());
    }
}
