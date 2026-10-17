package com.sekwah.narutomod.entity.ai;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * The sword shift of a worker clone: it hunts the animals around itself for meat and picks the fruit
 * that grows on its own - sweet berries and melons.
 *
 * <p>It reuses the whole walk-up-and-take loop of {@link WorkerBlockGoal} for the fruit. On top of
 * that it can break off whatever it is doing to chase a nearby animal: a hunter that let a cow walk
 * past because it was busy with a berry bush would be a poor one. Killing the animal puts its drops
 * straight into the bag (see {@code com.sekwah.narutomod.events.ShadowCloneEvents}).</p>
 *
 * <p>Proper crops are deliberately left alone - wheat and the like are the farmhand's job - so a
 * hunter never turns into a second harvest worker. Tamed animals are spared (they belong to
 * someone), and fish are ignored since a clone cannot swim after them.</p>
 */
public class WorkerHuntGoal extends WorkerBlockGoal {

    /** How far a hunter looks for an animal to chase, in blocks. */
    private static final double PREY_RANGE = 16.0d;

    /** How close the hunter has to be before it swings, squared (2 blocks). */
    private static final double PREY_REACH_SQR = 4.0d;

    /** Ticks between two swings at an animal. */
    private static final int ATTACK_COOLDOWN = 20;

    /** Ticks between two attempts to path at a fleeing animal. */
    private static final int REPATH_TICKS = 10;

    /** How far an animal may get before the hunter gives up and goes back to fruit. */
    private static final double GIVE_UP_SQR = 32.0d * 32.0d;

    /**
     * Animals a hunter leaves alone: they either hit back (a clone has one hit point, so picking a
     * fight with a wolf is suicide) or drop nothing worth eating. Tamed animals are handled
     * separately, since they belong to someone.
     */
    private static final Set<EntityType<?>> NOT_PREY = Set.of(
            EntityType.WOLF, EntityType.POLAR_BEAR, EntityType.GOAT,
            EntityType.LLAMA, EntityType.TRADER_LLAMA);

    private LivingEntity prey;
    private int attackCooldown;
    private int repathCooldown;

    public WorkerHuntGoal(ShadowCloneEntity worker) {
        super(worker);
    }

    @Override
    protected WorkerJob job() {
        return WorkerJob.HUNT;
    }

    /** A hunter sweeps a wider patch than a farmer: the animals it is after wander. */
    @Override
    protected int searchRadius() {
        return 16;
    }

    /**
     * The fruit a hunter picks: a bush with berries on it, or a melon. Crops are not here on
     * purpose - those belong to the farmhand.
     */
    @Override
    protected boolean matches(BlockState state) {
        return state.getBlock() instanceof SweetBerryBushBlock
                && state.getValue(SweetBerryBushBlock.AGE) >= 2
                || state.is(Blocks.MELON);
    }

    @Override
    protected List<BlockPos> plan(ServerLevel level, BlockPos pos) {
        return this.matches(level.getBlockState(pos)) ? List.of(pos.immutable()) : List.of();
    }

    /**
     * Picks the fruit. A berry bush is not dug up: it is stripped back to a bare bush so it grows
     * back, exactly as if a player had right-clicked it. A melon is broken like any block.
     */
    @Override
    protected void take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            for (ItemStack drop : Block.getDrops(state, level, pos, null, this.worker, ItemStack.EMPTY)) {
                this.worker.collect(drop);
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
        } else if (state.is(Blocks.MELON)) {
            for (ItemStack drop : Block.getDrops(state, level, pos, null, this.worker, ItemStack.EMPTY)) {
                this.worker.collect(drop);
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public boolean canUse() {
        if (!this.isMyJob()) {
            return false;
        }
        LivingEntity found = this.findPrey();
        if (found != null) {
            this.prey = found;
            return true;
        }
        this.prey = null;
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.prey != null) {
            return this.prey.isAlive() && this.worker.distanceToSqr(this.prey) <= GIVE_UP_SQR;
        }
        return super.canContinueToUse();
    }

    @Override
    public void start() {
        if (this.prey != null) {
            this.attackCooldown = 0;
            this.repathCooldown = 0;
            this.worker.getNavigation().moveTo(this.prey, 1.0d);
            return;
        }
        super.start();
    }

    @Override
    public void tick() {
        // A hunter that is busy with fruit drops it the moment an animal wanders by: the walk-up
        // loop of the base goal is paused while the hunt runs and picked back up afterwards.
        if (this.prey == null) {
            LivingEntity found = this.findPrey();
            if (found != null) {
                this.prey = found;
                this.attackCooldown = 0;
                this.repathCooldown = 0;
                this.worker.getNavigation().stop();
            }
        }
        if (this.prey != null) {
            this.huntTick();
            return;
        }
        super.tick();
    }

    @Override
    public void stop() {
        this.prey = null;
        super.stop();
    }

    /** Walks to the animal and swings at it until it drops. */
    private void huntTick() {
        if (this.prey == null || !this.prey.isAlive()) {
            return;
        }
        this.worker.getLookControl().setLookAt(this.prey, 30.0f, 30.0f);
        if (this.worker.distanceToSqr(this.prey) > PREY_REACH_SQR) {
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_TICKS;
                this.worker.getNavigation().moveTo(this.prey, 1.0d);
            }
            return;
        }
        this.worker.getNavigation().stop();
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }
        this.attackCooldown = ATTACK_COOLDOWN;
        this.worker.swing(InteractionHand.MAIN_HAND);
        this.worker.doHurtTarget(this.prey);
    }

    /** @return the closest huntable animal around the worker, or null. */
    private LivingEntity findPrey() {
        AABB box = this.worker.getBoundingBox().inflate(PREY_RANGE);
        List<Animal> found = this.worker.level().getEntitiesOfClass(Animal.class, box, this::isPrey);
        Animal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Animal animal : found) {
            double distance = this.worker.distanceToSqr(animal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = animal;
            }
        }
        return best;
    }

    /** A land animal nobody owns, still alive, and not one of the ones that bite back. */
    private boolean isPrey(Animal animal) {
        if (!animal.isAlive() || NOT_PREY.contains(animal.getType())) {
            return false;
        }
        return !(animal instanceof TamableAnimal tameable) || !tameable.isTame();
    }
}
