package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.ranks.NinjaRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The ninja's rank in front of their name in chat.
 *
 * <p>A rank that only exists on an advancement screen is a private trophy. Putting it in chat is the
 * cheapest way to make it part of how a server reads its players, and it needs no custom GUI, no
 * scoreboard upkeep and no packet: the chat event already carries the whole message, so the prefix
 * is just put in front of it.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class RankChatEvents {

    private RankChatEvents() {
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (!NarutoConfig.ninjaRanksEnabled) {
            return;
        }
        event.getPlayer().getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            NinjaRank rank = NinjaRank.byId(data.getNinjaRank());
            MutableComponent prefix = Component.literal("[")
                    .append(Component.translatable(rank.translationKey()))
                    .append(Component.literal("] "))
                    .withStyle(ChatFormatting.GRAY);
            event.setMessage(prefix.append(event.getMessage()));
        });
    }
}
