package com.sekwah.narutomod.ranks;

import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.triggers.NarutoTriggers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Works out and announces promotions.
 *
 * <p>The check is cheap and only looks at counters the ninja already keeps, so it runs on the normal
 * player tick, throttled to once a second. A promotion is one-way: dying or forgetting a technique
 * does not take a rank back, which is how a village would treat it and keeps the advancement chain
 * from flickering.</p>
 */
public final class NinjaRanks {

    /** The four bosses that count as milestones, matching {@code events/BossMilestoneEvents}. */
    private static final String[] MILESTONES = {"ender_dragon", "wither", "elder_guardian", "warden"};

    /** Promotions are checked once a second; nothing about them is urgent. */
    public static final int CHECK_INTERVAL = 40;

    private NinjaRanks() {
    }

    /**
     * Called the moment a player becomes a ninja: opens the advancement root, hands out the first
     * rank and lets the caller give the handbook.
     */
    public static void onAwakened(ServerPlayer player, INinjaData ninjaData) {
        NarutoTriggers.NINJA_AWAKENED.trigger(player);
        if (!NarutoConfig.ninjaRanksEnabled) {
            return;
        }
        if (ninjaData.getNinjaRank().isEmpty()) {
            ninjaData.setNinjaRank(NinjaRank.GENIN.id());
            NarutoTriggers.NINJA_RANK.trigger(player, NinjaRank.GENIN.id());
        }
    }

    public static void tick(ServerPlayer player, INinjaData ninjaData) {
        if (!NarutoConfig.ninjaRanksEnabled || !ninjaData.isNinjaModeEnabled()) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        if (ninjaData.getNinjaRank().isEmpty()) {
            // A save from before ranks existed (or a fresh awakening that skipped the packet):
            // start at genin and open the root advancement.
            onAwakened(player, ninjaData);
            return;
        }
        int skills = ninjaData.getSkillData().getUnlocked().size();
        int bosses = bossMilestones(ninjaData);
        NinjaRank target = NinjaRank.GENIN;
        for (NinjaRank rank : NinjaRank.values()) {
            if (rank.isReached(skills, bosses)) {
                target = rank;
            }
        }
        NinjaRank current = NinjaRank.byId(ninjaData.getNinjaRank());
        if (target.ordinal() <= current.ordinal()) {
            return;
        }
        // Fire every rank passed on the way, so a caster who jumped two steps still opens both
        // advancements rather than only the last one.
        for (NinjaRank rank : NinjaRank.values()) {
            if (rank.ordinal() > current.ordinal() && rank.ordinal() <= target.ordinal()) {
                NarutoTriggers.NINJA_RANK.trigger(player, rank.id());
            }
        }
        ninjaData.setNinjaRank(target.id());
        player.displayClientMessage(Component.translatable("naruto.rank.up",
                        Component.translatable(target.translationKey()).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.YELLOW), false);
    }

    private static int bossMilestones(INinjaData ninjaData) {
        int count = 0;
        for (String milestone : MILESTONES) {
            if (ninjaData.hasClaimedBossMilestone(milestone)) {
                count++;
            }
        }
        return count;
    }
}
