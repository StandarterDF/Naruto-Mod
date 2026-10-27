package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import com.sekwah.narutomod.item.NarutoItems;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * Summons copies of the caster which fight for them, see {@link ShadowCloneEntity}.
 *
 * <p>The same class backs all three versions of the technique (normal, chibi and multi shadow
 * clone), they only differ in their {@link Settings}.</p>
 *
 * <p>Casting any of these while sneaking summons workers instead of fighters: no talisman to craft,
 * just hold the shift key as you make the seal. The clones come out ready to work rather than fight
 * and walk off to farm, mine or chop around themselves. The item in the caster's main hand - which
 * has to be a tool at all for the summon to be allowed - decides which of the four it is, see
 * {@link WorkerJob}.</p>
 */
public class ShadowCloneJutsuAbility extends Ability implements Ability.Cooldown {
    /**
     * How far a cast of this technique carries as a game event. An ordinary cast.
     */
    @Override
    public int noiseRadius() {
        return 24;
    }


    /**
     * Tunables for one version of the technique.
     *
     * <p>{@code combo} uses the same hand seal encoding as the rest of the mod, where the digits
     * match the jutsu keys (1 = key 1, 2 = key 2, 3 = key 3). These are the combos from the 1.7.10
     * mod: cbbv for the shadow clone, cbvv for the chibi version and cbbvbc for the multi version.</p>
     *
     * <p>{@code masteryMaxClones} is the number of clones the technique grows to at full mastery. A
     * technique whose base spread is a single clone ({@code minClones == maxClones == 1}) hands that
     * one clone out at the start and adds another as it is mastered, all the way to
     * {@code masteryMaxClones}; if {@code masteryMaxClones} is no larger than {@code maxClones} the
     * count does not scale at all. {@code requiresSeal} marks the techniques that burn a chakra seal
     * unless the caster has mastered the technique enough that it is free.</p>
     */
    public record Settings(long combo, float chakraCost, int minClones, int maxClones, int masteryMaxClones,
                           int lifetime, boolean chibi, boolean meditating, boolean requiresSeal,
                           double attackDamage, double movementSpeed, int cooldown, int chakraRegenDelay) {}

    /**
     * C B B V - the plain Kage Bunshin. It makes one clone, as the technique does in the stories, and
     * earns the right to a second, third and fourth as it is mastered; a low mastery ninja does not
     * get a squad for free.
     */
    public static final Settings SHADOW_CLONE_SETTINGS =
            new Settings(1221L, 40f, 1, 1, 4, 20 * 25, false, false, false, 2.0d, 0.3d, 20 * 3, 20 * 2);

    /**
     * C B V V - cheap, small and weak. Also a single clone at the start, growing into a handful of
     * little ones as it is practised.
     */
    public static final Settings CHIBI_SHADOW_CLONE_SETTINGS =
            new Settings(1213L, 20f, 1, 1, 5, 20 * 15, true, false, false, 1.0d, 0.35d, 20 * 2, 20);

    /**
     * C B B V B C - the expensive one, a small army. It starts as a handful and grows to a full ten
     * as the caster masters it, and it burns a chakra seal until then.
     */
    public static final Settings MULTI_SHADOW_CLONE_SETTINGS =
            new Settings(111132L, 80f, 4, 5, 10, 20 * 25, false, false, true, 2.0d, 0.3d, 20 * 10, 20 * 5);

    /**
     * C B B C - two clones left behind to meditate, see
     * {@code ChakraMeditationJutsuAbility#tickAura(Player)}. They neither move nor fight (their
     * speed and damage are zero and their goals are skipped), so all they do is sit there and feed
     * the caster chakra while they last. They are the one clone that is supposed to be a long term
     * investment, so they outlive every other version of the technique by a wide margin.
     */
    public static final Settings CHAKRA_MEDITATION_SETTINGS =
            new Settings(12222L, 60f, 2, 2, 0, 20 * 180, false, true, true, 0.0d, 0.0d, 20 * 60, 20 * 3);

    /** Radius of the ring the clones appear in. */
    private static final double SPAWN_RADIUS = 1.6d;

    /**
     * What a worker summon costs, whichever clone combo was pressed to make it. Workers are a heavier
     * technique than a fighting clone - they are the closest thing to the multi shadow clone a ninja
     * can lean on for real work - so they are priced above a plain clone. How many come out is the
     * technique's own business: the shadow clone raises one worker, the multi raises a squad.
     */
    private static final float WORKER_COST = 75f;

    /**
     * How far a clone is looked for when a summon is checked against the ones already out. Fighters
     * follow their caster, so this only needs to cover a scuffle; workers are left behind where they
     * were summoned, so it is wide enough to keep finding a squad that has been posted a distance
     * away.
     */
    private static final double CLONE_SCAN = 128.0d;

    /** How long a combat summon is shown as on cooldown while its clones are still on the field. */
    public static final int COMBAT_CLONE_HOLD_SECONDS = 300;

    private final Settings settings;

    public ShadowCloneJutsuAbility(Settings settings) {
        this.settings = settings;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return this.settings.combo();
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // Sneaking turns the cast into a worker summon, which needs no talisman at all - the only
        // thing that decides whether the summon is allowed is a tool in the main hand.
        boolean summoningWorkers = summoningWorkers(player);

        // Only the heavier versions of the technique burn a seal talisman, and even those stop
        // needing one once the caster has mastered the technique. It is taken in performServer,
        // here we only make sure there is one to take so the caster is not charged for nothing.
        boolean needsSeal = this.settings.requiresSeal() && !Mastery.sealFree(ninjaData, this)
                && !summoningWorkers;
        if (needsSeal && !hasSeal(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.noseal",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        // A worker is defined by the tool it was summoned with, so sneaking without a tool in the
        // main hand is not a technique at all: refusing it here costs the caster nothing - no chakra
        // - rather than leaving them with clones and no shift to do. A tool that reads as a trade the
        // caster has not bought in the skill tree is refused the same way.
        if (summoningWorkers) {
            WorkerJob job = workerJob(player);
            if (job == null) {
                player.displayClientMessage(Component.translatable("jutsu.fail.workertool",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
                return false;
            }
            if (!SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), job.unlockKey())) {
                player.displayClientMessage(Component.translatable("jutsu.fail.workerlocked",
                        Component.translatable(job.labelKey()).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        float cost = this.masteryCost(ninjaData, summoningWorkers ? WORKER_COST : this.settings.chakraCost());
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, this.settings.chakraRegenDelay());
        return true;
    }

    /**
     * Whether the caster has a seal talisman to burn. Creative casters are spared the bookkeeping,
     * the same way they are spared food.
     */
    private static boolean hasSeal(Player player) {
        if (player.isCreative()) {
            return true;
        }
        return player.getInventory().items.stream().anyMatch(ShadowCloneJutsuAbility::isSeal)
                || player.getInventory().offhand.stream().anyMatch(ShadowCloneJutsuAbility::isSeal);
    }

    /**
     * Takes one seal off the caster: the first stack found, wherever it is. Called once per summon
     * no matter how many clones are about to appear.
     */
    private static void consumeSeal(Player player) {
        if (player.isCreative()) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isSeal(stack)) {
                stack.shrink(1);
                return;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isSeal(stack)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static boolean isSeal(ItemStack stack) {
        return stack.is(NarutoItems.CHAKRA_SEAL.get());
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // Meditation is always just meditation: sneaking does not turn a clone that is meant to sit
        // still into one that walks off to work. Every other technique follows the sneak, and
        // handleCost has already made sure a worker's tool is in hand by the time we get here.
        boolean summoningWorkers = summoningWorkers(player);

        if (this.settings.requiresSeal() && !Mastery.sealFree(ninjaData, this) && !summoningWorkers) {
            consumeSeal(player);
        }

        int cloneCount = this.cloneCount(player, ninjaData);

        for (int i = 0; i < cloneCount; i++) {
            int lifetime = summoningWorkers ? ShadowCloneEntity.WORKER_LIFETIME : this.settings.lifetime();
            // The clone branch stretches the life of a copy - worker and fighter tracked separately,
            // since a worker's shift and a fighter's charge are tuned against different numbers.
            float lifetimeBonus = SkillTree.bonus(ninjaData.getSkillData().getUnlocked(),
                    summoningWorkers ? SkillEffect.WORKER_LIFETIME : SkillEffect.CLONE_LIFETIME);
            lifetime = Math.round(lifetime * (1.0f + lifetimeBonus));
            ShadowCloneEntity clone = new ShadowCloneEntity(level, player,
                    lifetime, this.settings.chibi(), this.settings.meditating());
            clone.applyVariantAttributes(this.settings.attackDamage(), this.settings.movementSpeed());
            if (summoningWorkers) {
                clone.setWorker(true);
                clone.setWorkerHome(player.blockPosition());
                clone.setWorkerJob(workerJob(player));
                clone.setWorkerTool(player.getMainHandItem());
                clone.copyWorkerTorches(player);
            }
            this.positionClone(clone, level, player, i, cloneCount);
            level.addFreshEntity(clone);
        }

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                60, 0.6d, 0.8d, 0.6d, 0.02d);
        level.playSound(null, player, NarutoSounds.SHADOW_CLONE_POOF.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * Reads the job off the item in the caster's main hand: a pickaxe mines, an axe chops, a sword
     * hunts and a hoe farms. Any other item - and an empty hand - means there is no job to read, and
     * the summon is refused rather than turning into a farm hand nobody asked for.
     *
     * @return the shift the tool means, or null when the caster is not holding a tool at all
     */
    private static WorkerJob workerJob(Player player) {
        ItemStack tool = player.getMainHandItem();
        if (tool.getItem() instanceof PickaxeItem) {
            return WorkerJob.MINE;
        }
        if (tool.getItem() instanceof AxeItem) {
            return WorkerJob.CHOP;
        }
        if (tool.getItem() instanceof SwordItem) {
            return WorkerJob.HUNT;
        }
        if (tool.getItem() instanceof HoeItem) {
            return WorkerJob.FARM;
        }
        return null;
    }

    /**
     * Sneaking turns a clone summon into a worker summon - no talisman to hold, no item to craft,
     * just the shift key held as the seal is made. The meditation is never a worker, it only ever
     * sits still.
     */
    private boolean summoningWorkers(Player player) {
        return !this.settings.meditating() && player.isShiftKeyDown();
    }

    /**
     * Spreads the clones out in a ring around the caster, closing the ring in if there is no room.
     */
    private void positionClone(ShadowCloneEntity clone, ServerLevel level, Player player, int index, int total) {
        double angle = (Math.PI * 2.0d / total) * index;

        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.sin(angle) * radius;
            double z = player.getZ() + Math.cos(angle) * radius;
            clone.moveTo(x, player.getY(), z, player.getYRot(), player.getXRot());
            if (level.noCollision(clone, clone.getBoundingBox())) {
                break;
            }
        }

        Vec3 velocity = player.getLookAngle().scale(0.05d);
        clone.setDeltaMovement(velocity.x, 0.0d, velocity.z);
    }

    @Override
    public int getCooldown() {
        return this.settings.cooldown();
    }

    /**
     * How many clones this cast produces. A worker summon raises the technique's own number - the
     * shadow clone posts one worker, the multi posts a squad - and a fighter summon scales with
     * mastery: the single clone techniques hand out one clone at the start and add another as they
     * are mastered, up to {@link Settings#masteryMaxClones()}, and the multi version starts as a
     * handful and grows to a small army.
     */
    private int cloneCount(Player player, INinjaData ninjaData) {
        int low = this.settings.minClones();
        int high = this.settings.maxClones();
        int mastered = this.settings.masteryMaxClones();
        if (mastered > high) {
            float mastery = Mastery.level(ninjaData, this);
            low += Math.round((mastered - low) * mastery);
            high += Math.round((mastered - high) * mastery);
        }
        if (high < low) {
            high = low;
        }
        return low + player.getRandom().nextInt(high - low + 1);
    }

    /**
     * A clone summon is not held back by a timer but by the clones it already put on the field:
     * while even one of the caster's clones of that kind is still alive the technique reads as
     * though it were on a long cooldown, and the moment the last one is gone it can be cast again
     * for free. That way a ninja either has clones out or can make more, never both - a squad is
     * never stacked on top of another squad, whether they were summoned to fight or to work.
     *
     * <p>Only the meditation is timed as usual: it is a long lived state rather than a summon, and a
     * caster is meant to raise a fresh pair once the old ones have emptied out.</p>
     */
    @Override
    public boolean checkCooldown(Player player, INinjaData ninjaData, String translationKey) {
        if (this.settings.meditating()) {
            return Ability.Cooldown.super.checkCooldown(player, ninjaData, translationKey);
        }
        boolean alreadyOut = summoningWorkers(player)
                ? hasLivingClones(player, true)
                : hasLivingClones(player, false);
        if (alreadyOut) {
            int seconds = Math.max(1,
                    Mastery.reduceCooldown(ninjaData, translationKey, COMBAT_CLONE_HOLD_SECONDS * 20) / 20);
            player.displayClientMessage(Component.translatable("jutsu.fail.cooldown",
                    Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW),
                    Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.YELLOW)
            ), true);
            return true;
        }
        return false;
    }

    /**
     * A clone summon deliberately leaves no timer behind: the presence of its clones is the whole
     * cooldown, see {@link #checkCooldown}. Only the meditation is timed as usual.
     */
    @Override
    public void registerCooldown(Player player, INinjaData ninjaData, String translationKey) {
        if (this.settings.meditating()) {
            Ability.Cooldown.super.registerCooldown(player, ninjaData, translationKey);
        }
    }

    /**
     * Whether any clone this caster summoned of the given kind is still standing. Workers are left
     * where they were posted, so the search has to be wide enough to find one that has been left
     * behind at a worksite; fighters follow their caster and are always close at hand.
     */
    private static boolean hasLivingClones(Player player, boolean workers) {
        return !player.level().getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(CLONE_SCAN),
                clone -> clone.isOwnedBy(player) && clone.isWorker() == workers
                        && !clone.isMeditating()).isEmpty();
    }
}
