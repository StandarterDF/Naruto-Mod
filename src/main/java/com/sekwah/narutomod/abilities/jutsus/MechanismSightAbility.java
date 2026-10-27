package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundRevealPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mechanism Sight: the eye reads what was built, not what was grown.
 *
 * <p>A Byakugan on a hunt looks for bodies; the same eye on a raid looks for the things a body has
 * left in a corridor. A plate on the floor, a wire strung at ankle height, the charge behind it, a
 * spawner in the dark and every chest in the room are all made of a different kind of chakra than the
 * stone around them, and they stand out the way a lamp does at night.</p>
 *
 * <p>This is the trap-finder rather than the ore-finder: it shows the floor before the caster walks
 * on it, for a longer glance than the ore pulse and a shorter reach. It touches nothing, disarms
 * nothing and reads only loaded ground.</p>
 */
public class MechanismSightAbility extends Ability implements Ability.Cooldown {

    /** Reading a room is silent. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V B C B - the eye's seal ending on the builder's key. */
    public static final long COMBO = 11322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mechanism_sight");

    /** Chakra paid for the read, and how long before it can be repeated. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 300;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Half the edge of the cube that is read, in blocks. Short: a room, not a fortress. */
    private static final int SCAN_RADIUS = 10;

    /** How long the boxes stay on the caster's screen, in ticks. */
    private static final int DURATION = 200;

    /** How many mechanisms one cast may show, nearest first. */
    private static final int MAX_MARKERS = 128;

    /** The pale ring drawn around the caster as the read goes out. */
    private static final double RING_RADIUS = 2.0d;
    private static final int RING_POINTS = 20;

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
        // An empty list is still sent: it clears whatever the last read left behind.
        PacketHandler.sendToPlayer(new ClientboundRevealPacket(markers, DURATION), serverPlayer);
        if (markers.isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.mechanism_sight.none")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        ring(level, player);
    }

    /**
     * Walks the loaded part of the cube and collects the built things in it: everything that reacts
     * to a body walking past it, and everything a body would want to open.
     *
     * <p>Chunks the caster has never been near are skipped rather than loaded, the same way the
     * deeper glance does it.</p>
     */
    private static List<ClientboundRevealPacket.Marker> scan(ServerLevel level, BlockPos origin) {
        List<ClientboundRevealPacket.Marker> markers = new ArrayList<>();

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
                            if (isMechanism(level, cursor, state)) {
                                markers.add(new ClientboundRevealPacket.Marker(cursor.immutable(),
                                        ClientboundRevealPacket.KIND_MECHANISM));
                            }
                        }
                    }
                }
            }
        }

        markers.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(origin)));
        return markers.size() > MAX_MARKERS ? new ArrayList<>(markers.subList(0, MAX_MARKERS)) : markers;
    }

    /**
     * Whether a block is something built to be walked on, opened or set off. The container check is
     * deliberately by block entity rather than by a list of vanilla chests: a barrel, a hopper, a
     * shulker box, a modded crate and a trapped chest are all the same thing to the eye.
     */
    private static boolean isMechanism(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        // Covers the plain wooden and stone plates and the weighted ones, which share the base.
        if (block instanceof BasePressurePlateBlock) {
            return true;
        }
        if (block instanceof TripWireBlock || block instanceof TripWireHookBlock) {
            return true;
        }
        if (block instanceof TntBlock || block instanceof SpawnerBlock) {
            return true;
        }
        return state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container;
    }

    /** A faint ring of pale chakra on the floor, marking where the read went out from. */
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
