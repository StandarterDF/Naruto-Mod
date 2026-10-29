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
 * <p>Other professions join in too: a butcher keeps military pills and ramen on the side, a farmer
 * sells onigiri and dango, and a cleric carries chakra seals. The wandering trader's rare stock is
 * where the scrolls a village would never have turn up.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeEvents {

    /** Vanilla's own price drift for emerald trades. */
    private static final float PRICE_MULTIPLIER = 0.05f;

    private TradeEvents() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == VillagerProfession.LIBRARIAN) {
            // Level two: the first scroll a village is likely to have, at a price that stings.
            event.getTrades().get(2).add(sell(NarutoItems.SCROLL_RASENGAN.get(), 32, 1, 2, 15));
            event.getTrades().get(2).add(sell(NarutoItems.SCROLL_EARTH_WALL.get(), 28, 1, 2, 12));
            // Level three: a compass to keep track of where the good chests were, plus a sage scroll.
            event.getTrades().get(3).add(sell(NarutoItems.CHAKRA_COMPASS.get(), 18, 1, 3, 10));
            event.getTrades().get(3).add(sell(NarutoItems.SCROLL_SAGE_MODE.get(), 40, 1, 1, 20));
            // Level four: the gates and the eye scrolls.
            event.getTrades().get(4).add(sell(NarutoItems.SCROLL_EIGHT_GATES.get(), 48, 1, 1, 20));
            event.getTrades().get(4).add(sell(NarutoItems.SCROLL_SHARINGAN.get(), 48, 1, 1, 20));
            event.getTrades().get(4).add(sell(NarutoItems.SCROLL_BYAKUGAN.get(), 48, 1, 1, 20));
            // Level five: the rare ones, at a price that hurts.
            event.getTrades().get(5).add(sell(NarutoItems.SCROLL_RASENSHURIKEN.get(), 64, 1, 1, 30));
            event.getTrades().get(5).add(sell(NarutoItems.SCROLL_KAMUI.get(), 64, 1, 1, 30));
            event.getTrades().get(5).add(sell(NarutoItems.SCROLL_RINNEGAN.get(), 80, 1, 1, 40));
            event.getTrades().get(5).add(sell(NarutoItems.SCROLL_CHIBAKU_TENSEI.get(), 80, 1, 1, 40));
        } else if (event.getType() == VillagerProfession.BUTCHER) {
            event.getTrades().get(2).add(sell(NarutoItems.MILITARY_PILLS.get(), 6, 2, 8, 4));
            event.getTrades().get(2).add(sell(NarutoItems.RAMEN.get(), 4, 2, 8, 2));
            event.getTrades().get(3).add(sell(NarutoItems.MILITARY_PILLS.get(), 4, 4, 6, 6));
        } else if (event.getType() == VillagerProfession.FARMER) {
            event.getTrades().get(2).add(sell(NarutoItems.ONIGIRI.get(), 3, 4, 10, 2));
            event.getTrades().get(2).add(sell(NarutoItems.DANGO.get(), 3, 4, 10, 2));
            event.getTrades().get(3).add(sell(NarutoItems.RAMEN.get(), 5, 2, 6, 3));
        } else if (event.getType() == VillagerProfession.CLERIC) {
            event.getTrades().get(2).add(sell(NarutoItems.CHAKRA_SEAL.get(), 8, 4, 8, 4));
            event.getTrades().get(3).add(sell(NarutoItems.MILITARY_PILLS.get(), 5, 3, 6, 5));
        } else if (event.getType() == VillagerProfession.ARMORER
                || event.getType() == VillagerProfession.WEAPONSMITH) {
            event.getTrades().get(3).add(sell(NarutoItems.KUNAI.get(), 4, 4, 8, 3));
            event.getTrades().get(3).add(sell(NarutoItems.SHURIKEN.get(), 4, 8, 8, 3));
            event.getTrades().get(4).add(sell(NarutoItems.TRAINING_WEIGHTS.get(), 12, 1, 2, 8));
        }
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(sell(NarutoItems.CHAKRA_SEAL.get(), 8, 4, 8, 4));
        event.getGenericTrades().add(sell(NarutoItems.MILITARY_PILLS.get(), 6, 3, 8, 4));
        event.getGenericTrades().add(sell(NarutoItems.RAMEN.get(), 4, 2, 8, 2));
        event.getGenericTrades().add(sell(NarutoItems.ONIGIRI.get(), 3, 4, 8, 2));
        event.getGenericTrades().add(sell(NarutoItems.DANGO.get(), 3, 4, 8, 2));
        event.getGenericTrades().add(sell(NarutoItems.SMOKE_BOMB.get(), 5, 4, 8, 3));
        event.getGenericTrades().add(sell(NarutoItems.TRAINING_WEIGHTS.get(), 10, 1, 2, 6));
        event.getGenericTrades().add(sell(NarutoItems.CHAKRA_COMPASS.get(), 12, 1, 2, 6));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_CHAKRA.get(), 24, 1, 2, 10));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_STAMINA.get(), 24, 1, 2, 10));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_VITALITY.get(), 32, 1, 1, 15));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_STRENGTH.get(), 32, 1, 1, 15));
        // Rare stock: scrolls the wandering trader only sometimes carries.
        event.getRareTrades().add(sell(NarutoItems.SCROLL_EIGHT_GATES.get(), 48, 1, 1, 20));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_SAGE_MODE.get(), 48, 1, 1, 20));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_KIRIN.get(), 64, 1, 1, 25));
        event.getRareTrades().add(sell(NarutoItems.SCROLL_KAMUI.get(), 64, 1, 1, 20));
    }

    /** One plain "so many emeralds for so many of an item" offer. */
    private static VillagerTrades.ItemListing sell(Item item, int emeralds, int count, int maxUses, int xp) {
        return (trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, emeralds),
                new ItemStack(item, count), maxUses, xp, PRICE_MULTIPLIER);
    }
}
