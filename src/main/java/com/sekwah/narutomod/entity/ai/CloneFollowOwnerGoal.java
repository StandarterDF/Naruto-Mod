package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.EnumSet;

/**
 * Keeps a shadow clone close to whoever created it, teleporting when it falls too far behind.
 *
 * <p>Vanilla's {@code FollowOwnerGoal} only works with {@code TamableAnimal}, and clones are not
 * tameable, so this is a trimmed down version of it.</p>
 */
public class CloneFollowOwnerGoal extends Goal {

    private final ShadowCloneEntity clone;
    private final double speedModifier;
    /** Distance at which the clone starts walking after its owner. */
    private final float startDistance;
    /** Distance at which it stops again. */
    private final float stopDistance;
    /** Distance at which it gives up walking and just teleports. */
    private final float teleportDistance;

    private Player owner;
    private int timeToRecalcPath;
    private float oldWaterCost;

    public CloneFollowOwnerGoal(ShadowCloneEntity clone, double speedModifier, float startDistance,
                                float stopDistance, float teleportDistance) {
        this.clone = clone;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.teleportDistance = teleportDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player owner = this.clone.getOwner();
        if (owner == null || owner.isSpectator() || owner.isDeadOrDying()) {
            return false;
        }
        if (this.clone.distanceToSqr(owner) < (double) (this.startDistance * this.startDistance)) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null
                && this.clone.distanceToSqr(this.owner) > (double) (this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.clone.getPathfindingMalus(BlockPathTypes.WATER);
        this.clone.setPathfindingMalus(BlockPathTypes.WATER, 0.0f);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.clone.getNavigation().stop();
        this.clone.setPathfindingMalus(BlockPathTypes.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        this.clone.getLookControl().setLookAt(this.owner, 10.0f, (float) this.clone.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(10);

        if (this.clone.isPassenger()) {
            return;
        }
        if (this.clone.distanceToSqr(this.owner) >= (double) (this.teleportDistance * this.teleportDistance)) {
            this.teleportToOwner();
        } else {
            this.clone.getNavigation().moveTo(this.owner, this.speedModifier);
        }
    }

    private void teleportToOwner() {
        BlockPos ownerPos = this.owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + this.clone.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY() + this.clone.getRandom().nextInt(3) - 1;
            int z = ownerPos.getZ() + this.clone.getRandom().nextInt(7) - 3;
            if (this.maybeTeleportTo(x, y, z)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        BlockPos target = new BlockPos(x, y, z);
        if (!this.canTeleportTo(target)) {
            return false;
        }
        this.clone.moveTo((double) x + 0.5d, (double) y, (double) z + 0.5d,
                this.clone.getYRot(), this.clone.getXRot());
        this.clone.getNavigation().stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos pos) {
        if (WalkNodeEvaluator.getBlockPathTypeStatic(this.clone.level(), pos.mutable()) != BlockPathTypes.WALKABLE) {
            return false;
        }
        BlockPos offset = pos.subtract(this.clone.blockPosition());
        return this.clone.level().noCollision(this.clone, this.clone.getBoundingBox().move(offset));
    }
}
