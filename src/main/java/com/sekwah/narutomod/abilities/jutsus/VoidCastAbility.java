package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundRevealPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Void Cast: the Byakugan looks past the walls and reads the empty places inside the ground.
 *
 * <p>The eye is not only a way of finding bodies. Stone is not solid to it either, and what is
 * interesting under a field is what is <em>not</em> stone: a chamber nobody has opened, a seam of
 * water under a hill, a pocket of lava one dig away from a mining crew. This is that glance, spent
 * in one cast rather than held: for a few seconds the caster sees the hollows, the water and the
 * lava around them as boxes through the rock, then the picture fades.</p>
 *
 * <p>It reads nothing that moves and nothing that is man-made - the mechanisms have their own
 * technique, see {@link MechanismSightAbility}. The scan runs on the server because the chunks
 * around the caster may not be on their client at all, and only the positions cross over.</p>
 */
public class VoidCastAbility extends Ability implements Ability.Cooldown {

    /** Reading the ground is silent: the eye does not disturb what it looks at. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V B C V - the eye's own seal with a turn of the wrist at the end. */
    public static final long COMBO = 232312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "void_cast");

    /** Chakra paid for the glance, and how long before the eye can spend another. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Half the edge of the cube that is read, in blocks: a 41-block cube around the caster. */
    private static final int SCAN_RADIUS = 20;

    /** How long the boxes stay on the caster's screen, in ticks. */
    private static final int DURATION = 160;

    /**
     * How many blocks one cast may show. A whole cave system does not fit on a screen, and a wall of
     * boxes hides the very thing the caster was looking for, so the nearest blocks win.
     */
    private static final int MAX_MARKERS = 200;

    /**
     * How much of the picture one kind may take. Water and lava come in lakes, and without a cap a
     * single sea would push every chamber out of the result.
     */
    private static final int MAX_PER_KIND = 80;

    /** How many walls an air block needs around it to count as a chamber rather than open sky. */
    private static final int POCKET_WALLS = 4;

    /** The six sides of a block, kept whole rather than rebuilt for every block of the scan. */
    private static final Direction[] SIDES = Direction.values();

    /** The pale ring drawn around the caster as the glance goes out. */
    private static final double RING_RADIUS = 3.0d;
    private static final int RING_POINTS = 24;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
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
        List<ClientboundRevealPacket.Marker> markers = scan(level, player.blockPosition());
        // An empty list is still sent: it clears whatever the last glance left behind.
        PacketHandler.sendToPlayer(new ClientboundRevealPacket(markers, DURATION), serverPlayer);
        if (markers.isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.void_cast.none")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        ring(level, player);
    }

    /**
     * Walks the loaded part of the cube and collects the hidden blocks in it.
     *
     * <p>Only chunks already in memory are read. A chunk the caster has never been near is not
     * loaded, and asking a level for its blocks would pull it in - a glance is not worth generating
     * terrain for.</p>
     */
    private static List<ClientboundRevealPacket.Marker> scan(ServerLevel level, BlockPos origin) {
        List<ClientboundRevealPacket.Marker> voids = new ArrayList<>();
        List<ClientboundRevealPacket.Marker> water = new ArrayList<>();
        List<ClientboundRevealPacket.Marker> lava = new ArrayList<>();

        int minChunkX = (origin.getX() - SCAN_RADIUS) >> 4;
        int maxChunkX = (origin.getX() + SCAN_RADIUS) >> 4;
        int minChunkZ = (origin.getZ() - SCAN_RADIUS) >> 4;
        int maxChunkZ = (origin.getZ() + SCAN_RADIUS) >> 4;
        int lowestY = Math.max(level.getMinBuildHeight(), origin.getY() - SCAN_RADIUS);
        int highestY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + SCAN_RADIUS);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                int fromX = Math.max(origin.getX() - SCAN_RADIUS, chunkX << 4);
                int toX = Math.min(origin.getX() + SCAN_RADIUS, (chunkX << 4) + 15);
                int fromZ = Math.max(origin.getZ() - SCAN_RADIUS, chunkZ << 4);
                int toZ = Math.min(origin.getZ() + SCAN_RADIUS, (chunkZ << 4) + 15);
                for (int y = lowestY; y <= highestY; y++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int x = fromX; x <= toX; x++) {
                            cursor.set(x, y, z);
                            BlockState state = level.getBlockState(cursor);
                            byte kind = kindOf(level, cursor, state);
                            if (kind == ClientboundRevealPacket.KIND_VOID) {
                                voids.add(new ClientboundRevealPacket.Marker(cursor.immutable(), kind));
                            } else if (kind == ClientboundRevealPacket.KIND_WATER) {
                                water.add(new ClientboundRevealPacket.Marker(cursor.immutable(), kind));
                            } else if (kind == ClientboundRevealPacket.KIND_LAVA) {
                                lava.add(new ClientboundRevealPacket.Marker(cursor.immutable(), kind));
                            }
                        }
                    }
                }
            }
        }

        List<ClientboundRevealPacket.Marker> markers = new ArrayList<>();
        markers.addAll(nearest(voids, origin));
        markers.addAll(nearest(water, origin));
        markers.addAll(nearest(lava, origin));
        markers.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(origin)));
        return markers.size() > MAX_MARKERS ? new ArrayList<>(markers.subList(0, MAX_MARKERS)) : markers;
    }

    /**
     * What the eye would box in for this block, or a negative value when there is nothing to show.
     *
     * <p>Lava comes first and water second because both are fluids and a fluid block can only be
     * one of them; the void is checked last and only for air, since a solid block is not a hollow.
     * Only source water is marked - a flowing block is a stream the caster can follow on their own,
     * and drawing every one of them would fill a ravine with boxes.</p>
     */
    private static byte kindOf(ServerLevel level, BlockPos pos, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.LAVA)) {
            return ClientboundRevealPacket.KIND_LAVA;
        }
        if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
            return ClientboundRevealPacket.KIND_WATER;
        }
        if (state.isAir() && isPocket(level, pos)) {
            return ClientboundRevealPacket.KIND_VOID;
        }
        return -1;
    }

    /**
     * Whether an air block is closed in enough to be a chamber rather than the open sky above it.
     * Rock counts as a wall when it hides light as a full block, so modded stone counts too and a
     * fence post does not.
     */
    private static boolean isPocket(ServerLevel level, BlockPos pos) {
        int walls = 0;
        for (Direction side : SIDES) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockState(neighbour).isSolidRender(level, neighbour)) {
                walls++;
                if (walls >= POCKET_WALLS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The nearest {@link #MAX_PER_KIND} of one kind, so one kind cannot crowd the others out. */
    private static List<ClientboundRevealPacket.Marker> nearest(List<ClientboundRevealPacket.Marker> markers,
                                                                BlockPos origin) {
        markers.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(origin)));
        return markers.size() > MAX_PER_KIND ? markers.subList(0, MAX_PER_KIND) : markers;
    }

    /** A ring of pale chakra on the ground, marking where the glance went out from. */
    private static void ring(ServerLevel level, Player player) {
        for (int point = 0; point < RING_POINTS; point++) {
            double angle = Math.PI * 2.0d * point / RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX() + Math.sin(angle) * RING_RADIUS,
                    player.getY() + 0.1d,
                    player.getZ() + Math.cos(angle) * RING_RADIUS,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }
}
