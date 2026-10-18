package com.sekwah.narutomod.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.stream.Stream;

public class JutsuCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> jutsu = Commands.literal("jutsu")
                .then(Commands.literal("list").executes(ctx -> {
                    Stream<Map.Entry<ResourceKey<Ability>, Ability>> abilities = NarutoRegistries.ABILITIES.getEntries().stream().filter(entry -> entry.getValue().defaultCombo() != -1);
                    CommandSourceStack source = ctx.getSource();

                    source.sendSuccess(Component::empty,false);

                    abilities.forEach(entry -> {
                        var comboComponent = Component.literal("");
                        for (char comboKey: String.valueOf(entry.getValue().defaultCombo()).toCharArray()) {
                            comboComponent.append(" ").append(Component.keybind("naruto.keys.key" + comboKey));
                        }

                        source.sendSuccess(() -> Component.translatable(entry.getKey().location().toString())
                                .append(" -")
                                .append(comboComponent), false);
                    });
                    source.sendSuccess(() -> Component.translatable("naruto.keys.leap")
                            .append(" - ")
                            .append(Component.keybind("naruto.keys.leap")), false);
                    source.sendSuccess(Component::empty, false);

                    return Command.SINGLE_SUCCESS;
                }));

        // Debug helper: wipe learned technique progress. Cheat/op only (permission level 2), since it
        // is a testing tool and no ordinary player should be able to throw their mastery away.
        jutsu.then(Commands.literal("reset")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                            .ifPresent(data -> data.getMasteryData().reset());
                    ctx.getSource().sendSuccess(() ->
                            Component.translatable("commands.narutomod.mastery.reset.all"), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("technique", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                NarutoRegistries.ABILITIES.getKeys(), builder))
                        .executes(ctx -> {
                            ResourceLocation technique = ResourceLocationArgument.getId(ctx, "technique");
                            var player = ctx.getSource().getPlayerOrException();
                            player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                                    .ifPresent(data -> data.getMasteryData().reset(technique));
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "commands.narutomod.mastery.reset.one", technique.toString()), true);
                            return Command.SINGLE_SUCCESS;
                        })));

        dispatcher.register(jutsu);
    }

}
