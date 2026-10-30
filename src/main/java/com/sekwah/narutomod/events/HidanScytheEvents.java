package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.HidanScytheItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HidanScytheEvents {
    private HidanScytheEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (HidanScytheItem.kenjutsuCount(player) < HidanScytheItem.MIN_KENJUTSU_FOR_USE) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HidanScytheItem)) {
            held = player.getOffhandItem();
            if (!(held.getItem() instanceof HidanScytheItem)) {
                return;
            }
        }
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        // Contract with Jashin: while the scythe is held, the caster is kept alive and hardened.
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 49, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 10, true, false));
        FoodData food = player.getFoodData();
        food.eat(1, 1.0f);
        food.setSaturation(food.getFoodLevel());

        // Drain chakra and senin mana. The scythe works without chakra, but empties the pool fast.
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            float chakraCost = 25.0f;
            if (ninjaData.getChakra() >= chakraCost) {
                ninjaData.useChakra(chakraCost, 0);
            } else {
                ninjaData.setChakra(0.0f);
            }
            if (ninjaData.getSeninMana() > 0.0f) {
                ninjaData.addSeninMana(-5.0f);
                if (ninjaData.getSeninMana() < 0.0f) {
                    ninjaData.setSeninMana(0.0f);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }
        if (HidanScytheItem.kenjutsuCount(killer) < HidanScytheItem.MIN_KENJUTSU_FOR_KILLS) {
            return;
        }
        ItemStack held = killer.getMainHandItem();
        if (!(held.getItem() instanceof HidanScytheItem)) {
            held = killer.getOffhandItem();
            if (!(held.getItem() instanceof HidanScytheItem)) {
                return;
            }
        }
        HidanScytheItem.addKill(held);
    }
}