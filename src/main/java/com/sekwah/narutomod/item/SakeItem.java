package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A bottle of rice wine: the price of the Drunken Fist.
 *
 * <p>Drinking sets a short "drunk" window on the player's persistent data. The fist stance reads that
 * window when it is switched on, so a caster has to have had a drink before they can sway. The
 * nausea is part of the bargain - it makes the world swim for a while, which is exactly the unsteady
 * footing the style is built on - and the strength is the warmth of the drink.</p>
 */
public class SakeItem extends Item {

    /** Game time until which the drinker counts as having had a drink. */
    public static final String DRUNK_TAG = "narutomod:alcohol_until";

    /** How long one bottle keeps the caster drunk, in ticks. */
    public static final int DRUNK_TICKS = 20 * 60;

    public SakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.getPersistentData().putLong(DRUNK_TAG, level.getGameTime() + DRUNK_TICKS);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 12, 0, false, true));
            player.displayClientMessage(Component.translatable("item.narutomod.sake.drunk")
                    .withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.0f);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.sake.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
