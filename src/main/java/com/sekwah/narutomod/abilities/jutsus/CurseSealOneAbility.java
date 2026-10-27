package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The first stage of the curse seal: the body is pushed past what it can do on its own, and pays for
 * it by losing the ability to mend.
 *
 * <p>Opening the seal is a decision made once and then lived with - a bite of health and a chunk of
 * chakra up front, a trickle after that - and what it buys is the strength, speed and toughness of a
 * body that no longer cares about its own limits. The mark it leaves behind says the seal is up, and
 * everything the seal changes about the caster is read from it: the healing penalty is applied where
 * healing happens, see {@code ModeSealEvents}.</p>
 *
 * <p>Because the mark is stamped again on every active tick, a lost seal (a death, a relog) cleans
 * itself up within the grace instead of leaving its boons on a body that no longer holds it. The
 * second stage, {@link CurseSealTwoAbility}, is built on top of this one and lets go the moment this
 * one does.</p>
 */
public class CurseSealOneAbility extends Ability implements Ability.Toggled, Ability.HandleEnded, Ability.Retoggle {

    /** The seal is a quiet thing; nothing about opening it carries through the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B - one seal, one key: the same press opens the first stage and deepens it to the second. */
    public static final long COMBO = 11123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "curse_seal_one");

    /**
     * The mark that says the first stage is open. It is refreshed every active tick and read by the
     * healing penalty, so it is the single answer to "is this seal up".
     */
    public static final String UNTIL_TAG = "narutomod:curse_seal_one_until";

    /**
     * How much of any healing reaches a body holding the first stage. The seal stops the flesh from
     * closing itself properly, so all mending lands at half strength.
     */
    public static final float HEALING_FACTOR = 0.5f;

    /** Chakra paid to force the seal open, before mastery, and the health burned with it. */
    private static final float OPEN_COST = 30.0f;
    private static final float HEALTH_COST = 4.0f;

    /** Chakra paid for every tick the seal stays open, before mastery. */
    private static final float UPKEEP = 0.25f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How often the boons are handed out again, and how long each hold lasts. */
    private static final int EFFECT_INTERVAL = 20;
    private static final int EFFECT_DURATION = 40;

    /**
     * How far ahead the mark is kept while the seal is held. It only has to outlive the tick it was
     * written on; the small grace keeps a hit that resolves a moment later inside the seal.
     */
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
        long now = player.level().getGameTime();
        // The first tick after the seal is switched on finds no mark of its own, which is exactly
        // what makes it the opening tick - and the only one that costs health.
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;

        if (opening && player.getHealth() <= HEALTH_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mode.notenoughhealth",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }

        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        if (opening) {
            // The seal is carved in the caster's own flesh, so the health is spent as real damage
            // rather than written off the bar: resistance and absorption are allowed to blunt it.
            player.hurt(player.damageSources().magic(), HEALTH_COST);
        }
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

        if (player.level() instanceof ServerLevel level) {
            if (opening) {
                level.sendParticles(ParticleTypes.WITCH, player.getX(),
                        player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                        30, 0.5d, 0.8d, 0.5d, 0.05d);
                level.playSound(null, player, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 0.8f);
            } else if (player.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.WITCH, player.getX(),
                        player.getY() + player.getBbHeight() * 0.7d, player.getZ(),
                        3, 0.35d, 0.5d, 0.35d, 0.01d);
            }
        }
    }

    /** A body under the seal is stronger, faster and harder to put down. */
    private static void applyBoons(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false));
    }

    /** A seal is mastered by the time it is worn, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The boons are vanilla effects, which the server syncs on its own.
    }

    /** Closing the seal takes its mark and its boons back off at once. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
    }

    /**
     * The same key deepens the seal instead of closing it: first stage, then second, then let go.
     * Sneaking with the key always lets go, so a caster is never stuck in a stage they no longer
     * want. The second stage is only available once its node has been learned.
     */
    @Override
    public boolean onRetoggle(Player player, INinjaData ninjaData) {
        if (player.isShiftKeyDown()) {
            return false;
        }
        if (ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(CurseSealTwoAbility.ID)) {
            return false;
        }
        if (!AbilityUnlocks.isUnlocked(ninjaData, CurseSealTwoAbility.ID)) {
            return false;
        }
        ninjaData.getToggleAbilityData().addAbilityStarted(player, ninjaData, NarutoAbilities.CURSE_SEAL_TWO.get());
        return true;
    }
}
