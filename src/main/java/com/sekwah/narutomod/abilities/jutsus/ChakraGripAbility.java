package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chakra Grip: an open eye and an outstretched hand hold a body where it stands.
 *
 * <p>It is the unglamorous half of the Gentle Fist. The needles and the palm both need the target to
 * come to the caster, and this is what makes them come: whoever is caught is pinned in place for as
 * long as the caster can pay for it, with the chakra in their own limbs weighted down and, if they
 * are a ninja, leaking out of them a little every tick. Nothing is damaged. What the hold really
 * costs the target is the three seconds they would have spent closing the distance.</p>
 *
 * <p>The catch is made once, when the hand closes, and kept for the whole hold - looking away does
 * not let go, so the caster is free to keep watching the fight rather than the victim. It is dropped
 * when the caught body dies or tears itself more than {@link #RANGE} blocks away, and it cannot be
 * held longer than {@link #MAX_TICKS}: a pin that could last forever would be a different technique
 * altogether.</p>
 */
public class ChakraGripAbility extends Ability implements Ability.Channeled {

    /** A held body rattles with chakra; the grip is not silent, but it is not a shout either. */
    @Override
    public int noiseRadius() {
        return 6;
    }

    /** V B V V B V - the third seal of the branch, one key on from the palm. */
    public static final long COMBO = 3333L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_grip");

    /** How far the hand can close around a body. */
    private static final double RANGE = 8.0d;

    /** What closing the hand costs, and what every tick of holding costs on top. */
    private static final float BASE_COST = 30.0f;
    private static final float COST_PER_TICK = 6.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long a body can be pinned, in ticks. There is no cooldown: the upkeep is the price. */
    private static final int MAX_TICKS = 60;

    /** Slowness given to the caught body, and how long each helping lasts before it is renewed. */
    private static final int SLOW_AMPLIFIER = 3;
    private static final int SLOW_TICKS = 10;

    /** Chakra taken out of a ninja target every tick. */
    private static final float DRAIN_PER_TICK = 4.0f;

    /** Motes drawn in a ring around the caught body. */
    private static final int RING_POINTS = 8;

    /**
     * The body each caster is holding, by uuid rather than by reference: the ability is a registry
     * singleton, so this map is the one place a hold lives and it is emptied when the hold ends.
     */
    private final Map<UUID, UUID> grips = new HashMap<>();

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** The hand has to close and stay closed; a tap grips for no time at all. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The hold has run its length: end it here so the last tick is not paid for a pin that has
        // already been released.
        if (chargeAmount >= MAX_TICKS) {
            return false;
        }

        if (chargeAmount == 0) {
            LivingEntity target = resolve(player);
            if (target == null) {
                player.displayClientMessage(Component.translatable("jutsu.fail.chakra_grip.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            if (!pay(player, ninjaData, true)) {
                return false;
            }
            this.grips.put(player.getUUID(), target.getUUID());
            return true;
        }

        // A tick of a hold whose catch is already gone (a reload, a hold started before the server
        // knew about it) has nothing to grip: stop rather than charge for it.
        if (!this.grips.containsKey(player.getUUID())) {
            return false;
        }
        return pay(player, ninjaData, false);
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = this.heldTarget(player, level);
        if (target == null) {
            this.release(player, ninjaData);
            return;
        }

        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SLOW_TICKS, SLOW_AMPLIFIER, false, true));
        drain(target);
        drawRing(level, target);

        // Only on the opening tick: the beacon's hum is the sound of the hand closing, not of the
        // body staying caught, and repeating it would drown out the rest of the fight.
        if (ticksChanneled == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.35f, 1.5f);
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.grips.remove(player.getUUID());
    }

    /**
     * A channel is mastered by the time the body is actually held, so the credit is paid per active
     * tick from {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** Works out what the hand closes around: a living thing in the crosshair, if it may be held. */
    @Nullable
    private LivingEntity resolve(Player player) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)) {
            return null;
        }
        return canHold(player, target) ? target : null;
    }

    /**
     * The catch of the current hold, re-read from the world every tick so a body that died, left the
     * dimension or tore itself free is noticed at once.
     */
    @Nullable
    private LivingEntity heldTarget(Player caster, ServerLevel level) {
        UUID targetId = this.grips.get(caster.getUUID());
        if (targetId == null) {
            return null;
        }
        Entity entity = level.getEntity(targetId);
        if (!(entity instanceof LivingEntity target)
                || target.isRemoved()
                || target.isSpectator()
                || !target.isAlive()
                || target.level() != caster.level()
                || target.distanceToSqr(caster) > RANGE * RANGE) {
            return null;
        }
        return target;
    }

    /** Ends a hold the world broke rather than the caster: the upkeep stops this same tick. */
    private void release(Player player, INinjaData ninjaData) {
        this.grips.remove(player.getUUID());
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    /** Charges one tick of the hold, or says why it cannot be paid for. */
    private boolean pay(Player player, INinjaData ninjaData, boolean opening) {
        float cost = this.masteryCost(ninjaData,
                opening ? BASE_COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /** Spills a held ninja's chakra. Anything without a pool is left to the slowness. */
    private static void drain(LivingEntity target) {
        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(DRAIN_PER_TICK, data.getChakra());
            if (lost > 0.0f) {
                data.useChakra(lost, CHAKRA_REGEN_DELAY);
            }
        });
    }

    /** A ring of sparks at the held body, so the pin is visible to both sides of it. */
    private static void drawRing(ServerLevel level, LivingEntity target) {
        double radius = Math.max(0.5d, target.getBbWidth() * 0.8d);
        double y = target.getY() + target.getBbHeight() * 0.5d;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0d / RING_POINTS * i;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    target.getX() + Math.cos(angle) * radius, y,
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canHold(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
