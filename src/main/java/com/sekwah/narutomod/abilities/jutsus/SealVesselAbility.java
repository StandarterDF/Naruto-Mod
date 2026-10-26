package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Eight Trigrams seal: the cage a tailed beast is written into, and the gate every form of its
 * chakra is read through afterwards.
 *
 * <p>Opening the seal is a ritual rather than a cast. The caster stands still for five seconds while
 * the formula is drawn around them, and stepping off the spot or crouching breaks it: the chakra is
 * already spent by then, so an interrupted ritual is a wasted one. What is left behind is a vessel - a
 * hundred points of integrity, three tails and a clock - and nothing else in this branch of techniques
 * can be used without it. Integrity wears down on its own and is spent by the forms that borrow the
 * beast's strength, so the vessel is a resource to be managed, not a permanent upgrade.</p>
 *
 * <p>Only the opening half of the ritual lives here. The ticks that follow are consequences of a mark
 * rather than a cast - the caster standing still, the ring closing in, the seal being written - and
 * those are enforced by {@code events/TailedBeastEvents}, which is also where the vessel is worn down.
 * The registry lookup in that class calls {@link #complete(Player, INinjaData)} so the cast is
 * mastered at the moment the seal takes, not at the moment the ritual is asked for.</p>
 */
public class SealVesselAbility extends Ability implements Ability.Cooldown {

    /** The formula taking hold is heard well past the caster. */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** C C B B A A - the seal that holds the beast. */
    public static final long COMBO = 332211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "seal_vessel");

    /** Points of vessel left. Anything that borrows the beast's chakra reads this first. */
    public static final String INTEGRITY_TAG = "narutomod:seal_vessel_integrity";

    /** Tick the vessel gives out on its own, whether or not it was spent. */
    public static final String UNTIL_TAG = "narutomod:seal_vessel_until";

    /** How many tails the vessel holds, written once and read by the forms built on it. */
    public static final String TAILS_TAG = "narutomod:seal_vessel_tails";

    /**
     * Tick the ritual in progress closes on. Its presence is what says a ritual is running, so a
     * ritual that is outlived by its own clock is still completed by the tick handler rather than
     * being silently forgotten.
     */
    public static final String RITUAL_UNTIL_TAG = "narutomod:seal_vessel_ritual_until";

    /**
     * Where the ritual was started, packed as a block position. A ritual is abandoned by walking away
     * from the spot rather than by any movement at all: a caster who has just stopped walking still
     * carries a little momentum, and a formula that broke on the first tick of that would cost them
     * the whole ritual for a step they had already finished.
     */
    public static final String RITUAL_ANCHOR_TAG = "narutomod:seal_vessel_ritual_anchor";

    /** The seal of this caster holds three tails. */
    public static final int TAILS = 3;

    /** A freshly written vessel is whole; everything after this is wear. */
    public static final int MAX_INTEGRITY = 100;

    /** Chakra the ritual takes before mastery: the cage itself, plus a share per sealed tail. */
    private static final float BASE_COST = 100.0f;
    private static final float COST_PER_TAIL = 30.0f;

    /** How long a written vessel lasts, and how long before the ritual may be attempted again. */
    private static final int DURATION = 6000;
    private static final int COOLDOWN = 6000;

    /** Five seconds of standing still, which is a long time in the middle of a fight. */
    public static final int RITUAL_TICKS = 100;

    /**
     * How far from the spot the ritual was started on the caster may stand, in blocks. A step to the
     * next block is a step away; a body settling on its feet is not.
     */
    private static final double RITUAL_DRIFT = 1.0d;

    /** The ring drawn on the floor around the caster while the ritual runs. */
    private static final double RING_RADIUS = 1.6d;
    private static final int RING_POINTS = 16;
    private static final int RITUAL_PARTICLE_INTERVAL = 5;

    private static final int CHAKRA_REGEN_DELAY = 20;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (hasRitual(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.ritual")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (isSealed(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.sealed")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Checked before the chakra is taken: a formula that cannot be drawn in the air must not cost
        // the ritual it cannot hold.
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.airborne")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, BASE_COST + COST_PER_TAIL * TAILS);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * Starts the ritual. The chakra is already gone at this point: what is left is a mark and a
     * countdown, and the tick handler decides whether the caster is still standing where they were.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        data.putLong(RITUAL_UNTIL_TAG, now + RITUAL_TICKS);
        data.putLong(RITUAL_ANCHOR_TAG, player.blockPosition().asLong());
        player.displayClientMessage(Component.translatable("jutsu.seal_vessel.start")
                .withStyle(ChatFormatting.GOLD), true);
        if (player.level() instanceof ServerLevel level) {
            ring(level, player, 3);
            level.playSound(null, player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
    }

    /**
     * Writes the vessel once the ritual has run its five seconds. Called by the tick handler, which
     * owns the clock; this is the moment the cast is mastered.
     */
    public void complete(Player player, INinjaData ninjaData) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        data.remove(RITUAL_UNTIL_TAG);
        data.remove(RITUAL_ANCHOR_TAG);
        data.putInt(INTEGRITY_TAG, MAX_INTEGRITY);
        data.putLong(UNTIL_TAG, now + DURATION);
        data.putInt(TAILS_TAG, TAILS);
        // A full cast's worth of credit, earned by the seal that was actually written: starting a
        // ritual that gets interrupted is not practice.
        this.recordMasteryCredit(player, ninjaData, 1.0f);

        player.displayClientMessage(Component.translatable("jutsu.seal_vessel.done")
                .withStyle(ChatFormatting.GREEN), true);
        if (player.level() instanceof ServerLevel level) {
            ring(level, player, 20);
            level.playSound(null, player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    /**
     * Drops a ritual that was broken by walking off the spot or by a crouch. The chakra is not handed
     * back: the formula was already burning when it was interrupted, which is the price of starting it
     * in the open.
     */
    public static void interrupt(Player player) {
        if (!hasRitual(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.remove(RITUAL_UNTIL_TAG);
        data.remove(RITUAL_ANCHOR_TAG);
        player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.interrupted")
                .withStyle(ChatFormatting.RED), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    16, 0.6d, 0.7d, 0.6d, 0.02d);
            level.playSound(null, player, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
    }

    /** @return whether a ritual is currently being held open, whatever its clock says. */
    public static boolean hasRitual(Player player) {
        return player.getPersistentData().contains(RITUAL_UNTIL_TAG);
    }

    /** How much of the vessel is left, which is what every form in this branch spends. */
    public static int integrity(Player player) {
        return Mth.clamp(player.getPersistentData().getInt(INTEGRITY_TAG), 0, MAX_INTEGRITY);
    }

    /** How many tails this caster's vessel holds, zero when there is no vessel. */
    public static int tails(Player player) {
        return Mth.clamp(player.getPersistentData().getInt(TAILS_TAG), 0, TAILS);
    }

    /** @return whether a written vessel is on this caster and has not yet run out. */
    public static boolean isSealed(Player player) {
        return integrity(player) > 0
                && player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** Wears the vessel down by one of the forms that borrows the beast. */
    public static void spendIntegrity(Player player, int amount) {
        CompoundTag data = player.getPersistentData();
        if (amount <= 0 || !data.contains(INTEGRITY_TAG)) {
            return;
        }
        data.putInt(INTEGRITY_TAG, Math.max(0, integrity(player) - amount));
    }

    /** Removes every trace of the vessel: used when it runs dry and when it is out of time. */
    public static void close(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(INTEGRITY_TAG);
        data.remove(UNTIL_TAG);
        data.remove(TAILS_TAG);
    }

    /**
     * @return whether the caster is still standing where the formula was started, with both feet on
     *         the ground. Crouching is not asked about here: it is how the ritual is abandoned, so the
     *         tick handler answers it separately.
     */
    public static boolean holdsRitualSpot(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RITUAL_ANCHOR_TAG) || !player.onGround()) {
            return false;
        }
        Vec3 anchor = Vec3.atCenterOf(BlockPos.of(data.getLong(RITUAL_ANCHOR_TAG)));
        return player.position().distanceToSqr(anchor) <= RITUAL_DRIFT * RITUAL_DRIFT;
    }

    /**
     * The ritual is mastered by the seal it writes, not by the asking: the cast has cost the chakra and
     * the standing still, and the credit is paid from {@link #complete(Player, INinjaData)}.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * The ritual says itself in both directions, so the generic "you cast X" line is dropped: the cast
     * line and the break line would otherwise arrive in the same breath.
     */
    @Override
    public boolean logInChat() {
        return false;
    }

    /** One turn of the ritual: enchantment motes set at the points of a ring around the caster. */
    public static void drawRitual(ServerLevel level, Player player) {
        if (player.tickCount % RITUAL_PARTICLE_INTERVAL != 0) {
            return;
        }
        ring(level, player, 1);
    }

    private static void ring(ServerLevel level, Player player, int count) {
        double y = player.getY() + player.getBbHeight() * 0.5d;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0d * i / RING_POINTS;
            level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX() + Math.cos(angle) * RING_RADIUS,
                    y,
                    player.getZ() + Math.sin(angle) * RING_RADIUS,
                    count, 0.05d, 0.25d, 0.05d, 0.01d);
        }
    }
}
