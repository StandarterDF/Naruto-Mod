package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The geometry shared by every breath: a cone opened in front of the caster's eyes.
 *
 * <p>It used to live inside {@link AbstractBreathAbility}, but the fire breath keeps its own class
 * (it has its own damage type and furnace accessors) and needs exactly the same cone. Keeping the
 * maths here means a change to the shape applies to all four breaths at once instead of drifting
 * apart in copies.</p>
 */
public final class BreathCone {

    private BreathCone() {
    }

    /** Everything alive caught in the cone, the caster and their allies excluded. */
    public static List<LivingEntity> entities(ServerLevel level, Player player, double range, double coneCos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 centre = eye.add(look.scale(range * 0.5d));
        AABB area = new AABB(centre, centre).inflate(range);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player)
                        && !Friends.areFriends(player, entity))) {
            if (insideCone(eye, look, target.getEyePosition(), range, coneCos)) {
                found.add(target);
            }
        }
        return found;
    }

    /** True when the point is within {@code range} of the eye and inside the cone. */
    public static boolean insideCone(Vec3 eye, Vec3 look, Vec3 point, double range, double coneCos) {
        Vec3 toTarget = point.subtract(eye);
        if (toTarget.lengthSqr() > range * range) {
            return false;
        }
        return toTarget.normalize().dot(look) >= coneCos;
    }

    /**
     * Runs the consumer over every block position inside the cone. Positions are collected through a
     * plain coordinate walk, so a consumer is free to change the world as it goes.
     */
    public static void forEachBlock(Player player, double range, double coneCos, Consumer<BlockPos> consumer) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 centre = eye.add(look.scale(range * 0.5d));
        BlockPos origin = BlockPos.containing(centre.x, centre.y, centre.z);
        int r = Mth.ceil(range);
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (insideCone(eye, look, Vec3.atCenterOf(pos), range, coneCos)) {
                consumer.accept(pos.immutable());
            }
        }
    }
}
