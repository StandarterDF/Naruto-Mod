package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rewards for the two things a village asks a ninja to do about illagers: stop the patrol captain
 * before he reaches the walls, and break a raid.
 *
 * <p>Forge 47 has no {@code RaidEvent.Victory} (that is a NeoForge addition), so a won raid is found
 * by asking the level's {@code Raids} for the raid around each player once a second. A raid is paid
 * out once per player: the id of the last rewarded raid is written into the player's persistent data,
 * and a second look at the same raid is ignored. That is what stops a player from standing in a won
 * village and being paid every second.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaidEvents {

    /** How often victory is checked, in ticks. */
    private static final int CHECK_INTERVAL = 20;

    /** Squared distance from the village centre that still counts as taking part in the raid. */
    private static final int RAID_RANGE_SQR = 128 * 128;

    private static final String LAST_RAID_ID = "narutomod:last_raid_id";
    private static final String LAST_RAID_DIMENSION = "narutomod:last_raid_dimension";

    /** Checked on the server tick rather than on player ticks to keep one counter for every level. */
    private static int tickCounter;

    private RaidEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        if (!NarutoConfig.scrollLootEnabled) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkVictory(level, player);
            }
        }
    }

    private static void checkVictory(ServerLevel level, ServerPlayer player) {
        Raid raid = level.getRaids().getNearbyRaid(player.blockPosition(), RAID_RANGE_SQR);
        if (raid == null || !raid.isVictory() || !raid.isStarted()) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        String dimension = level.dimension().location().toString();
        if (tag.getInt(LAST_RAID_ID) == raid.getId()
                && tag.getString(LAST_RAID_DIMENSION).equals(dimension)) {
            return;
        }
        tag.putInt(LAST_RAID_ID, raid.getId());
        tag.putString(LAST_RAID_DIMENSION, dimension);
        giveScroll(player, pickVictoryScroll(player), "naruto.raid.victory");    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!NarutoConfig.scrollLootEnabled) {
            return;
        }
        if (!(event.getEntity() instanceof PatrollingMonster captain) || !captain.isPatrolLeader()) {
            return;
        }
        Player killer = killingPlayer(event);
        if (killer == null || killer.level().isClientSide) {
            return;
        }
        giveScroll(killer, pickCaptainScroll(killer), "naruto.raid.captain");
    }

    private static Item pickCaptainScroll(Player player) {
        int roll = player.getRandom().nextInt(10);
        if (roll < 6) {
            return NarutoItems.SCROLL_EARTH_WALL.get();
        }
        if (roll < 9) {
            return NarutoItems.SCROLL_RASENGAN.get();
        }
        return NarutoItems.SCROLL_KIRIN.get();
    }

    private static Item pickVictoryScroll(Player player) {
        Item[] scrolls = {
                NarutoItems.SCROLL_RASENSHURIKEN.get(),
                NarutoItems.SCROLL_EIGHT_GATES.get(),
                NarutoItems.SCROLL_KAMUI.get(),
                NarutoItems.SCROLL_SAGE_MODE.get()
        };
        return scrolls[player.getRandom().nextInt(scrolls.length)];
    }

    /** Hands over the scroll only to a ninja, and only announces it to them. */
    private static void giveScroll(Player player, Item scroll, String messageKey) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isNinjaModeEnabled()) {
                return;
            }
            ItemStack stack = new ItemStack(scroll);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.displayClientMessage(Component.translatable(messageKey, stack.getHoverName())
                    .withStyle(ChatFormatting.GOLD), false);
        });
    }

    /** @return the ninja credited with the kill, whether by blade, arrow or a clone. */
    private static Player killingPlayer(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) {
            return player;
        }
        Entity credit = event.getEntity().getKillCredit();
        return credit instanceof Player player ? player : null;
    }
}
