package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.ComboSwordItem;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fixes the attack speed attribute of a player holding a ComboSwordItem.
 * If the player has enough Kenjutsu skill nodes, removes the penalty modifier
 * and adds the normal speed modifier.
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ComboSwordAttributeEvents {

    private static long lastFixTick = 0;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        if (player.tickCount - lastFixTick < 20) {
            // Fix every 20 ticks to avoid spam
            return;
        }
        lastFixTick = player.tickCount;

        ItemStack mainHand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!(mainHand.getItem() instanceof ComboSwordItem sword)) {
            return;
        }

        var attackSpeed = player.getAttributes().getInstance(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }

        // Remove the penalty modifier
        attackSpeed.getModifiers().removeIf(mod ->
                mod.getName().contains("penalty") || mod.getName().contains("Penalty"));

        if (sword.playerMeetsRequirement(player)) {
            // Player qualifies: add normal speed
            attackSpeed.addTransientModifier(
                    new AttributeModifier(ComboSwordItem.ATTACK_SPEED_UUID,
                            "Combo sword speed", sword.getAttackSpeed(),
                            AttributeModifier.Operation.ADDITION));
        }
    }
}
