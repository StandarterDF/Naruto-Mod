package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Makes a shadow clone jump in when someone hits its owner.
 *
 * <p>Vanilla's {@code OwnerHurtByTargetGoal} needs a {@code TamableAnimal}, clones are not
 * tameable, so the owner is looked up through {@link ShadowCloneEntity#getOwner()} instead.</p>
 */
public class CloneOwnerHurtByTargetGoal extends Goal {

    private final ShadowCloneEntity clone;
    private LivingEntity ownerHurtBy;
    private int lastHurtTimestamp;

    public CloneOwnerHurtByTargetGoal(ShadowCloneEntity clone) {
        this.clone = clone;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Player owner = this.clone.getOwner();
        if (owner == null || owner.isSpectator()) {
            return false;
        }
        this.ownerHurtBy = owner.getLastHurtByMob();
        int timestamp = owner.getLastHurtByMobTimestamp();
        return timestamp != this.lastHurtTimestamp
                && this.ownerHurtBy != null
                && this.ownerHurtBy.isAlive()
                && !this.clone.isOwnedBy(this.ownerHurtBy)
                && !this.clone.isAlliedTo(this.ownerHurtBy)
                && TargetingConditions.DEFAULT.test(this.clone, this.ownerHurtBy);
    }

    @Override
    public void start() {
        this.clone.setTarget(this.ownerHurtBy);
        Player owner = this.clone.getOwner();
        if (owner != null) {
            this.lastHurtTimestamp = owner.getLastHurtByMobTimestamp();
        }
        super.start();
    }
}
