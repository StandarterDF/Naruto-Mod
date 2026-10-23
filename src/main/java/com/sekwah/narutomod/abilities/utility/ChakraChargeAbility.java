package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.effect.NarutoEffects;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Sitting on the chakra and pulling more of it in.
 *
 * <p>It is the reliable way to refill, not a fast one: standing still gathers at
 * {@link #STAND_RATE} per tick, moving at {@link #MOVING_RATE}, so a fight that empties the bar
 * costs the caster real time at a safe spot. What it does give back is the flow: the moment the bar
 * fills it hands over a short effect that makes the passive trickle run several times faster, so
 * charging is a preparation for the next fight rather than a chore in itself. See
 * {@code NinjaData#updateDataServer} for the other half of that.</p>
 *
 * <p>Mastery also widens the pool: each level adds a share of {@link #MASTERY_CHAKRA_BONUS}, so a
 * caster who has practised the technique ends up with twenty-five points more maximum chakra than
 * the day they started. It is not a stance or a buff - mastery is saved with the player, so the
 * extra capacity is simply theirs now.</p>
 */
public class ChakraChargeAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** Chakra gathered per tick while the caster stands still. */
    public static final float STAND_RATE = 0.5f;

    /** Chakra gathered per tick while the caster moves - quarter of the standing rate. */
    public static final float MOVING_RATE = 0.1f;

    /** How long the afterglow of a full bar lasts, in ticks. */
    public static final int FLOW_TICKS = 20 * 15;

    /**
     * The id mastery is tracked under. Spelled out here instead of asking the registry, so the
     * capacity bonus below can be worked out from {@code NinjaData#getMaxChakra} without touching
     * the ability registry (and without a class initialisation loop through {@code NarutoAbilities}).
     */
    public static final ResourceLocation ABILITY_ID =
            new ResourceLocation(NarutoMod.MOD_ID, "chakra_charge");

    /**
     * Capacity this technique hands over at full mastery. It is not a buff that has to be kept up:
     * mastery is saved with the caster, so once it is earned the extra maximum stays for good, even
     * through death and logging out.
     */
    public static final float MASTERY_CHAKRA_BONUS = 25.0f;

    /**
     * The part of {@link #MASTERY_CHAKRA_BONUS} already earned, linear in mastery - a technique that
     * restores chakra also teaches the body to hold more of it. Called from
     * {@code NinjaData#getMaxChakra()}, which is the single place every clamp reads from.
     */
    public static float masteryChakraBonus(INinjaData ninjaData) {
        return MASTERY_CHAKRA_BONUS * Mastery.level(ninjaData, ABILITY_ID);
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return 1;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // This jutsu can always be cast
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Add particle effects n stuff
    }


    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        float rate = (player.isSprinting() || !player.onGround()) ? MOVING_RATE : STAND_RATE;
        // The endurance branch speeds the gathering up: a trained caster pulls more out of the same
        // standing still, on top of the mastery that shortens everything else.
        rate *= 1.0f + SkillTree.bonus(ninjaData.getSkillData().getUnlocked(),
                SkillEffect.CHAKRA_CHARGE_RATE);
        // Mastery is earned by the chakra this actually put back into the bar, so standing still and
        // charging a full bar is a real piece of practice while tapping the keys on a full bar is
        // worth nothing. The gain is read back off the bar rather than assumed, so chakra that never
        // fit - a full or nearly full bar - does not count as work.
        float before = ninjaData.getChakra();
        float max = ninjaData.getMaxChakra();
        ninjaData.addChakra(rate);
        float restored = ninjaData.getChakra() - before;
        this.recordMasteryCredit(player, ninjaData, restored / Math.max(1f, max));
        // The fill itself is the reward: only the tick that actually closes the gap grants the
        // afterglow, so holding the keys on a full bar is quiet and does not renew it.
        if (before < max && ninjaData.getChakra() >= max) {
            this.grantFlow(player, ninjaData);
        }
    }

    /** A chime, a small flare and the effect that makes the passive trickle faster for a while. */
    private void grantFlow(Player player, INinjaData ninjaData) {
        if (!(player.level() instanceof ServerLevel level) || !NarutoConfig.chakraFlowEnabled) {
            return;
        }
        player.addEffect(new MobEffectInstance(NarutoEffects.CHAKRA_FLOW.get(), FLOW_TICKS, 0,
                true, true, true));
        level.playSound(null, player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.6f, 1.4f);
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0d, player.getZ(),
                    1, 0.4d, 0.6d, 0.4d, 0.02d);
        }
    }

    /**
     * A channelled gathering technique is not mastered by how often it is cast but by the chakra it
     * restored, which {@link #handleChannelling} already paid in - so the end-of-cast credit that
     * every other technique gets is deliberately left empty here.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }
}
