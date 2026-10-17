package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ShieldItem;

/**
 * Raises the shield a clone copied from its caster while it is fighting.
 *
 * <p>Mobs never block in vanilla, so this simply holds the offhand shield up. That is enough for the
 * vanilla damage code to do the rest: a blocked hit costs the shield some durability and deals no
 * damage, which is what keeps a one health clone alive.</p>
 *
 * <p>The goal deliberately has no {@link Goal.Flag}s, so it can run next to the melee and ranged
 * goals without stealing their movement or look control.</p>
 */
public class CloneUseShieldGoal extends Goal {

    /** Keep the shield up this long after being hit, even with nothing left to fight. */
    private static final int HURT_MEMORY_TICKS = 20 * 3;

    private final ShadowCloneEntity clone;

    public CloneUseShieldGoal(ShadowCloneEntity clone) {
        this.clone = clone;
    }

    @Override
    public boolean canUse() {
        return this.wantsShieldUp();
    }

    @Override
    public boolean canContinueToUse() {
        return this.wantsShieldUp();
    }

    /**
     * A clone blocks whenever it has a shield, something to fight and no shot worth taking: a
     * loaded ranged weapon wins at range, so the shield only comes up once the enemy is close enough
     * to swing at (or the clone was hit and is still reacting to it).
     *
     * <p>Because clones pop in a single hit, the shield has to be up <em>before</em> the blow lands,
     * which is why this does not wait to be attacked first.</p>
     */
    private boolean wantsShieldUp() {
        if (this.clone.isMeditating()) {
            return false;
        }
        if (!this.hasShield() || this.clone.isUsingRangedWeapon()) {
            return false;
        }
        if (this.clone.wantsToShoot()) {
            return false;
        }
        return this.hasTarget() || this.recentlyHurt();
    }

    private boolean hasShield() {
        return this.clone.getOffhandItem().getItem() instanceof ShieldItem;
    }

    private boolean hasTarget() {
        LivingEntity target = this.clone.getTarget();
        return target != null && target.isAlive();
    }

    private boolean recentlyHurt() {
        return this.clone.getLastHurtByMob() != null
                && this.clone.tickCount - this.clone.getLastHurtByMobTimestamp() <= HURT_MEMORY_TICKS;
    }

    @Override
    public void tick() {
        LivingEntity target = this.clone.getTarget();
        if (target != null) {
            // A shield only blocks hits from the front.
            this.clone.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
        // Raise it again if the ranged goal put it down on its way out.
        if (!this.clone.isUsingShield()) {
            this.clone.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    @Override
    public void stop() {
        if (this.clone.isUsingShield()) {
            this.clone.stopUsingItem();
        }
        super.stop();
    }
}
