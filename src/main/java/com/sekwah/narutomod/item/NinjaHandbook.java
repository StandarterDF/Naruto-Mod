package com.sekwah.narutomod.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The little book a new ninja wakes up with.
 *
 * <p>It is a vanilla written book, so it reads in the normal book screen and can be kept, copied or
 * thrown away - no custom GUI, no menu to remember. The pages are translatable components serialised
 * into the book: the text itself lives in the language files, so the book reads in the player's own
 * language instead of being frozen in whichever one the server happened to use.</p>
 *
 * <p>Only the title is fixed, because vanilla shows the book's title as a plain string.</p>
 */
public final class NinjaHandbook {

    /** How many pages the handbook has; the language files must define {@code naruto.book.pageN}. */
    private static final int PAGES = 6;

    private NinjaHandbook() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", "Shinobi Handbook");
        tag.putString("author", "The Village");
        tag.putInt("generation", 0);
        ListTag pages = new ListTag();
        for (int i = 1; i <= PAGES; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("naruto.book.page" + i))));
        }
        tag.put("pages", pages);
        return stack;
    }
}
