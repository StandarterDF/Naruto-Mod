package com.sekwah.narutomod.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Working out what a player is looking at, which vanilla does not hand you on a plate.
 *
 * <p>{@code Entity#pick} only ever traces <b>blocks</b>; it returns a block hit or a miss and never an
 * entity, which is why {@code player.pick(...)} cannot be used to find a target. The client builds
 * its own entity ray for the crosshair, but that lives in the renderer and is not available on the
 * server, so the mod casts the ray itself here.</p>
 */
public final class Targeting {

    private Targeting() {
    }

    /**
     * The entity a player is looking at within {@code range}, or null if they are looking at a wall
     * or at nothing.
     *
     * <p>The ray stops at the first block in the way, so a technique cannot reach a target through a
     * wall, and it is the entity's own hitbox that is tested - the same shape a hit would use.</p>
     */
    @Nullable
    public static Entity lookedAtEntity(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reach = eye.add(look.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, reach,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = blockHit.getType() == HitResult.Type.MISS
                ? range
                : blockHit.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(limit));

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(limit)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(), limit * limit);
        return hit != null ? hit.getEntity() : null;
    }
}
