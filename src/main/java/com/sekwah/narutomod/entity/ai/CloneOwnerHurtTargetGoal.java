package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Makes a shadow clone pile onto whatever its owner just attacked.
 *
 * <p>Vanilla's {@code OwnerHurtTargetGoal} needs a {@code TamableAnimal}, clones are not
 * tameable, so the owner is looked up through {@link ShadowCloneEntity#getOwner()} instead.</p>
 */
public class CloneOwnerHurtTargetGoal extends Goal {

    private final ShadowCloneEntity clone;
    private LivingEntity ownerHurt;
    private int lastHurtTimestamp;

    public CloneOwnerHurtTargetGoal(ShadowCloneEntity clone) {
        this.clone = clone;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Player owner = this.clone.getOwner();
        if (owner == null || owner.isSpectator()) {
            return false;
        }
        this.ownerHurt = owner.getLastHurtMob();
        int timestamp = owner.getLastHurtMobTimestamp();
        return timestamp != this.lastHurtTimestamp
                && this.ownerHurt != null
                && this.ownerHurt.isAlive()
                && !this.clone.isOwnedBy(this.ownerHurt)
                && !this.clone.isAlliedTo(this.ownerHurt)
                && !Friends.areFriends(owner, this.ownerHurt)
                && TargetingConditions.DEFAULT.test(this.clone, this.ownerHurt);
    }

    @Override
    public void start() {
        this.clone.setTarget(this.ownerHurt);
        Player owner = this.clone.getOwner();
        if (owner != null) {
            this.lastHurtTimestamp = owner.getLastHurtMobTimestamp();
        }
        super.start();
    }
}
