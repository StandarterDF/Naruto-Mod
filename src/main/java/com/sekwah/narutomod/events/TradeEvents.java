package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's goods in vanilla trade windows: nobody in the mod sells them, so a village librarian
 * and a wandering trader carry them instead.
 *
 * <p>This is deliberately small and late-game. The librarian only parts with a scroll once they are
 * a proper librarian (level two and up), and the wandering trader has the consumables a ninja burns
 * through: seals, pills, rations and smoke bombs. Everything is bought for emeralds, so villages
 * stay a legitimate way to gear up without turning into a loot fountain.</p>
 *
 * <p>Vanilla's own {@code ItemsForEmeralds} listing is private, so the few offers are written out
 * here; they are plain emerald-for-item deals and need nothing else.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeEvents {

    /** Vanilla's own price drift for emerald trades. */
    private static final float PRICE_MULTIPLIER = 0.05f;

    private TradeEvents() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }
        // Level two: the first scroll a village is likely to have, at a price that stings.
        event.getTrades().get(2).add(sell(NarutoItems.SCROLL_RASENGAN.get(), 32, 1, 2, 15));
        // Level three: a compass to keep track of where the good chests were.
        event.getTrades().get(3).add(sell(NarutoItems.CHAKRA_COMPASS.get(), 18, 1, 3, 10));
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(sell(NarutoItems.CHAKRA_SEAL.get(), 8, 4, 8, 4));
        event.getGenericTrades().add(sell(NarutoItems.MILITARY_PILLS.get(), 6, 3, 8, 4));
        event.getGenericTrades().add(sell(NarutoItems.RAMEN.get(), 4, 2, 8, 2));
        event.getGenericTrades().add(sell(NarutoItems.SMOKE_BOMB.get(), 5, 4, 8, 3));
        // Rare stock: a scroll the wandering trader only sometimes carries.
        event.getRareTrades().add(sell(NarutoItems.SCROLL_EIGHT_GATES.get(), 48, 1, 1, 20));
    }

    /** One plain "so many emeralds for so many of an item" offer. */
    private static VillagerTrades.ItemListing sell(Item item, int emeralds, int count, int maxUses, int xp) {
        return (trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, emeralds),
                new ItemStack(item, count), maxUses, xp, PRICE_MULTIPLIER);
    }
}
