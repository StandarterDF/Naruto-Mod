package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Worker's Seal: hold it in your off hand while casting a clone technique and the clones come
 * out as workers instead of fighters.
 *
 * <p>It is a catalyst, not a one-shot: it is never right-clicked and never consumed. What the
 * workers do is decided by the tool in the caster's main hand - a hoe sends them to the fields, a
 * pickaxe down the mine and an axe to the tree line. They appear in the same numbers the technique
 * normally summons and around the caster, just like ordinary clones, and then walk off to work
 * around that spot until their chakra runs out. See
 * {@code com.sekwah.narutomod.entity.WorkerJob} and
 * {@code com.sekwah.narutomod.entity.ai.WorkerBlockGoal}.</p>
 */
public class WorkerSealItem extends Item {

    public WorkerSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.narutomod.worker_seal.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.worker_seal.tool")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
