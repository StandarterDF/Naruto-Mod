package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Jougan's cut turned on a construction rather than on a body.
 *
 * <p>The eye sees the seams in what people build as clearly as it sees them in chakra. A look at a
 * wall finds the plane of its face, and the same cut that would sever a tenketsu takes a three by
 * three panel out of the material in one motion. Blocks that are not the same material are left
 * alone, so the cut follows what was actually built rather than a neat square of whatever stood in
 * the way.</p>
 *
 * <p>The panel drops what it destroys, which is why the technique respects the world's griefing rule:
 * a server that has told mobs not to alter the world has told players the same thing about a look
 * that rearranges it.</p>
 */
public class StructureCutAbility extends Ability implements Ability.Cooldown {

    /** A clean cut through stone carries as far as a shout. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B V V C V B - the Jougan (B V V C) and two more keys. */
    public static final long COMBO = 3211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "structure_cut");

    /** How far down the look ray the cut can be made. Walls stop it, as they stop every eye. */
    private static final double RANGE = 8.0d;

    /** What it costs and how long before the eye can cut again. */
    private static final float COST = 35.0f;
    private static final int COOLDOWN = 200;

    /** Half-width of the panel, so the cut is three blocks across each way. */
    private static final int PANEL_RADIUS = 1;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (player.level() instanceof ServerLevel level
                && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gamerule",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        boolean cuttable = hit != null && player.level() instanceof ServerLevel level
                && isCuttable(level, player, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
        if (!cuttable) {
            player.displayClientMessage(Component.translatable("jutsu.fail.structure_cut.noblock",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return;
        }
        BlockPos origin = hit.getBlockPos();
        BlockState base = level.getBlockState(origin);
        if (base.isAir()) {
            return;
        }

        List<BlockPos> panel = panel(origin, player.getLookAngle(), base, level, player);
        if (panel.isEmpty()) {
            return;
        }
        for (BlockPos pos : panel) {
            Vec3 centre = Vec3.atCenterOf(pos);
            level.destroyBlock(pos, true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z,
                    4, 0.25d, 0.25d, 0.25d, 0.02d);
        }
        Vec3 centre = Vec3.atCenterOf(origin).add(0.0d, 0.5d, 0.0d);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y, centre.z,
                3, 0.6d, 0.6d, 0.6d, 0.0d);
    }

    /**
     * The blocks of the panel: a three by three square in the plane whose normal is the dominant
     * axis of the caster's look, keeping only blocks of the same material that may still be broken.
     */
    private static List<BlockPos> panel(BlockPos origin, Vec3 look, BlockState base,
                                        ServerLevel level, Player player) {
        Direction.Axis axis = dominantAxis(look);
        List<BlockPos> found = new ArrayList<>(9);
        for (int u = -PANEL_RADIUS; u <= PANEL_RADIUS; u++) {
            for (int v = -PANEL_RADIUS; v <= PANEL_RADIUS; v++) {
                BlockPos pos = switch (axis) {
                    case X -> origin.offset(0, u, v);
                    case Y -> origin.offset(u, 0, v);
                    case Z -> origin.offset(u, v, 0);
                };
                BlockState state = level.getBlockState(pos);
                // A container or a machine is not a wall: cutting it would spill its contents into
                // the panel, and the technique is aimed at construction rather than at storage.
                if (state.getBlock() != base.getBlock()
                        || state.hasBlockEntity()
                        || !isCuttable(level, player, pos, state)) {
                    continue;
                }
                found.add(pos);
            }
        }
        return found;
    }

    /** @return whether the block is something the cut may destroy and the caster may touch. */
    private static boolean isCuttable(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0f) {
            return false;
        }
        return level.mayInteract(player, pos);
    }

    /** The axis the look ray is most aligned with, which is the normal of the panel. */
    private static Direction.Axis dominantAxis(Vec3 look) {
        double x = Math.abs(look.x);
        double y = Math.abs(look.y);
        double z = Math.abs(look.z);
        if (x >= y && x >= z) {
            return Direction.Axis.X;
        }
        return y >= z ? Direction.Axis.Y : Direction.Axis.Z;
    }
}
