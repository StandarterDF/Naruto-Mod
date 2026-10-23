package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires the friends list into the world: it arrives when a player joins, and it is the last line of
 * defence against a technique landing on a friend.
 *
 * <p>Most techniques ask {@link Friends} before picking a target, so this handler should rarely see
 * anything. It exists for the cases that are hard to ask at the source - a disc already in flight, a
 * clone swinging at somebody who became a friend a moment ago - and it only ever cancels the mod's
 * own techniques and its clones, never an ordinary sword blow between two friends.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FriendEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Friends.onLogin(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        // A clone's blow is its caster's blow.
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (attacker == null) {
            return;
        }
        if (attacker instanceof ShadowCloneEntity) {
            if (Friends.areFriends(attacker, victim)) {
                event.setCanceled(true);
            }
            return;
        }
        if (attacker instanceof Player && victim instanceof Player
                && Friends.isTechniqueDamage(source) && Friends.areFriends(attacker, victim)) {
            event.setCanceled(true);
        }
    }
}
