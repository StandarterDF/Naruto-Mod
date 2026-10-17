package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.armor.NarutoArmorItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Hands out what the mod's armour is for.
 *
 * <p>The pieces are ninja kit, so what they carry is chakra rather than armour points: headbands
 * help a caster get their chakra back, ANBU masks widen the pool, flak jackets and the Akatsuki
 * cloak put more into the body. The bonuses live on the items themselves (see
 * {@link NarutoArmorItem.Bonus}) and are summed here every tick from whatever is actually worn, so
 * taking a piece off takes its bonus with it with no bookkeeping.</p>
 *
 * <p>Server side only: the numbers that matter are the chakra pool and the player's own health
 * attribute, both of which the client reads synced copies of.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NarutoArmorEvents {

    /**
     * Fixed identity for the armour's max health modifier, so the same one is replaced rather than
     * stacked up every time the worn set changes.
     */
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("6c1f4d2a-7b3e-4a51-9d2f-0f5b8c1a7e42");
    private static final String HEALTH_MODIFIER_NAME = "narutomod.armor.health";

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            float chakraCapacity = 0.0f;
            float maxHealth = 0.0f;
            float chakraRegen = 0.0f;
            for (ItemStack piece : player.getInventory().armor) {
                if (piece.getItem() instanceof NarutoArmorItem armor) {
                    NarutoArmorItem.Bonus bonus = armor.getBonus();
                    if (bonus.isEmpty()) {
                        continue;
                    }
                    chakraCapacity += bonus.chakraCapacity();
                    maxHealth += bonus.maxHealth();
                    chakraRegen += bonus.chakraRegenPerSecond();
                }
            }

            ninjaData.setArmorChakraBonus(chakraCapacity);

            // The custom pool is topped up rather than the natural regeneration being poked at: an
            // armour bonus should not be able to be switched off by spending chakra.
            if (chakraRegen > 0.0f && player.tickCount % 20 == 0) {
                ninjaData.addChakra(chakraRegen);
            }

            // Only rebuild the modifier when the worn set actually changed; writing it every tick
            // would make the attribute dirty non stop.
            if (ninjaData.getArmorHealthBonus() != maxHealth) {
                AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
                if (attribute != null) {
                    attribute.removeModifier(HEALTH_MODIFIER_ID);
                    if (maxHealth > 0.0f) {
                        attribute.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID,
                                HEALTH_MODIFIER_NAME, maxHealth, AttributeModifier.Operation.ADDITION));
                    }
                }
                ninjaData.setArmorHealthBonus(maxHealth);
            }
        });
    }
}
