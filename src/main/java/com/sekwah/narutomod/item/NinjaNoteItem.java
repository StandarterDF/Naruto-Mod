package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A page torn out of somebody else's field notes: lore found in a chest instead of read in a book.
 *
 * <p>A scrap carries one line at a time and picks it when it is read, so a handful of them found on
 * the road reads like a handful of different people talking rather than six copies of one page.
 * Reading burns the scrap, because a page is a thing you can only lend once - a reader in creative
 * gets to keep it, since they are browsing rather than surviving.</p>
 *
 * <p>Both the title and the six notes are translation keys held in the language files, so a scrap
 * found on a Russian server reads in Russian. See {@code narutomod.note.title} and
 * {@code narutomod.note.1} to {@code narutomod.note.6}.</p>
 */
public class NinjaNoteItem extends Item {

    /** How many different notes the language files carry. */
    public static final int NOTES = 6;

    public NinjaNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        int note = player.getRandom().nextInt(NOTES) + 1;
        player.displayClientMessage(Component.translatable("narutomod.note.title")
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("narutomod.note." + note)
                .withStyle(ChatFormatting.GRAY), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
