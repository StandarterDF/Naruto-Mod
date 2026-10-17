package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Makes a shadow clone cut off a mob that has already marked its caster, before that mob lands the
 * first hit.
 *
 * <p>Every other fight a clone picks is a reaction to something that has already happened: the
 * clone was struck ({@code HurtByTargetGoal}), the caster was struck
 * ({@link CloneOwnerHurtByTargetGoal}), or the caster threw a punch
 * ({@link CloneOwnerHurtTargetGoal}). The moment that actually matters - a zombie walking straight
 * at the caster with its eyes already on them - fell between those three, so the caster took the
 * first hit while their bodyguards stood and watched.</p>
 *
 * <p>Only mobs whose current target <em>is</em> the caster count. A clone still never picks a fight
 * on its own, so it will not run off after the piglin its caster was peacefully trading with; a mob
 * that has taken the caster as its target has picked the fight itself.</p>
 *
 * <p>Workers are left out on purpose. The target is handed over through
 * {@code ShadowCloneEntity#setTarget}, whose worker guard only ever lets self defence through, and a
 * farmhand abandoning its field every time a zombie looks at its caster is exactly what that guard
 * exists to prevent. Meditating clones are skipped here too - they are not guards, they sit where
 * they were put.</p>
 */
public class CloneDefendOwnerGoal extends Goal {

    /** How far around the caster a clone looks for something hunting them. */
    private static final int SEARCH_RANGE = 16;

    /** Ticks between sweeps; a clone does not have to look every tick to be quick about it. */
    private static final int SCAN_INTERVAL = 10;

    private final ShadowCloneEntity clone;
    private Mob aggressor;
    private int cooldown;

    public CloneDefendOwnerGoal(ShadowCloneEntity clone) {
        this.clone = clone;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.clone.isMeditating()) {
            return false;
        }
        Player owner = this.clone.getOwner();
        if (owner == null || owner.isSpectator() || !owner.isAlive()) {
            return false;
        }
        this.cooldown = SCAN_INTERVAL + this.clone.getRandom().nextInt(SCAN_INTERVAL);
        this.aggressor = this.findAggressor(owner);
        return this.aggressor != null;
    }

    @Override
    public void start() {
        this.clone.setTarget(this.aggressor);
        super.start();
    }

    /**
     * The fight itself belongs to whoever holds the movement flag - the melee goal up close, the
     * ranged goal further out - so this goal only ever opens it and hands the target over straight
     * away. It keeps looking while it is not running, which is what lets a clone notice the next mob
     * that comes for its caster.
     */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    /** @return the mob hunting the caster that is closest to the clone, or null if there is none. */
    private Mob findAggressor(Player owner) {
        List<Mob> hunting = this.clone.level().getEntitiesOfClass(
                Mob.class, owner.getBoundingBox().inflate(SEARCH_RANGE), this::isHuntingOwner);
        Mob nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Mob mob : hunting) {
            double distance = mob.distanceToSqr(this.clone);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = mob;
            }
        }
        return nearest;
    }

    /** @return whether the candidate has taken the clone's caster as its current target. */
    private boolean isHuntingOwner(Mob candidate) {
        Player owner = this.clone.getOwner();
        return owner != null
                && candidate != this.clone
                && candidate.isAlive()
                && !this.clone.isAlliedTo(candidate)
                && candidate.getTarget() == owner;
    }
}
