package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the ground around the caster and shakes the ore out of it.
 *
 * <p>It works off the {@code forge:ores} tag rather than a list of blocks, so anything the pack
 * calls an ore counts, modded or not. It is deliberately capped: a fixed radius and a fixed number
 * of blocks per cast, so it can be used next to a vein without the caster having to worry about
 * what is underneath them.</p>
 *
 * <p>It is a hand that digs, not a hand that ignores tools: the caster has to be holding a pickaxe,
 * and only the ores that pickaxe is actually good enough to break are taken, one use of the
 * pickaxe's durability per block. A stone pickaxe will shake the coal out of the ground and leave
 * the diamonds where they are.</p>
 */
public class OreExcavationJutsuAbility extends Ability {

    /** C V V C */
    public static final long COMBO = 1221L;

    /** How far, in blocks, the technique reaches. */
    private static final int RADIUS = 5;

    /** The most blocks one cast will break, however rich the ground is. */
    private static final int MAX_BLOCKS = 64;

    private static final float CHAKRA_COST = 25.0f;

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
        if (ninjaData.getChakra() < CHAKRA_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_COST, 20 * 2);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos centre = player.blockPosition();
        List<BlockPos> toBreak = new ArrayList<>();
        ItemStack tool = player.getMainHandItem();

        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS),
                centre.offset(RADIUS, RADIUS, RADIUS))) {
            if (toBreak.size() >= MAX_BLOCKS) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            // Only what this pickaxe would be allowed to break if it were swung by hand.
            if (state.is(ORES) && tool.isCorrectToolForDrops(state)) {
                toBreak.add(pos.immutable());
            }
        }

        // Broken after the scan so the list is not being changed while it is walked. The tool is
        // re-read every block: a pickaxe that snaps half way through takes the rest of the cast
        // with it.
        for (BlockPos pos : toBreak) {
            BlockState state = level.getBlockState(pos);
            ItemStack held = player.getMainHandItem();
            if (!held.isCorrectToolForDrops(state)) {
                continue;
            }
            level.destroyBlock(pos, true, player);
            held.mineBlock(level, state, pos, player);
        }
    }
}
