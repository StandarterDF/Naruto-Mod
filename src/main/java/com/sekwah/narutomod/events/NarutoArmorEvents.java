package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.armor.NarutoArmorItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

    // Cached enchantment lookups: registry access every tick is expensive.
    private static Enchantment CACHED_CHAKRA_BOOST;
    private static Enchantment CACHED_STAMINA_BOOST;
    private static Enchantment CACHED_CHAKRA_REGEN;
    private static Enchantment CACHED_STAMINA_REGEN;
    private static Enchantment CACHED_SENIN_BOOST;
    private static Enchantment CACHED_SENIN_REGEN;
    private static boolean enchantmentsResolved = false;
    private static net.minecraft.core.RegistryAccess cachedRegistryAccess;

    private static void resolveEnchantments(net.minecraft.world.level.Level level) {
        // Re-resolve when the registry access changes (new world / reloaded datapacks).
        if (enchantmentsResolved && level.registryAccess() == cachedRegistryAccess) {
            return;
        }
        var reg = level.registryAccess().registry(Registries.ENCHANTMENT).orElse(null);
        if (reg == null) {
            return;
        }
        CACHED_CHAKRA_BOOST = reg.get(new ResourceLocation("narutomod", "chakra_boost"));
        CACHED_STAMINA_BOOST = reg.get(new ResourceLocation("narutomod", "stamina_boost"));
        CACHED_CHAKRA_REGEN = reg.get(new ResourceLocation("narutomod", "chakra_regen"));
        CACHED_STAMINA_REGEN = reg.get(new ResourceLocation("narutomod", "stamina_regen"));
        CACHED_SENIN_BOOST = reg.get(new ResourceLocation("narutomod", "senin_chakra"));
        CACHED_SENIN_REGEN = reg.get(new ResourceLocation("narutomod", "senin_regen"));
        cachedRegistryAccess = level.registryAccess();
        enchantmentsResolved = true;
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            float chakraCapacity = 0.0f;
            float maxHealth = 0.0f;
            float chakraRegen = 0.0f;
            float enchantChakra = 0.0f;
            float enchantStamina = 0.0f;
            float enchantChakraRegen = 0.0f;
            float enchantStaminaRegen = 0.0f;
            float enchantSenin = 0.0f;
            float enchantSeninRegen = 0.0f;
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
                // Enchantment bonuses.
                resolveEnchantments(player.level());
                if (CACHED_CHAKRA_BOOST != null) enchantChakra += 10.0f * EnchantmentHelper.getItemEnchantmentLevel(CACHED_CHAKRA_BOOST, piece);
                if (CACHED_STAMINA_BOOST != null) enchantStamina += 10.0f * EnchantmentHelper.getItemEnchantmentLevel(CACHED_STAMINA_BOOST, piece);
                if (CACHED_CHAKRA_REGEN != null) enchantChakraRegen += EnchantmentHelper.getItemEnchantmentLevel(CACHED_CHAKRA_REGEN, piece);
                if (CACHED_STAMINA_REGEN != null) enchantStaminaRegen += EnchantmentHelper.getItemEnchantmentLevel(CACHED_STAMINA_REGEN, piece);
                if (CACHED_SENIN_BOOST != null) enchantSenin += 10.0f * EnchantmentHelper.getItemEnchantmentLevel(CACHED_SENIN_BOOST, piece);
                if (CACHED_SENIN_REGEN != null) enchantSeninRegen += EnchantmentHelper.getItemEnchantmentLevel(CACHED_SENIN_REGEN, piece);
            }

            ninjaData.setArmorChakraBonus(chakraCapacity);
            ninjaData.setEnchantChakraBonus(enchantChakra);
            ninjaData.setEnchantStaminaBonus(enchantStamina);
            ninjaData.setEnchantSeninBonus(enchantSenin);
            if (enchantChakraRegen > 0 && player.tickCount % 20 == 0) {
                ninjaData.addChakra(enchantChakraRegen);
            }
            if (enchantStaminaRegen > 0 && player.tickCount % 20 == 0) {
                ninjaData.addStamina(enchantStaminaRegen);
            }
            if (enchantSeninRegen > 0 && player.tickCount % 20 == 0) {
                ninjaData.addSeninMana(enchantSeninRegen);
            }

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
