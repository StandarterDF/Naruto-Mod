package com.sekwah.narutomod.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/**
 * Operator-only testing commands, under {@code /naruto debug}.
 *
 * <p>The mod has a lot of progression - a 195 node skill tree, scrolls, per-technique mastery - and
 * checking a single late-game technique in a fresh world used to mean grinding for it first. These
 * commands let the person testing (or the server owner on their own world) skip that: hand out
 * points, open the whole tree, set mastery, refill the pools and drop every cooldown.</p>
 *
 * <p>Everything here is gated at permission level 2, which is the vanilla "cheat/op" level. That is
 * deliberate: these are world-editing shortcuts, not gameplay, so a normal ninja must not be able to
 * walk up and open the entire tree. There is no other check - an operator is trusted with their own
 * world, and the commands are exactly as sharp as {@code /give} or {@code /xp}.</p>
 */
public final class NarutoDebugCommand {

    /** Casts that put a technique at effectively full mastery on the default curve (0.998+). */
    private static final int MAX_CASTS = 1000;

    private NarutoDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
                .requires(source -> source.hasPermission(2));

        debug.then(Commands.literal("points")
                .then(Commands.literal("all").executes(NarutoDebugCommand::grantAllPoints))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> grantPoints(ctx, IntegerArgumentType.getInteger(ctx, "amount")))));

        debug.then(Commands.literal("unlock")
                .then(Commands.literal("all").executes(NarutoDebugCommand::unlockAll))
                .then(Commands.argument("node", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Skill.all().stream().map(Skill::path), builder))
                        .executes(NarutoDebugCommand::unlockOne)));

        debug.then(Commands.literal("resetskills").executes(NarutoDebugCommand::resetSkills));

        debug.then(Commands.literal("mastery")
                .then(Commands.literal("all")
                        .then(Commands.literal("max").executes(ctx -> setAllMastery(ctx, MAX_CASTS))))
                .then(Commands.argument("technique", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                NarutoRegistries.ABILITIES.getKeys(), builder))
                        .then(Commands.literal("max")
                                .executes(ctx -> setMastery(ctx, MAX_CASTS)))
                        .then(Commands.argument("casts", IntegerArgumentType.integer(0))
                                .executes(ctx -> setMastery(ctx,
                                        IntegerArgumentType.getInteger(ctx, "casts"))))));

        // Pace control for testing: the real curve is slow on purpose, which makes it hard to judge
        // what a technique feels like at the far end without playing for hours first.
        debug.then(Commands.literal("masteryspeed")
                .executes(NarutoDebugCommand::showMasterySpeed)
                .then(Commands.literal("reset").executes(ctx -> setMasterySpeed(ctx, 1.0f)))
                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.0f, 1000.0f))
                        .executes(ctx -> setMasterySpeed(ctx,
                                FloatArgumentType.getFloat(ctx, "multiplier")))));

        debug.then(Commands.literal("refill").executes(NarutoDebugCommand::refill));

        debug.then(Commands.literal("ninja").executes(NarutoDebugCommand::toggleNinja));

        debug.then(Commands.literal("scrolls").executes(NarutoDebugCommand::learnScrolls));

        debug.then(Commands.literal("cooldowns").executes(NarutoDebugCommand::clearCooldowns));

        dispatcher.register(Commands.literal("naruto").then(debug).then(MenuClickCommand.node()));
    }

    private static int grantPoints(CommandContext<CommandSourceStack> ctx, int amount)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        data.getSkillData().addPoints(amount);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.points.added", amount), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int grantAllPoints(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        int total = totalTreeCost();
        INinjaData data = data(ctx);
        data.getSkillData().addPoints(total);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.points.all", total), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unlockOne(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        String path = StringArgumentType.getString(ctx, "node");
        Skill skill = SkillTree.byPath(path);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "commands.narutomod.debug.unlock.unknown", path));
            return 0;
        }
        data(ctx).getSkillData().unlock(skill.id());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.unlock.one", path), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        List<Skill> skills = Skill.all();
        for (Skill skill : skills) {
            data.getSkillData().unlock(skill.id());
        }
        int scrolls = learnEveryScroll(data);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.unlock.all", skills.size(), scrolls), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetSkills(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        data(ctx).getSkillData().reset();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.reset.skills"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMastery(CommandContext<CommandSourceStack> ctx, int casts)
            throws CommandSyntaxException {
        ResourceLocation technique = ResourceLocationArgument.getId(ctx, "technique");
        data(ctx).getMasteryData().setCasts(technique, casts);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.mastery.one", technique.toString(), casts), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setAllMastery(CommandContext<CommandSourceStack> ctx, int casts)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        Set<ResourceLocation> techniques = NarutoRegistries.ABILITIES.getKeys();
        for (ResourceLocation technique : techniques) {
            data.getMasteryData().setCasts(technique, casts);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.mastery.all", techniques.size(), casts), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int showMasterySpeed(CommandContext<CommandSourceStack> ctx) {
        float speed = Mastery.gainMultiplier();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.masteryspeed.show", formatSpeed(speed)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMasterySpeed(CommandContext<CommandSourceStack> ctx, float multiplier) {
        Mastery.setGainMultiplier(multiplier);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.masteryspeed.set", formatSpeed(multiplier)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Whole multipliers read as "3", fractions as "0.25", without locale dependent decimal commas. */
    private static String formatSpeed(float multiplier) {
        if (multiplier == Math.round(multiplier)) {
            return Integer.toString(Math.round(multiplier));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", multiplier);
    }

    private static int refill(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        data.setChakra(data.getMaxChakra());
        data.setStamina(data.getMaxStamina());
        data.setSeninMana(data.getMaxSeninMana());
        // The shield is a pool of armour points spent before health, and the gates drain health for
        // as long as they stay open; neither is "full" after a refill, so both are cleared instead.
        data.setShieldPoints(0f);
        data.setGateCount(0);
        data.setGateBacklashCarry(0f);
        data.setSecondBreathArmed(false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.refill"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleNinja(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        boolean enable = !data.isNinjaModeEnabled();
        data.setIsNinja(enable);
        ctx.getSource().sendSuccess(() -> Component.translatable(enable
                ? "commands.narutomod.debug.ninja.on"
                : "commands.narutomod.debug.ninja.off"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int learnScrolls(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        int learned = learnEveryScroll(data(ctx));
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.scrolls", learned), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearCooldowns(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        INinjaData data = data(ctx);
        int cleared = data.getCooldownEvents().size();
        data.getCooldownEvents().clear();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.narutomod.debug.cooldowns", cleared), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Teaches every scroll there is: the ones the tree wants for its deepest nodes, plus the
     * scroll-only techniques that never appear on the tree at all ({@code kirin}).
     */
    private static int learnEveryScroll(INinjaData data) {
        int learned = 0;
        for (Skill skill : Skill.all()) {
            if (skill.requiresScroll() && data.getSkillData().learnScroll(skill.scrollKnowledge())) {
                learned++;
            }
        }
        // Scroll-only techniques have no node to ask for them, so they are named here. This is the
        // same set the ability gate checks, see AbilityUnlocks#SCROLL_ONLY.
        if (data.getSkillData().learnScroll(new ResourceLocation("narutomod", "kirin"))) {
            learned++;
        }
        return learned;
    }

    /** The full price of the tree, so {@code points all} is enough even from an empty account. */
    private static int totalTreeCost() {
        int total = 0;
        for (Skill skill : Skill.all()) {
            total += skill.cost();
        }
        return total;
    }

    private static INinjaData data(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .orElseThrow(() -> new IllegalStateException("Player is missing ninja data"));
    }
}
