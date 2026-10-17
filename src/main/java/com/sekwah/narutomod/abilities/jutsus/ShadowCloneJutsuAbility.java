package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.WorkerJob;
import com.sekwah.narutomod.item.NarutoItems;
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
import net.minecraft.world.phys.Vec3;

/**
 * Summons copies of the caster which fight for them, see {@link ShadowCloneEntity}.
 *
 * <p>The same class backs all three versions of the technique (normal, chibi and multi shadow
 * clone), they only differ in their {@link Settings}.</p>
 *
 * <p>With the Worker's Seal ({@code narutomod:worker_seal}) in the off hand, the very same
 * techniques summon workers instead of fighters: the clones come out in the usual numbers but walk
 * off to farm, mine or chop around themselves, and their tool - the item in the caster's main hand -
 * decides which of the three it is. See {@link WorkerJob}.</p>
 */
public class ShadowCloneJutsuAbility extends Ability implements Ability.Cooldown {

    /**
     * Tunables for one version of the technique.
     *
     * <p>{@code combo} uses the same hand seal encoding as the rest of the mod, where the digits
     * match the jutsu keys (1 = key 1, 2 = key 2, 3 = key 3). These are the combos from the 1.7.10
     * mod: cbbv for the shadow clone, cbvv for the chibi version and cbbvbc for the multi version.</p>
     */
    public record Settings(long combo, float chakraCost, int minClones, int maxClones, int lifetime,
                           boolean chibi, boolean meditating, double attackDamage, double movementSpeed,
                           int cooldown, int chakraRegenDelay) {}

    /** C B B V - a couple of copies of yourself. */
    public static final Settings SHADOW_CLONE_SETTINGS =
            new Settings(1332L, 40f, 2, 3, 20 * 25, false, false, 2.0d, 0.3d, 20 * 3, 20 * 2);

    /** C B V V - cheap, small and weak, but you get a handful of them. */
    public static final Settings CHIBI_SHADOW_CLONE_SETTINGS =
            new Settings(1322L, 20f, 3, 5, 20 * 15, true, false, 1.0d, 0.35d, 20 * 2, 20);

    /** C B B V B C - the expensive one, a small army. */
    public static final Settings MULTI_SHADOW_CLONE_SETTINGS =
            new Settings(133231L, 80f, 8, 11, 20 * 25, false, false, 2.0d, 0.3d, 20 * 10, 20 * 5);

    /**
     * C B B C - two clones left behind to meditate, see
     * {@code ChakraMeditationJutsuAbility#tickAura(Player)}. They neither move nor fight (their
     * speed and damage are zero and their goals are skipped), so all they do is sit there and feed
     * the caster chakra while they last. They are the one clone that is supposed to be a long term
     * investment, so they outlive every other version of the technique by a wide margin.
     */
    public static final Settings CHAKRA_MEDITATION_SETTINGS =
            new Settings(1331L, 60f, 2, 2, 20 * 180, false, true, 0.0d, 0.0d, 20 * 60, 20 * 3);

    /** Radius of the ring the clones appear in. */
    private static final double SPAWN_RADIUS = 1.6d;

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
        // Every version of the technique burns one seal talisman. It is taken in performServer,
        // here we only make sure there is one to take so the caster is not charged for nothing.
        if (!hasSeal(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.noseal",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getChakra() < this.settings.chakraCost()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(this.settings.chakraCost(), this.settings.chakraRegenDelay());
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

        consumeSeal(player);

        // Meditation is always just meditation: the seal in hand does not turn a clone that is meant
        // to sit still into one that walks off to work. Every other technique follows the seal.
        boolean summoningWorkers = !this.settings.meditating() && isHoldingWorkerSeal(player);

        int cloneCount = this.settings.minClones()
                + player.getRandom().nextInt(this.settings.maxClones() - this.settings.minClones() + 1);

        for (int i = 0; i < cloneCount; i++) {
            int lifetime = summoningWorkers ? ShadowCloneEntity.WORKER_LIFETIME : this.settings.lifetime();
            ShadowCloneEntity clone = new ShadowCloneEntity(level, player,
                    lifetime, this.settings.chibi(), this.settings.meditating());
            clone.applyVariantAttributes(this.settings.attackDamage(), this.settings.movementSpeed());
            if (summoningWorkers) {
                clone.setWorker(true);
                clone.setWorkerHome(player.blockPosition());
                clone.setWorkerJob(workerJob(player));
                clone.setWorkerTool(player.getMainHandItem());
            }
            this.positionClone(clone, level, player, i, cloneCount);
            level.addFreshEntity(clone);
        }

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                60, 0.6d, 0.8d, 0.6d, 0.02d);
        level.playSound(null, player, NarutoSounds.SHADOW_CLONE_POOF.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Whether the caster is holding the Worker's Seal in their off hand, which turns clones into workers. */
    private static boolean isHoldingWorkerSeal(Player player) {
        return player.getOffhandItem().is(NarutoItems.WORKER_SEAL.get());
    }

    /**
     * Reads the job off the tool in the caster's main hand: a hoe farms, a pickaxe mines and an axe
     * chops. Anything else (or an empty hand) falls back to farming, which needs no tool.
     */
    private static WorkerJob workerJob(Player player) {
        ItemStack tool = player.getMainHandItem();
        if (tool.getItem() instanceof PickaxeItem) {
            return WorkerJob.MINE;
        }
        if (tool.getItem() instanceof AxeItem) {
            return WorkerJob.CHOP;
        }
        return WorkerJob.FARM;
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
}
