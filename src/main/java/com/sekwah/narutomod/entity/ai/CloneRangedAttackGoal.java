package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import java.util.EnumSet;

/**
 * Makes a clone fight with the ranged weapon it borrowed from its caster.
 *
 * <p>Vanilla's {@code RangedBowAttackGoal} and {@code RangedCrossbowAttackGoal} both only accept
 * {@code Monster}s, so the approach, strafe and charge logic is repeated here for
 * {@link ShadowCloneEntity}. One goal covers all three weapons because they only differ in how long
 * the clone has to hold the trigger and what leaves the weapon in the end.</p>
 *
 * <p>Two rules are clone specific: a bow or crossbow spends the arrows the clone copied from its
 * caster (which is why the clone pops once that quiver runs dry), and the clone never shoots at
 * something standing close enough to hit with a sword, so melee takes over at close range.</p>
 */
public class CloneRangedAttackGoal extends Goal {

    /** A bow has to be drawn to full power before it can be loosed. */
    private static final int BOW_DRAW_TICKS = 20;

    /** The trident's wind up, the same ten ticks a player needs before throwing. */
    private static final int TRIDENT_CHARGE_TICKS = 10;

    /** Speed a crossbow bolt or a thrown trident leaves the weapon at. */
    private static final float SHOT_VELOCITY = 1.6f;

    private final ShadowCloneEntity clone;
    private final double speedModifier;
    private final int attackInterval;
    private final double attackRadiusSqr;

    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public CloneRangedAttackGoal(ShadowCloneEntity clone, double speedModifier, int attackInterval,
                                 float attackRadius) {
        this.clone = clone;
        this.speedModifier = speedModifier;
        this.attackInterval = attackInterval;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.canShoot();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canShoot();
    }

    /**
     * A clone shoots while {@link ShadowCloneEntity#wantsToShoot()} says so and the shield is not in
     * the way. The shield yields as soon as the clone wants to shoot, so this only skips a tick or
     * two while it comes down.
     */
    private boolean canShoot() {
        return !this.clone.isMeditating() && this.clone.wantsToShoot() && !this.clone.isUsingShield();
    }

    @Override
    public void start() {
        super.start();
        this.clone.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.clone.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = -1;
        // Only ever put the ranged weapon away, the shield may be up at the same time.
        if (this.clone.isUsingRangedWeapon()) {
            this.clone.stopUsingItem();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * How long the clone has to hold the trigger before the weapon is ready: the full draw for a
     * bow, the crossbow's own charge time (which Quick Charge shortens) and the trident's wind up.
     */
    private int requiredChargeTicks(ItemStack weapon) {
        if (weapon.getItem() instanceof CrossbowItem) {
            return CrossbowItem.getChargeDuration(weapon);
        }
        if (weapon.getItem() instanceof TridentItem) {
            return TRIDENT_CHARGE_TICKS;
        }
        return BOW_DRAW_TICKS;
    }

    /**
     * How hard the shot is: a bow's power depends on how far it was drawn, a crossbow bolt and a
     * thrown trident always leave at the same speed.
     */
    private float releaseVelocity(ItemStack weapon, int chargeTicks) {
        return weapon.getItem() instanceof BowItem ? BowItem.getPowerForTime(chargeTicks) : SHOT_VELOCITY;
    }

    @Override
    public void tick() {
        LivingEntity target = this.clone.getTarget();
        if (target == null) {
            return;
        }

        double distanceSqr = this.clone.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.clone.getSensing().hasLineOfSight(target);
        boolean sawBefore = this.seeTime > 0;
        if (canSee != sawBefore) {
            this.seeTime = 0;
        }
        this.seeTime += canSee ? 1 : -1;

        // Walk in until the target is in range, then hold position and strafe around it.
        if (distanceSqr <= this.attackRadiusSqr && this.seeTime >= 20) {
            this.clone.getNavigation().stop();
            ++this.strafingTime;
        } else {
            this.clone.getNavigation().moveTo(target, this.speedModifier);
            this.strafingTime = -1;
        }

        if (this.strafingTime >= 20) {
            if ((double) this.clone.getRandom().nextFloat() < 0.3d) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if ((double) this.clone.getRandom().nextFloat() < 0.3d) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (distanceSqr > this.attackRadiusSqr * 0.75d) {
                this.strafingBackwards = false;
            } else if (distanceSqr < this.attackRadiusSqr * 0.25d) {
                this.strafingBackwards = true;
            }
            this.clone.getMoveControl().strafe(this.strafingBackwards ? -0.5f : 0.5f,
                    this.strafingClockwise ? 0.5f : -0.5f);
            this.clone.lookAt(target, 30.0f, 30.0f);
        } else {
            this.clone.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        InteractionHand hand = this.clone.getRangedWeaponHand();
        if (hand == null) {
            if (this.clone.isUsingRangedWeapon()) {
                this.clone.stopUsingItem();
            }
            return;
        }
        ItemStack weapon = this.clone.getItemInHand(hand);

        if (this.clone.isUsingRangedWeapon()) {
            if (!canSee && this.seeTime < -60) {
                this.clone.stopUsingItem();
            } else if (canSee) {
                int chargeTicks = this.clone.getTicksUsingItem();
                if (chargeTicks >= this.requiredChargeTicks(weapon)) {
                    this.clone.stopUsingItem();
                    this.clone.performRangedAttack(target, this.releaseVelocity(weapon, chargeTicks));
                    this.attackTime = this.attackInterval;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.clone.startUsingItem(hand);
        }
    }
}
