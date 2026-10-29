package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.ComboSwordItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the great blades too heavy to swing properly for a caster who has not trained Kenjutsu.
 *
 * <p>An item cannot see who is holding it, so the blade always hands out its normal attack speed
 * and the penalty is applied to the holder instead. A single {@code MULTIPLY_TOTAL} modifier is
 * added while the main hand holds a great sword whose requirement is not met, and removed again as
 * soon as the caster either trains enough or puts the blade away.</p>
 *
 * <p>The check runs on both sides: the server owns the actual swing cooldown, while the client
 * draws the attack indicator, and the two must agree or the blade feels broken rather than heavy.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ComboSwordAttributeEvents {

    /** How often, in ticks, the held blade is re-checked. */
    private static final int CHECK_INTERVAL = 10;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        AttributeInstance attackSpeed = player.getAttributes().getInstance(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        boolean tooHeavy = held.getItem() instanceof ComboSwordItem sword
                && !sword.playerMeetsRequirement(player);
        boolean penalised = attackSpeed.getModifier(ComboSwordItem.HEAVY_PENALTY_UUID) != null;
        if (tooHeavy == penalised) {
            return;
        }

        if (tooHeavy) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    ComboSwordItem.HEAVY_PENALTY_UUID,
                    "Combo sword heavy blade",
                    ComboSwordItem.HEAVY_SPEED_MULTIPLIER,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else {
            attackSpeed.removeModifier(ComboSwordItem.HEAVY_PENALTY_UUID);
        }
    }
}
