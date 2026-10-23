package com.sekwah.narutomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the standing sentry seals are, per dimension.
 *
 * <p>A spawn check is asked about a position, not about a block entity, so it needs a cheap way to
 * ask "is a ward near here" without scanning chunk sections. Loaded seals register themselves here
 * and drop out when their block entity is removed or their chunk unloads, so the set always mirrors
 * the seals that could actually do anything.</p>
 *
 * <p>Kept deliberately free of block entity references: only positions, so an unloaded chunk cannot
 * be pinned in memory.</p>
 */
public final class SentrySealTracker {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> SEALS = new ConcurrentHashMap<>();

    private SentrySealTracker() {
    }

    public static void register(ServerLevel level, BlockPos pos) {
        SEALS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Set<BlockPos> seals = SEALS.get(level.dimension());
        if (seals != null) {
            seals.remove(pos);
        }
    }

    /** @return whether any loaded seal of this dimension stands within {@code radius} of a point. */
    public static boolean anyWithin(ServerLevel level, double x, double y, double z, double radius) {
        Set<BlockPos> seals = SEALS.get(level.dimension());
        if (seals == null || seals.isEmpty()) {
            return false;
        }
        double radiusSqr = radius * radius;
        for (BlockPos pos : seals) {
            if (pos.distToCenterSqr(x, y, z) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
