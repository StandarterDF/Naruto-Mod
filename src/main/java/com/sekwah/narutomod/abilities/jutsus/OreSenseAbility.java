package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundOreSensePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A pulse of chakra through the ground: for a few seconds the caster sees the ore around them
 * through the rock.
 *
 * <p>This is not the chakra sense with a different filter. That one reads living things and
 * structures and is held open; this one reads blocks, is a single pulse with a cooldown instead of
 * an upkeep, and is deliberately short lived. The server walks the loaded sections within reach once
 * at the moment of casting and sends the positions it found; the client draws the boxes for five
 * seconds and then the picture is gone. Nothing is kept current, so an ore that is mined after the
 * pulse was cast simply stays drawn until it fades - a fair trade for not scanning the world every
 * tick.</p>
 *
 * <p>Mastery widens the pulse from {@link #BASE_RADIUS} to {@link #MASTER_RADIUS}, and from a point
 * on also lets it feel the things that are not ore but are worth finding down there: ancient debris,
 * chests and spawners. The tag read is {@code forge:ores}, so modded ores count too.</p>
 */
public class OreSenseAbility extends Ability implements Ability.Cooldown {

    /** C C B. */
    public static final long COMBO = 112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "ore_sense");

    /** Chakra paid for the pulse. */
    private static final float COST = 30.0f;

    /** Cooldown in ticks: thirty seconds. Mastery shortens it like any other technique. */
    private static final int COOLDOWN = 20 * 30;

    /** Reach of an untrained pulse, and of a fully mastered one, in blocks. */
    private static final float BASE_RADIUS = 24f;
    private static final float MASTER_RADIUS = 32f;

    /** Mastery at which the pulse starts finding ancient debris, chests and spawners as well. */
    private static final float SPECIALS_AT = 0.5f;

    /** The pulse only shows the closest handful, so a rich cave does not turn into a wall of boxes. */
    private static final int MAX_MARKERS = 64;

    private static final int CHAKRA_REGEN_DELAY = 20;

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
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.oreSenseEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.oresense.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        float mastery = this.masteryLevel(ninjaData);
        float radius = BASE_RADIUS + (MASTER_RADIUS - BASE_RADIUS) * mastery;
        boolean specials = mastery >= SPECIALS_AT;
        List<ClientboundOreSensePacket.Marker> markers = scan(level, player.blockPosition(), radius, specials);
        if (markers.isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.oresense.none")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        PacketHandler.sendToPlayer(new ClientboundOreSensePacket(markers), serverPlayer);
    }

    /**
     * Walks the loaded sections that can be reached and collects the blocks worth marking. The
     * section palette is asked first, so a section that holds no ore at all is skipped without
     * touching its blocks.
     */
    private static List<ClientboundOreSensePacket.Marker> scan(ServerLevel level, BlockPos origin,
                                                              float radius, boolean specials) {
        double radiusSqr = (double) radius * radius;
        int reach = Mth.ceil(radius);
        int minChunkX = (origin.getX() - reach) >> 4;
        int maxChunkX = (origin.getX() + reach) >> 4;
        int minChunkZ = (origin.getZ() - reach) >> 4;
        int maxChunkZ = (origin.getZ() + reach) >> 4;
        int minSectionY = Math.max(level.getMinSection(), (origin.getY() - reach) >> 4);
        int maxSectionY = Math.min(level.getMaxSection() - 1, (origin.getY() + reach) >> 4);
        int minSection = level.getMinSection();

        List<ClientboundOreSensePacket.Marker> markers = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int index = sectionY - minSection;
                    if (index < 0 || index >= sections.length) {
                        continue;
                    }
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()
                            || !section.maybeHas(state -> kindOf(state, specials) >= 0)) {
                        continue;
                    }
                    int baseY = sectionY << 4;
                    int baseX = cx << 4;
                    int baseZ = cz << 4;
                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        int dy = worldY - origin.getY();
                        if (dy * dy > radiusSqr) {
                            continue;
                        }
                        for (int z = 0; z < 16; z++) {
                            int worldZ = baseZ + z;
                            int dz = worldZ - origin.getZ();
                            if (dy * dy + dz * dz > radiusSqr) {
                                continue;
                            }
                            for (int x = 0; x < 16; x++) {
                                int worldX = baseX + x;
                                int dx = worldX - origin.getX();
                                if (dx * dx + dy * dy + dz * dz > radiusSqr) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(x, y, z);
                                int kind = kindOf(state, specials);
                                if (kind >= 0) {
                                    markers.add(new ClientboundOreSensePacket.Marker(
                                            new BlockPos(worldX, worldY, worldZ), (byte) kind));
                                }
                            }
                        }
                    }
                }
            }
        }

        markers.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(origin)));
        if (markers.size() > MAX_MARKERS) {
            return new ArrayList<>(markers.subList(0, MAX_MARKERS));
        }
        return markers;
    }

    /**
     * What kind of marker a block deserves, or a negative value when the pulse should not show it.
     * Ancient debris is checked before the ore tag because some packs put it in that tag, and it is
     * meant to be a late find rather than something a beginner's pulse already shows.
     */
    private static int kindOf(BlockState state, boolean specials) {
        if (state.is(Blocks.ANCIENT_DEBRIS)) {
            return specials ? ClientboundOreSensePacket.KIND_ANCIENT : -1;
        }
        if (state.is(ORES)) {
            return ClientboundOreSensePacket.KIND_ORE;
        }
        if (!specials) {
            return -1;
        }
        Block block = state.getBlock();
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block instanceof ShulkerBoxBlock) {
            return ClientboundOreSensePacket.KIND_CHEST;
        }
        if (block == Blocks.SPAWNER) {
            return ClientboundOreSensePacket.KIND_SPAWNER;
        }
        return -1;
    }
}
