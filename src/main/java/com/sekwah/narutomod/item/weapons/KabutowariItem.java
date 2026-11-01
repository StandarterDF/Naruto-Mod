package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.item.NinjaTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Kabutowari: a hammer-axe built to crack any guard. Its blows ignore shields and bypass most of the
 * target's armour. The armour-piercing and shield-breaking behaviour is enforced in
 * {@code events/KabutowariEvents}.
 */
public class KabutowariItem extends SwordItem {

    public KabutowariItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    public KabutowariItem(Properties properties) {
        this(NinjaTier.LEGENDARY, 6, -2.8f, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.kabutowari.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
