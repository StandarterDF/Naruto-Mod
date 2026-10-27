package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.item.NinjaTier;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bone blade grown by the Camellia Dance.
 *
 * <p>It is deliberately not a durable item: there is no durability bar to repair, no anvil that can
 * mend it and no enchantment table that will touch it. Instead the blade carries a small number of
 * strikes in its own tag, and the last one breaks it. That keeps the technique a technique - a few
 * cuts of bone, not a free sword that can be carried around and upgraded.</p>
 */
public class BoneBladeItem extends SwordItem {

    /** NBT key holding the strikes left in this particular blade. */
    public static final String CHARGES_TAG = "narutomod:bone_charges";

    /** How many cuts one grown blade is worth. */
    public static final int MAX_CHARGES = 8;

    public BoneBladeItem(Properties properties) {
        super(NinjaTier.KATANA, 3, -1.9f, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (attacker.level().isClientSide) {
            return true;
        }
        // Most of the practice comes from the cuts, not the draw: the blade is redrawn rarely once
        // it lasts, so each landed cut counts as a full cast-equivalent.
        if (attacker instanceof Player player) {
            player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                    .ifPresent(ninjaData -> com.sekwah.narutomod.abilities.mastery.Mastery.recordCredit(
                            player, ninjaData, com.sekwah.narutomod.abilities.NarutoAbilities.CAMELLIA_DANCE.get(), 1.0f));
        }
        int charges = remaining(stack) - 1;
        if (charges <= 0) {
            stack.shrink(1);
            if (attacker instanceof Player player) {
                player.displayClientMessage(Component.translatable("item.narutomod.bone_blade.broken")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        }
        stack.getOrCreateTag().putInt(CHARGES_TAG, charges);
        return true;
    }

    /** @return how many cuts are left before the bone gives out. */
    public static int remaining(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(CHARGES_TAG) ? tag.getInt(CHARGES_TAG) : MAX_CHARGES;
    }

    /**
     * The blade has no durability at all. TieredItem would otherwise hand it the tier's 250 points
     * and put a durability bar on it, which is exactly the thing the charge counter exists to avoid.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return 0;
    }

    /**
     * Without this vanilla still treats the item as damageable and the first swing's
     * {@code hurtAndBreak} breaks it: the durability is zero, so any damage is lethal. The charges
     * in the tag are the only wear this blade knows.
     */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** A fresh blade starts with its full, short life. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(com.sekwah.narutomod.item.NarutoItems.BONE_BLADE.get());
        stack.getOrCreateTag().putInt(CHARGES_TAG, MAX_CHARGES);
        return stack;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.bone_blade.charges",
                Component.literal(String.valueOf(remaining(stack))).withStyle(ChatFormatting.YELLOW),
                Component.literal(String.valueOf(MAX_CHARGES)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.bone_blade.no_enchant")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The charges are shown as a vanilla durability bar even though there is no durability to
     * repair: green while fresh, red when nearly gone, like any blade.
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return remaining(stack) < MAX_CHARGES;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float fraction = (float) remaining(stack) / (float) MAX_CHARGES;
        return Math.round(13.0f * Math.max(0.0f, Math.min(1.0f, fraction)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = (float) remaining(stack) / (float) MAX_CHARGES;
        return net.minecraft.util.Mth.hsvToRgb(Math.max(0.0f, Math.min(1.0f, fraction)) / 3.0f, 1.0f, 1.0f);
    }
}
