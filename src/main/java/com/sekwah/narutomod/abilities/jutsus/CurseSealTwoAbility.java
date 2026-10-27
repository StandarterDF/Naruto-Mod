package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The second stage of the curse seal: the body is no longer merely pushed, it is remade, and the
 * price is paid out of the caster's stomach.
 *
 * <p>It can only be worn on top of the first stage, so it is the same decision taken one step
 * further: more strength, a leap the body should not manage, and a fall that barely hurts - while
 * the healing that might undo the damage is cut to a quarter. The hunger is the other half of the
 * trade: a body burning that hot has to be fed, and one that is not fed quickly runs dry.</p>
 *
 * <p>Because the prerequisite is checked on every tick, closing the first stage closes this one with
 * it rather than leaving a stronger seal running on nothing.</p>
 */
public class CurseSealTwoAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The seal is a quiet thing; nothing about opening it carries through the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /**
     * The second stage has no key of its own: it is reached by pressing the first stage's key again,
     * see {@link CurseSealOneAbility#onRetoggle}.
     */
    public static final long COMBO = -1L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "curse_seal_two");

    /** The mark that says the second stage is open, refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:curse_seal_two_until";

    /** Tick the flight is kept; the seal's wings lift the bearer while the mark is up. */
    public static final String FLIGHT_TAG = "narutomod:curse_seal_two_flight";
    private static final String MAYFLY_TAG = "mayfly";
    private static final String FLYING_TAG = "flying";

    /**
     * The mark that carries the slow fall. The mark itself does nothing; the listener in
     * {@code ModeSealEvents} turns it into the effect while it is in the future, so the gift appears
     * and vanishes with the seal.
     */
    public static final String SLOW_FALL_TAG = "narutomod:curse_seal_two_slow_fall";

    /** How much of any healing reaches a body holding both stages: a quarter of what it should. */
    public static final float HEALING_FACTOR = 0.25f;

    /** Chakra paid for every tick the second stage stays open, before mastery. */
    private static final float UPKEEP = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * Hunger taken per tick, in exhaustion. Vanilla charges a quarter of this for a sprinting step,
     * so the seal empties a full hunger bar over a few minutes of use rather than starving its
     * caster in seconds.
     */
    private static final float HUNGER_PER_TICK = 0.05f;

    /** How often the boons are handed out again, and how long each hold lasts. */
    private static final int EFFECT_INTERVAL = 20;
    private static final int EFFECT_DURATION = 40;

    /** Slow fall is renewed by the mark's listener every half second, with a short hold. */
    public static final int SLOW_FALL_RENEW_TICKS = 10;
    public static final int SLOW_FALL_DURATION = 20;

    /** How far ahead the marks are kept while the seal is held. */
    private static final int MARK_GRACE = 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (player.getPersistentData().getLong(CurseSealOneAbility.UNTIL_TAG) <= player.level().getGameTime()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.curse_seal.one.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }

        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        player.causeFoodExhaustion(HUNGER_PER_TICK);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A seal is mastered by wearing it, not by opening it.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        long now = player.level().getGameTime();
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;
        if (opening || player.tickCount % EFFECT_INTERVAL == 0) {
            applyBoons(player);
        }
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);
        player.getPersistentData().putLong(SLOW_FALL_TAG, now + MARK_GRACE);

        // The seal's wings lift the body: creative flight while the mark is up, shift to descend.
        if (player.isShiftKeyDown()) {
            releaseFlight(player);
        } else {
            grantFlight(player);
        }

        if (player.level() instanceof ServerLevel level) {
            if (opening) {
                level.sendParticles(ParticleTypes.SOUL, player.getX(),
                        player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                        40, 0.6d, 0.9d, 0.6d, 0.03d);
                level.playSound(null, player, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.9f, 0.7f);
            } else if (player.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.SOUL, player.getX(),
                        player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                        3, 0.35d, 0.5d, 0.35d, 0.01d);
            }
        }
    }

    /** The second stage hits harder, moves faster and leaps further than a body should. */
    private static void applyBoons(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_DURATION, 1, true, false));
    }

    /** A seal is mastered by the time it is worn, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The boons are vanilla effects and the slow fall is a vanilla effect too; both are synced.
    }

    /** Closing the seal takes its marks and its boons back off at once. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        player.getPersistentData().remove(SLOW_FALL_TAG);
        releaseFlight(player);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.SLOW_FALLING);
    }

    /** Hands the caster their wings, remembering what they had before so the seal can give it back. */
    private static void grantFlight(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(FLIGHT_TAG)) {
            CompoundTag saved = new CompoundTag();
            saved.putBoolean(MAYFLY_TAG, player.getAbilities().mayfly);
            saved.putBoolean(FLYING_TAG, player.getAbilities().flying);
            tag.put(FLIGHT_TAG, saved);
        }
        if (!player.getAbilities().mayfly || !player.getAbilities().flying) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    /** Puts the flight state back exactly as it was before the seal. Safe when nothing was lent. */
    private static void releaseFlight(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(FLIGHT_TAG)) {
            return;
        }
        CompoundTag saved = tag.getCompound(FLIGHT_TAG);
        tag.remove(FLIGHT_TAG);
        player.getAbilities().mayfly = saved.getBoolean(MAYFLY_TAG);
        player.getAbilities().flying = saved.getBoolean(FLYING_TAG);
        player.onUpdateAbilities();
    }
}
