package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealOneAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealTwoAbility;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeDamageEvents {
    private ModeDamageEvents() {}

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player caster)) {
            return;
        }
        INinjaData data = caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) return;

        float multiplier = 1.0f;
        if (SixPathsModeAbility.isActive(caster)) {
            multiplier = Math.max(multiplier, 4.0f);
        } else if (data.isSageMode()) {
            multiplier = Math.max(multiplier, 3.0f);
        }
        var toggles = data.getToggleAbilityData().getAbilitiesHashSet();
        if (toggles.contains(CurseSealOneAbility.ID) || toggles.contains(CurseSealTwoAbility.ID)) {
            multiplier = Math.max(multiplier, 1.75f);
        }
        if (toggles.contains(PerfectCloakAbility.ID)) {
            multiplier = Math.max(multiplier, 3.0f);
        } else if (toggles.contains(DividedCloakAbility.ID)) {
            multiplier = Math.max(multiplier, 2.5f);
        } else if (toggles.contains(TailedCloakAbility.ID)) {
            multiplier = Math.max(multiplier, 2.0f);
        } else if (toggles.contains(ChakraCloakAbility.ID)) {
            multiplier = Math.max(multiplier, 1.5f);
        }
        // Eight Gates: no technique damage boost (taijutsu only).

        if (multiplier > 1.0f) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }
}