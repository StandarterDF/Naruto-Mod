package com.sekwah.narutomod.enchantment;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NarutoEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, NarutoMod.MOD_ID);

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[] {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static final RegistryObject<Enchantment> CHAKRA_BOOST = ENCHANTMENTS.register("chakra_boost",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    public static final RegistryObject<Enchantment> CHAKRA_REGEN = ENCHANTMENTS.register("chakra_regen",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    public static final RegistryObject<Enchantment> STAMINA_BOOST = ENCHANTMENTS.register("stamina_boost",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    public static final RegistryObject<Enchantment> STAMINA_REGEN = ENCHANTMENTS.register("stamina_regen",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    public static final RegistryObject<Enchantment> SENIN_CHAKRA = ENCHANTMENTS.register("senin_chakra",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    public static final RegistryObject<Enchantment> SENIN_REGEN = ENCHANTMENTS.register("senin_regen",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS) {
                @Override public int getMaxLevel() { return 3; }
                @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
                @Override public int getMaxCost(int level) { return 25 + (level - 1) * 10; }
            });

    private NarutoEnchantments() {
    }

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
