package com.sekwah.narutomod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sekwah.narutomod.abilities.Nature;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /naruto nature} - the one choice a ninja makes with their chakra paper.
 *
 * <p>It is deliberately a command rather than a crafted item: the paper is story, and asking the
 * player to carry it, click it and lose it would add a step without adding a decision. The choice is
 * made once and kept; asking again only shows what the paper already said.</p>
 */
public final class NatureCommand {

    private NatureCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("naruto")
                .then(Commands.literal("nature")
                        .executes(NatureCommand::show)
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (Nature nature : Nature.values()) {
                                        builder.suggest(nature.getSerializedName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(NatureCommand::set))));
    }

    private static int show(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        INinjaData data = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) {
            return 0;
        }
        String affinity = data.getNatureAffinity();
        if (affinity == null || affinity.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.narutomod.nature.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        Nature nature = Nature.byId(affinity);
        context.getSource().sendSuccess(() -> Component.translatable("commands.narutomod.nature.show",
                Component.translatable(nature == null ? "naruto.nature.none" : nature.translationKey())
                        .withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Nature nature = Nature.byId(StringArgumentType.getString(context, "element"));
        if (nature == null) {
            context.getSource().sendFailure(Component.translatable("commands.narutomod.nature.invalid"));
            return 0;
        }
        INinjaData data = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) {
            return 0;
        }
        String affinity = data.getNatureAffinity();
        if (affinity != null && !affinity.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.narutomod.nature.already",
                    Component.translatable(Nature.byId(affinity) == null
                            ? "naruto.nature.none" : Nature.byId(affinity).translationKey())));
            return 0;
        }
        data.setNatureAffinity(nature.getSerializedName());
        context.getSource().sendSuccess(() -> Component.translatable("commands.narutomod.nature.set",
                Component.translatable(nature.translationKey()).withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }
}
