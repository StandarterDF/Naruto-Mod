package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.util.ExperienceUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Buy one node of the skill tree with experience points. The server is the only one that ever spends
 * points or writes a node down; the screen just asks, and a node it should not have asked for is
 * simply refused here.
 */
public class ServerUnlockSkillPacket {

    private final String skill;

    public ServerUnlockSkillPacket(String skill) {
        this.skill = skill;
    }

    public static void encode(ServerUnlockSkillPacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeUtf(msg.skill);
    }

    public static ServerUnlockSkillPacket decode(FriendlyByteBuf inBuffer) {
        return new ServerUnlockSkillPacket(inBuffer.readUtf());
    }

    public static class Handler {
        public static void handle(ServerUnlockSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                    Skill skill = SkillTree.byPath(msg.skill);
                    if (skill == null) {
                        return;
                    }
                    if (!ninjaData.isNinjaModeEnabled()) {
                        player.displayClientMessage(Component.translatable("jutsu.not_a_ninja")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    if (SkillTree.isUnlocked(ninjaData.getSkillData().getUnlocked(), skill)) {
                        return;
                    }
                    if (!SkillTree.prerequisitesMet(skill, ninjaData.getSkillData().getUnlocked())) {
                        player.displayClientMessage(Component.translatable("naruto.skill.fail.prereq",
                                Component.translatable(skill.nameKey())).withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    int held = ExperienceUtils.currentPoints(player);
                    if (held < skill.cost()) {
                        player.displayClientMessage(Component.translatable("naruto.skill.fail.levels",
                                Component.literal(String.format("%,d", skill.cost() - held))
                                        .withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    buy(player, ninjaData, skill);
                });
            });
            ctx.get().setPacketHandled(true);
        }

        private static void buy(ServerPlayer player, INinjaData ninjaData, Skill skill) {
            player.giveExperiencePoints(-skill.cost());
            ninjaData.getSkillData().unlock(skill.id());
            player.level().playSound(null, player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            player.displayClientMessage(Component.translatable("naruto.skill.unlocked",
                    Component.translatable(skill.nameKey()).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }
}
