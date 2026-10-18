package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.jutsus.WideDigAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of the wide dig stance an ability cannot reach: actually breaking the extra blocks.
 *
 * <p>A swing is widened once, around the block that was broken, into the three by three plane
 * perpendicular to the face that was hit. The plane is walked in the level rather than in the
 * technique so the caster keeps steering the tunnel with their own pick. Every extra block is paid
 * for one at a time - chakra and tool durability both - and anything the tool could not have broken
 * by hand is left where it is. The whole batch is wrapped in a re-entry guard: breaking a block can
 * itself report a break, and without the guard one swing would eat the world.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WideDigEvents {

    /** A hard ceiling on how many blocks one swing may take, however the plane is shaped later. */
    private static final int MAX_EXTRA_BLOCKS = 26;

    /** How far the crosshair ray is followed to find the face that was hit. */
    private static final double FACE_REACH = 5.5d;

    /** Guards against the extra breaks reporting themselves as new swings. */
    private static boolean breakingExtras;

    private WideDigEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (breakingExtras || event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                widen(level, player, ninjaData, event.getPos()));
    }

    private static void widen(ServerLevel level, Player player, INinjaData ninjaData, BlockPos origin) {
        if (!ninjaData.isNinjaModeEnabled() || !WideDigAbility.isActive(ninjaData)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }
        // The face has already been chosen by the client when the block went; the server is not
        // told which one it was, so the crosshair is asked first and the caster's position relative
        // to the block is the fallback. Either way the plane is perpendicular to that face.
        Direction face = faceOf(player, origin);
        List<BlockPos> extras = plane(origin, face.getAxis());
        WideDigAbility ability = NarutoAbilities.WIDE_DIG.get();
        float perBlock = ability.chakraPerExtraBlock(ninjaData);

        breakingExtras = true;
        try {
            int broken = 0;
            for (BlockPos pos : extras) {
                if (broken >= MAX_EXTRA_BLOCKS) {
                    break;
                }
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    break;
                }
                BlockState state = level.getBlockState(pos);
                if (!canBreak(held, state, level, pos)) {
                    continue;
                }
                if (ninjaData.getChakra() < perBlock) {
                    // The rest of the plane stays in the ground, to be finished with a second swing.
                    break;
                }
                // Drops first, then the tool: a pickaxe that snaps on this block still took it.
                // The chakra is only taken for a block that actually came out of the ground, and the
                // mastery is credited the same way - per block, never per tick.
                if (level.destroyBlock(pos, true, player)) {
                    ninjaData.useChakra(perBlock, WideDigAbility.REGEN_DELAY_PER_BLOCK);
                    held.mineBlock(level, state, pos, player);
                    ability.creditBrokenBlock(player, ninjaData);
                    broken++;
                }
            }
        } finally {
            breakingExtras = false;
        }
    }

    /**
     * Whether the caster could have taken this block with the tool they are holding. It has to be a
     * block that is there to be dug at all, and either one the tool is the right kind for or one that
     * needs no particular tool - dirt, gravel and leaves come out behind a pickaxe even though a
     * pickaxe is not "for" them. Anything harder than the tier can manage stays put.
     */
    private static boolean canBreak(ItemStack tool, BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0f) {
            return false;
        }
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /**
     * The six faces of the plane's orientation. The crosshair is used when it agrees with the block
     * that actually broke; otherwise the largest offset between the caster's eyes and the block is
     * taken, which is the side they are standing on.
     */
    private static Direction faceOf(Player player, BlockPos pos) {
        BlockHitResult looked = Targeting.lookedAtBlock(player, FACE_REACH);
        if (looked != null && looked.getBlockPos().equals(pos)) {
            return looked.getDirection();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 centre = Vec3.atCenterOf(pos);
        double dx = eye.x - centre.x;
        double dy = eye.y - centre.y;
        double dz = eye.z - centre.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy >= 0d ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return dx >= 0d ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0d ? Direction.SOUTH : Direction.NORTH;
    }

    /** The eight neighbours of one block in the plane perpendicular to the given axis. */
    private static List<BlockPos> plane(BlockPos centre, Direction.Axis axis) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                positions.add(switch (axis) {
                    case X -> centre.offset(0, a, b);
                    case Y -> centre.offset(a, 0, b);
                    case Z -> centre.offset(a, b, 0);
                });
            }
        }
        return positions;
    }
}
