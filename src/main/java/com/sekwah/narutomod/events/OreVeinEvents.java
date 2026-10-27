package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.OreVeinMiningAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes {@link OreVeinMiningAbility} real: when the stance is on, breaking one ore pulls out the seam
 * it belongs to.
 *
 * <p>The first block is left to vanilla completely - the event is not cancelled, the ordinary swing
 * breaks it, the pickaxe wears and the drop falls. This hook only adds the rest of the vein behind
 * it, spending chakra per block and wearing the same pickaxe once per block. A caster whose pickaxe
 * cannot break the ore, whose chakra runs dry or who sheathes the tool mid-seam simply stops early
 * and leaves the rest of the vein in the ground.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OreVeinEvents {

    /** The tag the whole mod treats as ore, so modded ores are included. */
    private static final TagKey<Block> ORES = BlockTags.create(new ResourceLocation("forge", "ores"));

    /** The most blocks one broken ore may pull out, so a huge modded seam cannot empty a bar. */
    private static final int MAX_BLOCKS = 96;

    /** Chakra charged for every extra block that comes out of the ground. */
    private static final float PER_BLOCK = 3.0f;

    private static final int CHAKRA_REGEN_DELAY = 40;

    /** Set while this hook is already pulling a vein, so a re-entrant break cannot loop. */
    private static final String WORKING_TAG = "narutomod:vein_mining_working";

    private OreVeinEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }
        BlockState broken = event.getState();
        if (!broken.is(ORES)) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !OreVeinMiningAbility.isActive(ninjaData)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof PickaxeItem) || !tool.isCorrectToolForDrops(broken)) {
            return;
        }
        if (player.getPersistentData().getBoolean(WORKING_TAG)) {
            return;
        }

        List<BlockPos> vein = collectVein(level, event.getPos(), broken.getBlock(), tool);
        if (vein.size() <= 1) {
            return;
        }
        player.getPersistentData().putBoolean(WORKING_TAG, true);
        try {
            float perBlock = PER_BLOCK;
            for (BlockPos pos : vein) {
                if (pos.equals(event.getPos())) {
                    continue;
                }
                if (ninjaData.getChakra() < perBlock) {
                    break;
                }
                ItemStack held = player.getMainHandItem();
                BlockState state = level.getBlockState(pos);
                if (!(held.getItem() instanceof PickaxeItem) || !held.isCorrectToolForDrops(state)) {
                    break;
                }
                ninjaData.useChakra(perBlock, CHAKRA_REGEN_DELAY);
                level.destroyBlock(pos, true, player);
                held.mineBlock(level, state, pos, player);
            }
        } finally {
            player.getPersistentData().remove(WORKING_TAG);
        }
    }

    /**
     * Breadth-first walk of the seam starting at the broken block. Only blocks of the same kind that
     * the held pickaxe can actually break are followed, which is the same rule the swing itself uses.
     */
    private static List<BlockPos> collectVein(ServerLevel level, BlockPos origin, Block ore, ItemStack tool) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && vein.size() < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            vein.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() != ore || !tool.isCorrectToolForDrops(state)) {
                    continue;
                }
                queue.add(next);
            }
        }
        return vein;
    }
}
