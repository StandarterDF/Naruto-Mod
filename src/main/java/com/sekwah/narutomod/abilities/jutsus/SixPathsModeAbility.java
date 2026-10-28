package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Six Paths mode: the sage's body opened all the way, for a minute at a time.
 *
 * <p>This is not another eye and not another element. It is what a Rinnegan is for: the nature
 * energy a sage spends a whole fight gathering is poured out at once, and for one minute the caster
 * stops being bound by the ground, by wounds, by poison or by an illusion. The price is the whole
 * ritual pool - a hundred nature energy, spent in the moment the mode opens - and a chakra upkeep on
 * top of it, so the mode never becomes something a ninja simply leaves on.</p>
 *
 * <p>The mode carries nine Truth-Seeking Orbs. They are not items: each is a charge written on the
 * caster and spent by the three techniques built on them - {@link GudodamaLaunchAbility},
 * {@link GudodamaGuardAbility} and {@link GudodamaFormAbility}. When the last one is gone the mode
 * closes with it; running dry is a defeat, not a refresh. The tags and the flight, the healing and
 * the immunities are enforced in {@code events/SixPathsEvents}, because those are consequences of
 * the mode being up rather than things the ability does on a cast.</p>
 *
 * <p>Once opened, the mode runs on its own clock. The Rinnegan is the key that turns it, not a
 * tether that has to be held open for the whole minute: the ritual already took what the eye was
 * for.</p>
 */
public class SixPathsModeAbility extends Ability
        implements Ability.Toggled, Ability.HandleEnded, Ability.Cooldown {

    /** Opening the mode is a beacon of chakra; the whole field hears it. */
    @Override
    public int noiseRadius() {
        return 24;
    }

    /** B B B B C C - the Six Paths seal, the branch's last key. */
    public static final long COMBO = 21333L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "six_paths_mode");

    /** Nature energy the ritual takes at once. A sage has to stand still for this. */
    private static final float RITUAL_SENIN = 100.0f;

    /** Chakra burnt for every tick the mode stays open, on top of the ritual. */
    private static final float CHAKRA_PER_TICK = 1.0f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** A minute, and then the body is only a body again. */
    private static final int DURATION = 1200;

    /** Five minutes before the ritual can be attempted again. */
    private static final int COOLDOWN = 6000;

    /** The mode's own gift on top of the eye's: a far larger pool to spend while it lasts. */
    private static final float BONUS_CHAKRA = 100.0f;

    /** How many Truth-Seeking Orbs the mode hands out. */
    public static final int MAX_ORBS = 9;

    /** Tick the mode closes on its own. */
    public static final String UNTIL_TAG = "narutomod:six_paths_until";

    /** Tick the mode was opened on, kept so anything that looks back can see how long it has run. */
    public static final String STARTED_TAG = "narutomod:six_paths_started";

    /** Orbs left, spent by the three orb techniques. */
    public static final String ORBS_TAG = "narutomod:six_paths_orbs";

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /**
     * Refuses the switch-on before the stance is ever added, so a ritual that cannot be paid leaves
     * no toggle, no mark and no cooldown behind. Without this the mode was switched on first and
     * checked a tick later, and a refused attempt looked like it had opened for one tick before
     * closing.
     */
    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        return hasStarted(player) || checkRitual(player, ninjaData);
    }

    /** The one-off price of opening: the eye, the wait since the last ritual and the nature energy. */
    private boolean checkRitual(Player player, INinjaData ninjaData) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        if (ninjaData.getSeninMana() < RITUAL_SENIN) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senin.notenough",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW),
                    (int) RITUAL_SENIN).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!hasStarted(player) && !checkRitual(player, ninjaData)) {
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!hasStarted(player)) {
            open(player, ninjaData);
        }
        // The mode is lived in, not cast in a moment, so mastery is paid per tick it is really up.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        // The eye that opened the mode hands out a smaller pool bonus of its own every tick. Asserting
        // the larger one here keeps the mode from quietly losing it whenever the upkeep order favours
        // the eye; the comparison means a bigger bonus from any other source is never cut down.
        ninjaData.setEyeChakraBonus(Math.max(ninjaData.getEyeChakraBonus(), BONUS_CHAKRA));
    }

    /** Pours the collected ritual out in one go: the pool, the tags, the orbs and the arena-wide light. */
    private static void open(Player player, INinjaData ninjaData) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        data.putLong(UNTIL_TAG, now + DURATION);
        data.putLong(STARTED_TAG, now);
        data.putInt(ORBS_TAG, MAX_ORBS);
        ninjaData.setSixPathsOrbs(MAX_ORBS);        ninjaData.setSeninMana(ninjaData.getSeninMana() - RITUAL_SENIN);
        ninjaData.setEyeChakraBonus(BONUS_CHAKRA);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    80, 0.9d, 1.1d, 0.9d, 0.12d);
            level.sendParticles(ParticleTypes.SQUID_INK, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    24, 1.2d, 0.6d, 1.2d, 0.02d);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }

    /**
     * A stance is mastered by holding it, so the credit is paid from {@link #performServer} and this
     * records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Only state and tags are kept; the client reads them through the ordinary sync.
    }

    /**
     * Closing the mode takes the ritual's leftovers with it: the tags, the orbs and the pool.
     *
     * <p>The cooldown belongs to a ritual that actually happened. The toggle is switched on first
     * and paid for on the next tick, so a cast refused for a missing eye, an empty nature-energy
     * pool or a lack of chakra also ends here - and charging it the five minutes anyway turned one
     * bad moment into a lockout, which every further attempt then refreshed. Only a mode that was
     * really opened earns the wait.</p>
     */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        boolean wasOpened = hasStarted(player);
        clear(player, ninjaData);
        if (wasOpened) {
            // The minute was the whole allowance; a second ritual has to wait for the eye to recover.
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }
    }

    /**
     * Removes every trace of the mode from a caster. Public because the event class also has to be
     * able to close a mode it found running without a stance behind it - a relog, a death, a stale
     * tag - and it must not be forced to go through {@link #handleAbilityEnded} to pay a cost.
     */
    public static void clear(Player player, INinjaData ninjaData) {
        CompoundTag data = player.getPersistentData();
        data.remove(UNTIL_TAG);
        data.remove(STARTED_TAG);
        data.remove(ORBS_TAG);
        ninjaData.setSixPathsOrbs(0);
        // If the eye is still open its own upkeep writes its bonus back this same tick, so the pool
        // never dips on the way out. With no eye left there is nothing to keep.
        if (!RinneganAbility.isActive(ninjaData)) {
            RinneganAbility.clearBonus(ninjaData);
        }
    }

    /** @return whether the ritual has already been poured out, whether or not the mark is still live. */
    public static boolean hasStarted(Player player) {
        return player.getPersistentData().contains(UNTIL_TAG);
    }

    /** @return whether the mode is up this instant, read from its own clock. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return the orbs the caster has left, clamped to the mode's own allowance. */
    public static int orbs(Player player) {
        return Mth.clamp(player.getPersistentData().getInt(ORBS_TAG), 0, MAX_ORBS);
    }

    /** Writes the orb count, keeping any other code from putting more out than the mode gave. */
    public static void setOrbs(Player player, int orbs) {
        int clamped = Mth.clamp(orbs, 0, MAX_ORBS);
        player.getPersistentData().putInt(ORBS_TAG, clamped);
        player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .ifPresent(ninjaData -> ninjaData.setSixPathsOrbs(clamped));
    }

    /** Spends one orb and @return how many are left. */
    public static int spendOrb(Player player) {
        int left = orbs(player) - 1;
        setOrbs(player, left);
        return Math.max(0, left);
    }
}
