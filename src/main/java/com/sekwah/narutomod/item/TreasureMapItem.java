package com.sekwah.narutomod.item;

import com.sekwah.narutomod.world.NarutoWorldGen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;
import java.util.List;


/**
 * A scrap of someone else's map, pointing at the nearest place worth robbing.
 *
 * <p>It does not draw a map of its own: it reads the world's structure index, picks the nearest site
 * of one kind and hands the player a real vanilla filled map with a target marker on it. That means
 * a map found in a chest is always useful: villages, mineshafts, shipwrecks and ocean ruins are
 * already out there, and so are the mod's own sacred trees and ruins.</p>
 *
 * <p>Finding a structure walks the world's structure placement index, so it is done only on use, on
 * the server, and a map that finds nothing is not consumed.</p>
 */
public class TreasureMapItem extends Item {

    /** How far the search looks, in chunks. */
    private static final int SEARCH_RADIUS_CHUNKS = 120;

    /** The kinds of places a scrap can lead to, each with the name shown on the map. */
    private static final Kind[] KINDS = {
            new Kind(StructureTags.VILLAGE, "village", MapDecoration.Type.TARGET_X),
            new Kind(StructureTags.MINESHAFT, "mineshaft", MapDecoration.Type.TARGET_POINT),
            new Kind(StructureTags.SHIPWRECK, "shipwreck", MapDecoration.Type.TARGET_X),
            new Kind(StructureTags.RUINED_PORTAL, "portal", MapDecoration.Type.TARGET_POINT),
            new Kind(StructureTags.OCEAN_RUIN, "ocean_ruin", MapDecoration.Type.TARGET_POINT),
            new Kind(NarutoWorldGen.TREASURE_MAP_TARGETS, "hidden_site", MapDecoration.Type.TARGET_X)
    };

    public TreasureMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        Kind kind = KINDS[player.getRandom().nextInt(KINDS.length)];
        BlockPos target = serverLevel.findNearestMapStructure(kind.tag(), player.blockPosition(),
                SEARCH_RADIUS_CHUNKS, false);
        if (target == null) {
            player.displayClientMessage(Component.translatable("naruto.map.none",
                    Component.translatable("naruto.map.type." + kind.id()).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(held);
        }
        ItemStack map = MapItem.create(serverLevel, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItemSavedData.addTargetDecoration(map, target, "+", kind.marker());
        map.setHoverName(Component.translatable("naruto.map.found",
                        Component.translatable("naruto.map.type." + kind.id()))
                .withStyle(ChatFormatting.GOLD));
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        held.shrink(1);
        return InteractionResultHolder.sidedSuccess(held, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("naruto.map.hint").withStyle(ChatFormatting.GRAY));
    }

    private record Kind(TagKey<Structure> tag, String id, MapDecoration.Type marker) {
    }
}
