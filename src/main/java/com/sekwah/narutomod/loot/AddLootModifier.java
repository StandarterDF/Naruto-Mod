package com.sekwah.narutomod.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sekwah.narutomod.config.NarutoConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A global loot modifier that hands out one of a weighted handful of items.
 *
 * <p>It exists so the mod's own things - scrolls above all - can turn up in vanilla chests without
 * replacing the vanilla tables. One modifier is placed per tier of dungeon (see the JSON under
 * {@code data/narutomod/loot_modifiers/}), each with its own list of tables and its own odds; the
 * deeper the structure, the better the list behind the roll.</p>
 *
 * <p>The roll is luck aware: the Luck effect (and anything else that raises
 * {@link LootContext#getLuck()}) adds {@link #luckBonus()} to the chance per point, so a lucky ninja
 * finds more. That is the one line of luck handling the whole loot side needs - the tables are
 * otherwise plain data.</p>
 */
public class AddLootModifier extends LootModifier {

    public static final Codec<AddLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).and(instance.group(
                    WeightedItem.CODEC.listOf().fieldOf("entries").forGetter(AddLootModifier::entries),
                    Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(AddLootModifier::chance),
                    Codec.FLOAT.optionalFieldOf("luck_bonus", 0.05f).forGetter(AddLootModifier::luckBonus),
                    Codec.INT.optionalFieldOf("min", 1).forGetter(AddLootModifier::min),
                    Codec.INT.optionalFieldOf("max", 1).forGetter(AddLootModifier::max)
            )).apply(instance, AddLootModifier::new));

    private final List<WeightedItem> entries;
    private final float chance;
    private final float luckBonus;
    private final int min;
    private final int max;

    public AddLootModifier(LootItemCondition[] conditions, List<WeightedItem> entries, float chance,
                           float luckBonus, int min, int max) {
        super(conditions);
        this.entries = List.copyOf(entries);
        this.chance = chance;
        this.luckBonus = luckBonus;
        this.min = Math.max(0, min);
        this.max = Math.max(this.min, max);
    }

    public List<WeightedItem> entries() {
        return this.entries;
    }

    public float chance() {
        return this.chance;
    }

    public float luckBonus() {
        return this.luckBonus;
    }

    public int min() {
        return this.min;
    }

    public int max() {
        return this.max;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (this.entries.isEmpty()) {
            return generatedLoot;
        }
        if (!NarutoConfig.scrollLootEnabled) {
            return generatedLoot;
        }
        RandomSource random = context.getRandom();
        float effective = this.chance + this.luckBonus * Math.max(0f, context.getLuck());
        if (random.nextFloat() >= Mth.clamp(effective, 0f, 1f)) {
            return generatedLoot;
        }
        WeightedItem picked = pick(random);
        if (picked == null) {
            return generatedLoot;
        }
        int count = this.min >= this.max ? this.min : this.min + random.nextInt(this.max - this.min + 1);
        if (count > 0) {
            generatedLoot.add(new ItemStack(picked.item(), count));
        }
        return generatedLoot;
    }

    /** Rolls one entry from the list by weight; a weight of zero or less is simply never picked. */
    private WeightedItem pick(RandomSource random) {
        int total = 0;
        for (WeightedItem entry : this.entries) {
            if (entry.weight() > 0) {
                total += entry.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (WeightedItem entry : this.entries) {
            if (entry.weight() <= 0) {
                continue;
            }
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }

    /** One item and how likely it is to be the one picked when the roll happens. */
    public record WeightedItem(Item item, int weight) {
        public static final Codec<WeightedItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(WeightedItem::item),
                Codec.intRange(0, 1000).optionalFieldOf("weight", 1).forGetter(WeightedItem::weight)
        ).apply(instance, WeightedItem::new));
    }
}
