package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SenriganEvasionAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenriganEvasionEvents {
    private SenriganEvasionEvents() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        // Do not evade your own projectiles.
        if (event.getProjectile().getOwner() == player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            Projectile projectile = event.getProjectile();
            CompoundTag data = projectile.getPersistentData();
            // Already evaded by this same player: keep passing through without paying again.
            if (data.contains("narutomod:senriganEvaded")) {
                long mark = data.getLong("narutomod:senriganEvaded");
                int storedPlayer = (int) (mark >> 32);
                int expiry = (int) (mark & 0xFFFFFFFFL);
                if (storedPlayer == player.getId() && projectile.tickCount < expiry) {
                    event.setCanceled(true);
                    return;
                }
                data.remove("narutomod:senriganEvaded");
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (ninjaData.getChakra() >= cost) {
                ninjaData.useChakra(cost, 10);
                event.setCanceled(true);
                // Mark so the same projectile does not charge again on the next tick.
                data.putLong("narutomod:senriganEvaded",
                        ((long) player.getId() << 32) | (projectile.tickCount + 20L));
            } else {
                // Not enough chakra: end the stance and let the projectile land.
                ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                        com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
            }
        });
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (ninjaData.getChakra() >= cost) {
                // Half damage passes through, paid for with chakra.
                event.setAmount(event.getAmount() * 0.5f);
                ninjaData.useChakra(cost, 10);
            } else {
                // Not enough chakra: the stance ends and this hit lands in full.
                ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                        com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
            }
        });
    }
}