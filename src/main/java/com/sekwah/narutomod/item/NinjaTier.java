package com.sekwah.narutomod.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public enum NinjaTier implements Tier {
    KATANA(2, 250, 6.0F, 1.5F, 14,
            () -> {
        return Ingredient.of(Items.IRON_INGOT);
    }, BlockTags.NEEDS_IRON_TOOL),
    /**
     * The named blades: the same steel as a katana, but considerately more of it. A legendary sword
     * is a once-in-a-lifetime find, not something a caster should have to reforge every skirmish.
     */
    LEGENDARY(2, 1800, 6.0F, 1.5F, 18,
            () -> {
        return Ingredient.of(Items.PRISMARINE_SHARD);
    }, BlockTags.NEEDS_IRON_TOOL),
    ;

    private final int level;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantmentValue;
    private final LazyLoadedValue<Ingredient> repairIngredient;

    private final TagKey<Block> tag;

    private NinjaTier(int level, int uses, float speed, float damage, int enchantmentValue, Supplier<Ingredient> repairIngredient, TagKey<Block> tag) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = new LazyLoadedValue<>(repairIngredient);
        this.tag = tag;
    }

    public int getUses() {
        return this.uses;
    }

    public float getSpeed() {
        return this.speed;
    }

    public float getAttackDamageBonus() {
        return this.damage;
    }

    public int getLevel() {
        return this.level;
    }

    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }
    public TagKey<Block> getTag() {
        return tag;
    }
}
