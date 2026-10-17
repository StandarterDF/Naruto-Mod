package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
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
 * <p>It is registered for every clone rather than only for workers: even one that was summoned for
 * a fight should not walk past loot. It sits below combat and following, though, so a clone never
 * abandons a target or its caster to chase a dropped item - it only picks things up when it has
 * nothing more important to do.</p>
 */
public class ClonePickupLootGoal extends Goal {

    /** How far from the clone it looks for loot. */
    private static final double SEARCH_RANGE = 8.0d;

    /** How close it has to get before it can take an item. */
    private static final double PICKUP_RANGE_SQR = 2.25d;

    /** Ticks between one attempt to find a path and the next. */
    private static final int REPATH_TICKS = 10;

    /** How long a clone tries to reach one item before it leaves it be. */
    private static final int GIVE_UP_TICKS = 120;

    /** How long an item the clone could not reach is ignored before it is worth another try. */
    private static final int FORGET_TICKS = 200;

    private final ShadowCloneEntity clone;

    /** Items this clone gave up on, with the tick they may be picked again. */
    private final Map<Integer, Integer> forgotten = new HashMap<>();

    private ItemEntity item;
    private int repathTicks;
    private int reachTicks;

    public ClonePickupLootGoal(ShadowCloneEntity clone) {
        this.clone = clone;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.clone.level().isClientSide || this.clone.isMeditating()) {
            return false;
        }
        this.forgotten.values().removeIf(until -> this.clone.tickCount >= until);
        this.item = this.findLoot();
        return this.item != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.item == null || !this.item.isAlive() || this.item.getItem().isEmpty()) {
            return false;
        }
        if (this.reachTicks > GIVE_UP_TICKS) {
            // Out of reach after all: leave it be for a while, or the goal would pick the very same
            // item again on the next tick and the clone would stand by it forever.
            this.forgotten.put(this.item.getId(), this.clone.tickCount + FORGET_TICKS);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.repathTicks = 0;
        this.reachTicks = 0;
    }

    @Override
    public void stop() {
        this.item = null;
        this.clone.getNavigation().stop();
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
                        && !candidate.hasPickUpDelay()
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
