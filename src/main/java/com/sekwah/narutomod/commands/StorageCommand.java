package com.sekwah.narutomod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.menu.SupplyChestContainer;
import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * {@code /naruto storage} - opens the travelling supply chest without the technique.
 *
 * <p>The chest is the worker clones' drop-off and lives in the ninja's own data rather than in a
 * block, so there is no reason the player should have to remember a combo to look inside it. The
 * command opens the same menu the technique does, which means items can be put in and taken out
 * exactly as usual. An operator may open somebody else's chest to inspect or empty it.</p>
 */
public final class StorageCommand {

    private StorageCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("naruto")
                .then(Commands.literal("storage")
                        .executes(context -> open(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> open(context, EntityArgument.getPlayer(context, "player"))))));
    }

    private static int open(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        INinjaData data = target.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) {
            return 0;
        }
        SupplyChestContainer chest = data.getSupplyChest();
        NetworkHooks.openScreen(target, new SimpleMenuProvider(
                        (id, inventory, player) -> new SupplyChestMenu(id, inventory, chest),
                        Component.translatable("narutomod:storage_chest")),
                buffer -> buffer.writeVarInt(chest.getContainerSize()));
        context.getSource().sendSuccess(() -> Component.translatable("commands.narutomod.storage.opened",
                target.getDisplayName()), false);
        return 1;
    }
}
