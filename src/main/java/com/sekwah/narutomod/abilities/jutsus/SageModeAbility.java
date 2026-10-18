package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Sage mode: the gathered nature energy is opened up and burned, turning the caster into something
 * faster, stronger and harder to put down for as long as it lasts.
 *
 * <p>The mode is not on a timer, it is on the pool. Every tick it takes nature energy, and when the
 * pool is empty the mode closes on its own - so how long a sage lasts is decided entirely by how long
 * they stood still beforehand, which is the trade the whole branch of techniques is built on. Death
 * and logging out also end it: the state is deliberately kept out of the save file.</p>
 *
 * <p>The maintenance lives in {@link #maintain(Player, INinjaData)}, called every tick from
 * {@code PlayerEvents#onEntityUpdate}, in the same way the eight gates are kept alive.</p>
 */
public class SageModeAbility extends Ability {

    /** V B V */
    public static final long COMBO = 232L;

    /** The registry id of this technique, which is also the key its mastery is stored under. */
    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "sage_mode");

    /** Nature energy needed to open the mode at all. */
    public static final float MIN_SENIN = 50.0f;

    /** Nature energy burned every tick the mode is up at full mastery (a full 100 is a bit over four minutes). */
    private static final float DRAIN_PER_TICK = 0.02f;

    /**
     * How much faster a beginner burns through nature energy. Holding it is a skill: someone who has
     * barely opened the mode once cannot keep it steady and it slips away quickly, while a master
     * holds the same pool for many times as long. This is the progression the mode lives on - the
     * duration grows with every use, on top of the perks below unlocking one by one.
     *
     * <p>At twelve times the base burn, a full hundred energy is about twenty seconds of mode for a
     * beginner and half that off the fifty it takes to open at all, which is the ten to twenty second
     * window the mode is meant to sit in until it is practised.</p>
     */
    private static final float BEGINNER_DRAIN_MULTIPLIER = 12.0f;

    /** Chakra the mode feeds its caster every tick, from a trickle at the start to a steady flow. */
    private static final float CHAKRA_PER_TICK_MIN = 0.15f;
    private static final float CHAKRA_PER_TICK_MAX = 0.5f;

    /** How long the effects are handed out for; refreshed every tick, so this is just a hold time. */
    private static final int EFFECT_DURATION = 200;

    /**
     * Mastery at which each of the mode's perks first appears, and where the two that grow get their
     * second step. A beginner who barely holds the mode gets nothing but a little speed and a slower
     * burn; strength, then toughness, then the leap and finally regeneration are all earned by using
     * it. This is deliberate - opening the gate for the first time should not hand over the whole
     * list at once.
     */
    private static final float SPEED_UP_AT = 0.6f;
    private static final float STRENGTH_AT = 0.15f;
    private static final float STRENGTH_UP_AT = 0.6f;
    private static final float RESISTANCE_AT = 0.35f;
    private static final float JUMP_AT = 0.5f;
    private static final float REGENERATION_AT = 0.7f;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.sageModeEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sage.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ninjaData.isSageMode()) {
            // The mode is let go the same way it is opened. There is nothing to pay for putting it
            // down, so the recast always goes through and performServer closes it.
            return true;
        }
        if (ninjaData.getSeninMana() < MIN_SENIN) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senin.notenough",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW),
                    (int) MIN_SENIN).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ninjaData.isSageMode()) {
            end(player, ninjaData);
            return;
        }
        ninjaData.setSageMode(true);
        player.displayClientMessage(Component.translatable("jutsu.sage.start").withStyle(ChatFormatting.GREEN), true);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    /**
     * The opening and the closing both say themselves, so the generic "you cast X" line is dropped to
     * keep the recast that lets go of the mode from reading as if it had just cast it.
     */
    @Override
    public boolean logInChat() {
        return false;
    }

    /**
     * Closes the mode and takes its perks back off. Used both when the pool finally runs dry and when
     * the caster releases it with a recast.
     */
    private static void end(Player player, INinjaData ninjaData) {
        ninjaData.setSageMode(false);
        clearPerks(player);
        player.displayClientMessage(Component.translatable("jutsu.sage.end").withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * The mode is not mastered by opening it: opening it costs nothing to master and a bare
     * activation is not the work. It is mastered by holding it, so the credit is paid tick by tick in
     * {@link #maintain} instead and this deliberately records nothing. See
     * {@link Ability#MASTERY_CREDIT_PER_TICK}.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Keeps a sage a sage: drained, fed, buffed and lit up, every server tick.
     *
     * <p>How long a sage lasts and how much they get out of the mode both ride on mastery. The burn
     * starts steep and eases off with every use, so a beginner's hold is short and a master's is long,
     * and the effects below are handed out one at a time rather than all at once. The buffs are given
     * a short hold and refreshed, and are cleared outright the moment the mode closes, so none of them
     * hang around after the pool runs dry.</p>
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        if (!ninjaData.isSageMode()) {
            return;
        }
        float mastery = Mastery.level(ninjaData, ID);
        float drain = DRAIN_PER_TICK * Mth.lerp(mastery, BEGINNER_DRAIN_MULTIPLIER, 1.0f);
        ninjaData.addSeninMana(-drain);
        if (ninjaData.getSeninMana() <= 0.0f) {
            ninjaData.setSeninMana(0.0f);
            end(player, ninjaData);
            return;
        }

        ninjaData.addChakra(Mth.lerp(mastery, CHAKRA_PER_TICK_MIN, CHAKRA_PER_TICK_MAX));
        applyPerks(player, mastery);
        // Mastery is paid for the time the mode is actually held, not for the act of opening it.
        NarutoAbilities.SAGE_MODE.get().recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                    player.getY() + 1.0d, player.getZ(), 4, 0.4d, 0.7d, 0.4d, 0.02d);
        }
    }

    /**
     * Hands out the perks of the mode that the caster has earned so far. Speed is always there, the
     * rest arrive as mastery grows, and the two that scale step up a second time later on.
     */
    private static void applyPerks(Player player, float mastery) {
        int speed = mastery >= SPEED_UP_AT ? 1 : 0;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, speed, true, false));
        if (mastery >= STRENGTH_AT) {
            int strength = mastery >= STRENGTH_UP_AT ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, strength, true, false));
        }
        if (mastery >= RESISTANCE_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false));
        }
        if (mastery >= JUMP_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_DURATION, 0, true, false));
        }
        if (mastery >= REGENERATION_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_DURATION, 0, true, false));
        }
    }

    /** Takes every buff of the mode back off when it ends. */
    private static void clearPerks(Player player) {
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.REGENERATION);
    }
}
