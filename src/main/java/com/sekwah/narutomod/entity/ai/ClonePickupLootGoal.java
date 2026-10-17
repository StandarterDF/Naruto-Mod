package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a shadow clone pick up loot lying on the ground, the way its caster would.
 *
 * <p>Any dropped item is fair game - a mob's drops, a tool somebody threw away, the overflow of a
 * full chest - and it goes into the caster's supply chest exactly like a worker's harvest does, see
 * {@link ShadowCloneEntity#collect(ItemStack)}. If the caster is not around to take it, it waits in
 * the clone's own bag and is handed over when the clone pops, so nothing is ever lost.</p>
 *
 * <p>The goal sits at priority 0, above every other goal a clone has, because of how vanilla
 * {@code GoalSelector} hands out flags: {@code WrappedGoal#canBeReplacedBy} lets a goal take a flag
 * from a running one only when its priority number is <em>strictly</em> smaller, so a loot goal
 * anywhere below the work, following and combat goals would simply never run - the clone is busy
 * with one of those almost all the time, and a busy clone would walk straight past the loot.</p>
 */
public class ClonePickupLootGoal extends Goal {

    /** How far from the clone it looks for loot. */
    private static final double SEARCH_RANGE = 8.0d;

    /** How close it has to get before it can take an item: two blocks, a worker's reach. */
    private static final double PICKUP_RANGE_SQR = 4.0d;

    /** Ticks between one attempt to find a path and the next. */
    private static final int REPATH_TICKS = 10;

    /**
     * How old an item has to be before the clone will take it. A dropped item is left alone for
     * half a second, so a clone standing next to its caster does not pluck a thrown item out of the
     * air on the very tick it appears.
     */
    private static final int SNATCH_TICKS = 10;

    /** How long a clone tries to reach one item before it leaves it be. */
    private static final int GIVE_UP_TICKS = 120;

    /**
     * How long a clone stands with no path left before it gives up on an item. The pathfinder usually
     * gives up long before the hard cap when an item sits somewhere it cannot walk to, and standing
     * there for the full six seconds would cost a worker a good part of its shift.
     */
    private static final int STUCK_TICKS = 40;

    /** How long an item the clone could not reach is ignored before it is worth another try. */
    private static final int FORGET_TICKS = 400;

    /** Ticks between two searches for loot, so a clone does not sweep the area every tick. */
    private static final int SEARCH_INTERVAL = 10;

    private final ShadowCloneEntity clone;

    /** Items this clone gave up on, with the tick they may be picked again. */
    private final Map<Integer, Integer> forgotten = new HashMap<>();

    private ItemEntity item;
    private int repathTicks;
    private int reachTicks;
    private int stuckTicks;
    private int searchCooldown;

    /**
     * Whoever the clone was fighting when it set off for loot. Vanilla's melee goal clears the mob's
     * target when it stops, and taking the movement flag away from it stops it, so the enemy is
     * remembered here and handed back in {@link #stop()}: a clone that stepped aside for a dropped
     * item walks back into the fight instead of forgetting it.
     */
    private LivingEntity savedTarget;

    public ClonePickupLootGoal(ShadowCloneEntity clone) {
        this.clone = clone;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.applies()) {
            return false;
        }
        if (this.searchCooldown > 0) {
            this.searchCooldown--;
            return false;
        }
        this.searchCooldown = SEARCH_INTERVAL;
        this.forgotten.values().removeIf(until -> this.clone.tickCount >= until);
        this.item = this.findLoot();
        if (this.item != null) {
            // Read before the goal actually starts: the melee goal is stopped as soon as this goal
            // takes the movement flag, and stopping it is what clears the target.
            this.savedTarget = this.clone.getTarget();
        }
        return this.item != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.applies() || this.item == null || !this.item.isAlive()
                || this.item.getItem().isEmpty()) {
            return false;
        }
        if (this.reachTicks > GIVE_UP_TICKS || this.stuckTicks > STUCK_TICKS) {
            // Out of reach after all: leave it be for a while, or the goal would pick the very same
            // item again on the next tick and the clone would stand by it forever.
            this.forgotten.put(this.item.getId(), this.clone.tickCount + FORGET_TICKS);
            return false;
        }
        return true;
    }

    /** Only the server picks things up, and a meditating clone stays where it was put. */
    private boolean applies() {
        return !this.clone.level().isClientSide && !this.clone.isMeditating();
    }

    @Override
    public void start() {
        this.repathTicks = 0;
        this.reachTicks = 0;
        this.stuckTicks = 0;
    }

    @Override
    public void stop() {
        this.item = null;
        this.clone.getNavigation().stop();
        LivingEntity target = this.savedTarget;
        this.savedTarget = null;
        // Only if the clone has not picked a new enemy in the meantime, and the old one is still
        // worth fighting: HurtByTargetGoal drops a target that died or got away, and that still
        // holds here.
        if (target != null && target.isAlive() && this.clone.getTarget() == null) {
            this.clone.setTarget(target);
        }
    }

    @Override
    public void tick() {
        if (this.item == null) {
            return;
        }
        this.clone.getLookControl().setLookAt(this.item, 10.0f, (float) this.clone.getMaxHeadXRot());
        if (this.clone.distanceToSqr(this.item) <= PICKUP_RANGE_SQR) {
            this.take();
            return;
        }
        this.reachTicks++;
        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_TICKS;
            // Standing still with no path left means the item cannot be walked to; a couple of
            // seconds of that is enough to conclude as much, the same way a worker gives up on a
            // block it cannot reach.
            this.stuckTicks = this.clone.getNavigation().isDone() ? this.stuckTicks + REPATH_TICKS : 0;
            this.clone.getNavigation().moveTo(this.item, 1.1d);
        }
    }

    /** Takes the whole dropped stack: it is the caster's now, not the clone's. */
    private void take() {
        ItemStack stack = this.item.getItem();
        if (!stack.isEmpty()) {
            this.clone.collect(stack);
        }
        this.item.discard();
        this.item = null;
    }

    /** The closest dropped item that is ready to be picked up, or null when there is none. */
    private ItemEntity findLoot() {
        List<ItemEntity> nearby = this.clone.level().getEntitiesOfClass(ItemEntity.class,
                this.clone.getBoundingBox().inflate(SEARCH_RANGE),
                candidate -> candidate.isAlive()
                        && !candidate.getItem().isEmpty()
                        && candidate.tickCount >= SNATCH_TICKS
                        && !this.forgotten.containsKey(candidate.getId()));
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity candidate : nearby) {
            double distance = this.clone.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
