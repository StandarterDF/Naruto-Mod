package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.SkillPoints;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Exchange experience for skill points.
 *
 * <p>The client asks for a number of points - its buttons offer one, ten, or everything affordable -
 * and the server is the only side that moves either currency. A request larger than the experience on
 * hand is trimmed down to what is actually affordable rather than refused, so a stale button (the
 * caster spent experience in the same tick) still buys what it can instead of failing outright.</p>
 */
public class ServerBuySkillPointsPacket {

    /** The amount asked for, or a non-positive value for "as many as the experience on hand affords". */
    private final int requested;

    public ServerBuySkillPointsPacket(int requested) {
        this.requested = requested;
    }

    public static void encode(ServerBuySkillPointsPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeVarInt(msg.requested);
    }

    public static ServerBuySkillPointsPacket decode(FriendlyByteBuf inBuffer) {
        return new ServerBuySkillPointsPacket(inBuffer.readVarInt());
    }

    public static class Handler {
        public static void handle(ServerBuySkillPointsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                    if (!ninjaData.isNinjaModeEnabled()) {
                        return;
                    }
                    int affordable = SkillPoints.affordable(player);
                    int wanted = msg.requested <= 0 ? affordable
                            : Math.min(msg.requested, SkillPoints.MAX_PURCHASE);
                    int bought = Math.min(wanted, affordable);
                    if (bought <= 0) {
                        player.displayClientMessage(Component.translatable("naruto.skill.fail.experience",
                                Component.literal(String.format("%,d", SkillPoints.XP_PER_POINT))
                                        .withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    player.giveExperiencePoints(-SkillPoints.exchangeCost(bought));
                    ninjaData.getSkillData().addPoints(bought);
                    player.level().playSound(null, player, SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS, 0.7f, 1.2f);
                    player.displayClientMessage(Component.translatable("naruto.skill.points.bought",
                            Component.literal(Integer.toString(bought)).withStyle(ChatFormatting.GOLD),
                            Component.literal(String.format("%,d", SkillPoints.exchangeCost(bought)))
                                    .withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GREEN), true);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
