package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.JouganAbility;
import com.sekwah.narutomod.abilities.jutsus.SewingPocketAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The consequences of the Jougan's sewing techniques: a portal that has been sewn shut, a door that
 * has been sewn to its frame, and a caster whose steps the world does not hear.
 *
 * <p>Blocks cannot carry data the way an entity can, so the two block techniques keep their state in
 * the server's memory: the position of every seam, what was there before it was sewn, and when the
 * seam lets go. The sweeps here are the other half of those casts - the techniques write, this class
 * restores, expires and refuses. Nothing is persisted on purpose: a seal is a moment, not a change to
 * the world, and losing the table on shutdown only means a portal keeps whatever state the save has.</p>
 *
 * <p>The silent step belongs here for the same reason: it is a tag the game event dispatcher reads,
 * and the tag is only true while the eye that pays for it is open.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JouganExtraEvents {

    /**
     * Marks a body the world must not hear. Read by {@code GameEventDispatcherMixin} together with
     * the silence stance; the mixin only needs the name, so it stays a string here.
     */
    public static final String SILENT_STEP_TAG = "narutomod:silent_step";

    /** The node that teaches the silent step. An unknown key is never unlocked, so this fails safe. */
    private static final String SILENT_STEP_NODE = "mechanic:jounin_silent_step";

    /** How close an ender pearl has to land to a sewn portal before the journey is refused. */
    private static final double PEARL_REFUSAL_RADIUS = 4.0d;

    /** Seals and locks are checked once a second; none of them has to expire within a tick. */
    private static final int SWEEP_INTERVAL = 20;

    /** Sewn portals, with what stood there and when the opening comes back. */
    private static final Map<BlockKey, Sealed> SEALED_PORTALS = new HashMap<>();

    /** Locks on doors, gates and lids: who may still work them, and for how long. */
    private static final Map<BlockKey, Lock> LOCKS = new HashMap<>();

    private static int sweepCounter;

    private JouganExtraEvents() {
    }

    // ---------------------------------------------------------------------------------------------
    // What the techniques call
    // ---------------------------------------------------------------------------------------------

    /** @return whether this block is one of the seams a portal is cut along. */
    public static boolean isPortal(BlockState state) {
        Block block = state.getBlock();
        return block instanceof NetherPortalBlock
                || block instanceof EndPortalBlock
                || block instanceof EndGatewayBlock;
    }

    /** @return whether this block is something the sewing lock can hold shut. */
    public static boolean isLockable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock;
    }

    /**
     * Takes every portal block within {@code radius} of {@code center} out of the world and remembers
     * what to put back. The frame is left alone: only the opening is sewn.
     *
     * @return the positions that were sewn, for the cast's own effects
     */
    public static List<BlockPos> sealPortals(ServerLevel level, BlockPos center, int radius, long until) {
        List<BlockPos> sewn = new ArrayList<>();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.isOutsideBuildHeight(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isPortal(state)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            SEALED_PORTALS.put(key(level, immutable), new Sealed(state, until));
            level.setBlock(immutable, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            sewn.add(immutable);
        }
        return sewn;
    }

    /** Holds a block shut for everyone but {@code owner} until {@code until}. */
    public static void lock(ServerLevel level, BlockPos pos, UUID owner, long until) {
        LOCKS.put(key(level, pos), new Lock(owner, until));
    }

    // ---------------------------------------------------------------------------------------------
    // Restoration and expiry
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++sweepCounter < SWEEP_INTERVAL) {
            return;
        }
        sweepCounter = 0;
        MinecraftServer server = event.getServer();
        restorePortals(server);
        expireLocks(server);
    }

    /**
     * Puts every expired portal back. A chunk that is not loaded is left for a later sweep rather
     * than written into, and a position somebody has built over is not overwritten: the seal takes
     * the opening away, it does not claim the space.
     */
    private static void restorePortals(MinecraftServer server) {
        Iterator<Map.Entry<BlockKey, Sealed>> entries = SEALED_PORTALS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockKey, Sealed> entry = entries.next();
            BlockKey key = entry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                entries.remove();
                continue;
            }
            if (level.getGameTime() < entry.getValue().until()) {
                continue;
            }
            if (!level.isLoaded(key.pos())) {
                continue;
            }
            if (level.getBlockState(key.pos()).isAir()) {
                level.setBlock(key.pos(), entry.getValue().state(), Block.UPDATE_ALL);
            }
            entries.remove();
        }
    }

    /** Drops the locks whose time is up, one level at a time so each keeps its own clock. */
    private static void expireLocks(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            LOCKS.entrySet().removeIf(entry ->
                    entry.getKey().dimension().equals(level.dimension()) && entry.getValue().until() <= now);
        }
    }

    /**
     * Restores the seams before the server saves, so a restart does not leave a permanently open
     * frame behind. Only the shutdown path gets this; a crash loses the table and the portal blocks
     * with it, which is the accepted cost of keeping seals in memory.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<BlockKey, Sealed> entry : SEALED_PORTALS.entrySet()) {
            BlockKey key = entry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.pos())) {
                continue;
            }
            if (level.getBlockState(key.pos()).isAir()) {
                level.setBlock(key.pos(), entry.getValue().state(), Block.UPDATE_ALL);
            }
        }
        SEALED_PORTALS.clear();
        LOCKS.clear();
        sweepCounter = 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Pearls and locks
    // ---------------------------------------------------------------------------------------------

    /**
     * A pearl thrown at a sewn portal is refused, which is the only part of the seal a caster can feel
     * directly: the seam is shut for travel as well as for walking.
     *
     * <p>This is the dimension-change path only; a pearl that never leaves its own level is handled
     * by vanilla's own teleport and is not seen here.</p>
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ThrownEnderpearl pearl)
                || !(pearl.level() instanceof ServerLevel level)) {
            return;
        }
        if (isSealedNear(level, pearl.position(), PEARL_REFUSAL_RADIUS)) {
            event.setCanceled(true);
        }
    }

    /** @return whether a live seal stands within {@code radius} of the given point. */
    private static boolean isSealedNear(ServerLevel level, Vec3 position, double radius) {
        long now = level.getGameTime();
        double limit = radius * radius;
        for (Map.Entry<BlockKey, Sealed> entry : SEALED_PORTALS.entrySet()) {
            if (!entry.getKey().dimension().equals(level.dimension()) || entry.getValue().until() <= now) {
                continue;
            }
            if (Vec3.atCenterOf(entry.getKey().pos()).distanceToSqr(position) <= limit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuses the interaction when the block under the player's hand is sewn shut and somebody else
     * did the sewing. The owner is read from the lock rather than from the player, so the same rule
     * holds after a relog.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockKey key = key(level, event.getPos());
        Lock lock = LOCKS.get(key);
        if (lock == null) {
            return;
        }
        if (lock.until() <= level.getGameTime()) {
            LOCKS.remove(key);
            return;
        }
        Player player = event.getEntity();
        if (lock.owner().equals(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("jutsu.fail.sewing_lock.held"), true);
    }

    // ---------------------------------------------------------------------------------------------
    // Silent step
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        tickSilentStep(event.player);
        tickSewingPocket(event.player);
    }

    /**
     * Keeps the silent-step tag in step with the eye. The tag is only written when it has to change,
     * because persistent data is what gets saved with the player and rewriting it every tick would
     * keep marking the file dirty for nothing.
     */
    private static void tickSilentStep(Player player) {
        boolean silent = player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .map(ninja -> JouganAbility.isActive(ninja)
                        && SkillTree.hasUnlock(ninja.getSkillData().getUnlocked(), SILENT_STEP_NODE))
                .orElse(false);
        CompoundTag data = player.getPersistentData();
        if (silent) {
            if (!data.getBoolean(SILENT_STEP_TAG)) {
                data.putBoolean(SILENT_STEP_TAG, true);
            }
        } else if (data.contains(SILENT_STEP_TAG)) {
            data.remove(SILENT_STEP_TAG);
        }
    }

    /**
     * Closes the sewing pocket when its time is up. The menu id recorded by the cast is compared
     * first, so a container the caster opened in the meantime is left alone.
     */
    private static void tickSewingPocket(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SewingPocketAbility.OPEN_UNTIL_TAG)) {
            return;
        }
        if (player.level().getGameTime() < data.getLong(SewingPocketAbility.OPEN_UNTIL_TAG)) {
            return;
        }
        int menuId = data.getInt(SewingPocketAbility.OPEN_MENU_TAG);
        data.remove(SewingPocketAbility.OPEN_UNTIL_TAG);
        data.remove(SewingPocketAbility.OPEN_MENU_TAG);
        if (player.containerMenu != player.inventoryMenu && player.containerMenu.containerId == menuId) {
            player.closeContainer();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Keys and values
    // ---------------------------------------------------------------------------------------------

    private static BlockKey key(ServerLevel level, BlockPos pos) {
        return new BlockKey(level.dimension(), pos.immutable());
    }

    /** A block is only meaningful together with its dimension; the same coordinates exist in every one. */
    private record BlockKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** An opening that was taken away, and the tick it is due back on. */
    private record Sealed(BlockState state, long until) {
    }

    /** Who sewed a block shut, and the tick the seam lets go. */
    private record Lock(UUID owner, long until) {
    }
}
