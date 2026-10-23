package com.sekwah.narutomod.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Switches the jutsu menu between reference and control panel.
 *
 * <p>By default only techniques that stay on - stances, the armed chakra shield and sage mode - can
 * be clicked in the menu, so a stray click cannot fire a one-shot cast. This command lets a player
 * opt out of that guard: with it on, left clicking any unlocked row casts the technique exactly as
 * its combo would, and right clicking switches the controllable ones off as before.</p>
 *
 * <p>It is deliberately not gated at permission level 2 like the {@code /naruto debug} tree: this
 * changes nothing about the world or progression, only how the player's own menu responds, so every
 * ninja may choose it. The flag is synced and saved with the rest of the ninja data.</p>
 */
public final class MenuClickCommand {

    private MenuClickCommand() {
    }

    /** The {@code menuclick} branch, hung off the {@code /naruto} root next to {@code debug}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("menuclick")
                .executes(MenuClickCommand::toggle)
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        INinjaData ninjaData = data(ctx);
        boolean enabled = !ninjaData.isMenuClickAll();
        ninjaData.setMenuClickAll(enabled);
        report(ctx, enabled);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean enabled)
            throws CommandSyntaxException {
        data(ctx).setMenuClickAll(enabled);
        report(ctx, enabled);
        return 1;
    }

    private static INinjaData data(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .orElseThrow(() -> new IllegalStateException("Player is missing ninja data"));
    }

    private static void report(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                ? "commands.narutomod.menuclick.on"
                : "commands.narutomod.menuclick.off"), false);
    }
}
