package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SenriganFlightAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganSightAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Senrigan's two borrowed states honest.
 *
 * <p>Possession survives being hurt, being carried to another dimension and logging out - that is
 * the point of it - but it does not survive the borrowed body being destroyed. A killing blow is
 * cancelled and the caster is put back in their own body at the health they left it with. Damage
 * that bypasses invulnerability is left alone, because the void and {@code /kill} are exactly the
 * cases where the world is ending rather than a body being broken.</p>
 *
 * <p>Death also has to give the flight of the Senrigan back: the toggle set is copied onto the new
 * player, so without this a respawned caster would still be marked as flying.</p>
 *
 * <p>A borrowed body does not knit itself back together. It arrives with the clone's wounds - usually
 * the single point of health a combat clone has - and natural regeneration is switched off while the
 * caster wears it, so a possession is a short dive that can be ended by one solid hit, not a free
 * refill. Fire, frost and held breath are part of the body and travel with it, see
 * {@code SenriganSightAbility}.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SenriganEvents {

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCanceled()) {
            return;
        }
        if (!SenriganSightAbility.isPossessing(player)) {
            return;
        }
        // Saturation, food regeneration and regeneration effects all go through here. None of them
        // work on a chakra construct: the clone's health is what it is for as long as it is worn.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        if (!SenriganSightAbility.isPossessing(player)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getAmount() < player.getHealth()) {
            return;
        }
        // The borrowed body is broken: the caster snaps back home instead of dying.
        event.setCanceled(true);
        SenriganSightAbility.endPossession(player);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            SenriganSightAbility.clear(player);
            SenriganFlightAbility.release(player);
        }
    }
}
