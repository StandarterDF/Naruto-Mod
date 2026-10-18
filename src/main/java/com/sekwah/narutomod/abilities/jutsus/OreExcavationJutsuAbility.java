package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens the ground around the caster and shakes the ore out of it.
 *
 * <p>It works off the {@code forge:ores} tag rather than a list of blocks, so anything the pack
 * calls an ore counts, modded or not. Every ore in reach is followed out along the vein it belongs
 * to, block by connected block, so a cast takes the whole seam rather than the handful of blocks
 * that happened to be in the scanned box - a vein that wanders out of the scan is still taken,
 * because the walk follows the ore, not the box.</p>
 *
 * <p>It is a hand that digs, not a hand that ignores tools: the caster has to be holding a pickaxe,
 * and only the ores that pickaxe is actually good enough to break are taken, one use of the
 * pickaxe's durability per block. A stone pickaxe will shake the coal out of the ground and leave
 * the diamonds where they are.</p>
 *
 * <p>It is priced like work rather than like a spell. Turning the technique on costs
 * {@link #MIN_COST}, and then every block that actually comes out is charged
 * {@link #PER_BLOCK}: a caster who finds a rich seam pays for it and a caster who finds nothing has
 * only paid for the attempt. Run out of chakra half way and the rest of the vein stays in the
 * ground, to be finished with a second cast.</p>
 */
public class OreExcavationJutsuAbility extends Ability {

    /** C V V C */
    public static final long COMBO = 1221L;

    /** How far, in blocks, the technique reaches for the first block of a vein. */
    private static final int RADIUS = 5;

    /** The most blocks one cast will break, however rich the ground is. */
    private static final int MAX_BLOCKS = 128;

    /** Chakra to turn the technique on, before any block is paid for. */
    private static final float MIN_COST = 10.0f;

    /** Chakra charged for every block that actually comes out of the ground. */
    private static final float PER_BLOCK = 4.0f;

    private static final int CHAKRA_REGEN_DELAY = 20 * 2;

    private static final TagKey<Block> ORES = BlockTags.create(new ResourceLocation("forge", "ores"));

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // The technique borrows the caster's arm, so the arm has to be holding a pickaxe. Refusing
        // here rather than in performServer means a caster with the wrong tool is not charged.
        if (!(player.getMainHandItem().getItem() instanceof PickaxeItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.needpickaxe",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        float minCost = this.masteryCost(ninjaData, MIN_COST);
        if (ninjaData.getChakra() < minCost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        // Only the attempt is paid for here; the blocks charge themselves as they come out.
        ninjaData.useChakra(minCost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> toBreak = this.collectVeins(level, player, player.getMainHandItem());

        float perBlock = this.masteryCost(ninjaData, PER_BLOCK);
        // Broken after the scan so the world is not being changed while it is walked, and paid for
        // block by block. The pickaxe is re-read every block: one that snaps half way through takes
        // the rest of the cast with it, and so does an empty chakra bar.
        for (BlockPos pos : toBreak) {
            BlockState state = level.getBlockState(pos);
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof PickaxeItem) || !held.isCorrectToolForDrops(state)) {
                break;
            }
            if (ninjaData.getChakra() < perBlock) {
                break;
            }
            ninjaData.useChakra(perBlock, CHAKRA_REGEN_DELAY);
            level.destroyBlock(pos, true, player);
            held.mineBlock(level, state, pos, player);
        }
    }

    /**
     * Finds every vein that starts inside the reach of the caster, walking each one out block by
     * connected block, and puts the vein the caster is actually looking at at the very front of the
     * list.
     *
     * <p>The crosshair sorts goes first because a seam can run on well past a hundred blocks, and
     * without it the cast would eat a caster's chakra while the block they were aiming at sat
     * untouched somewhere behind the work. Breaking the looked-at block first means a cast is always
     * visibly doing something, whatever else the vein happens to hold.</p>
     *
     * <p>A "vein" is a run of the same ore block touching each other, which is how ores generate:
     * a diamond vein does not turn into coal half way. Following the block rather than the tag is
     * what keeps two unrelated seams that happen to touch from being treated as one.</p>
     */
    private List<BlockPos> collectVeins(ServerLevel level, Player player, ItemStack tool) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        // The block under the crosshair and its vein go first, so the target always comes out first.
        BlockHitResult looked = Targeting.lookedAtBlock(player, RADIUS);
        if (looked != null) {
            this.followVein(level, looked.getBlockPos(), tool, vein, seen);
        }

        BlockPos centre = player.blockPosition();
        for (BlockPos seed : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS),
                centre.offset(RADIUS, RADIUS, RADIUS))) {
            if (vein.size() >= MAX_BLOCKS) {
                break;
            }
            this.followVein(level, seed.immutable(), tool, vein, seen);
        }
        return vein;
    }

    /**
     * Walks one vein out from {@code seed}, breadth-first, so the seed itself is the first block
     * added and the rest follow in rings behind it. Does nothing unless the seed really is an ore
     * this pickaxe is allowed to break.
     */
    private void followVein(ServerLevel level, BlockPos seed, ItemStack tool,
                            List<BlockPos> vein, Set<BlockPos> seen) {
        if (vein.size() >= MAX_BLOCKS || seen.contains(seed)) {
            return;
        }
        BlockState seedState = level.getBlockState(seed);
        // Only what this pickaxe would be allowed to break if it were swung by hand.
        if (!seedState.is(ORES) || !tool.isCorrectToolForDrops(seedState)) {
            return;
        }
        Block ore = seedState.getBlock();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty() && vein.size() < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            vein.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.contains(next)) {
                    continue;
                }
                seen.add(next);
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() != ore || !tool.isCorrectToolForDrops(nextState)) {
                    continue;
                }
                queue.add(next);
            }
        }
    }
}
