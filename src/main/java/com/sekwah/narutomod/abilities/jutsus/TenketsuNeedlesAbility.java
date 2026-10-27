package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tenketsu Needles: three thin darts of chakra pushed through the channels of whoever the caster is
 * looking at.
 *
 * <p>The Gentle Fist closes a body's tenketsu with a palm, which means standing within arm's reach of
 * it. This is the same strike thrown instead of dealt: the caster holds the seal, the eye reads the
 * channels, and every eighth tick one needle leaves the fingers along the line of sight. A needle
 * barely cuts - it is not meant to - but it takes chakra out of a ninja and seals the flow for a
 * moment, so three of them can end a fight before the other body closes the distance.</p>
 *
 * <p>The hold is spent after the third needle. Letting it run on would only keep charging for a
 * technique that has nothing left to fire, so the channel ends itself there: see
 * {@link #MAX_NEEDLES} and the {@code chargeAmount} guard in {@link #handleCost}.</p>
 */
public class TenketsuNeedlesAbility extends Ability implements Ability.Channeled, Ability.Cooldown {

    /** Three needles leaving the hand are as loud as a bowstring, no more. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** V B V V B B - the Byakugan, then two keys for the thrown strike. */
    public static final long COMBO = 3223L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tenketsu_needles");

    /** How far down the look ray a needle can find its channel. Walls stop it, as they stop every eye. */
    private static final double RANGE = 16.0d;

    /** What opening the seal costs, and what every tick of holding it costs on top. */
    private static final float BASE_COST = 35.0f;
    private static final float COST_PER_TICK = 4.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the eye needs before it can throw another three, in ticks. */
    private static final int COOLDOWN = 240;

    /** Ticks of holding between two needles, and how many fit in one hold. */
    private static final int TICKS_PER_NEEDLE = 8;
    private static final int MAX_NEEDLES = 3;

    /** A needle does not cut deep, so a body can take a few and still stand. */
    private static final float NEEDLE_DAMAGE = 2.0f;

    /** Chakra taken out of a ninja target by one needle, capped by what they actually have. */
    private static final float NEEDLE_DRAIN = 5.0f;

    /**
     * How long a needle closes the channels, and the tag it is written to. Shorter than the palm's
     * seal because three needles land in a couple of seconds and the seals stack in time, not in
     * strength - see {@code ByakuganEvents} for the tag's owner and its reader.
     */
    private static final int SEAL_TICKS = 30;
    private static final String SEAL_UNTIL_TAG = "narutomod:tenketsu_until";

    /** How many motes are drawn between the hand and the struck body. */
    private static final int LINE_POINTS = 12;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /** There is no needle without the seal being held; a tap has nowhere to aim. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The cooldown is asked on the way in only. An open hold must not be cut short by a timer
        // that the hold itself started.
        if (chargeAmount <= 0
                && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
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
        // The third needle is the last: ending the channel here stops the upkeep at the same moment,
        // instead of letting the caster pay for a hold that can no longer do anything.
        if (chargeAmount >= TICKS_PER_NEEDLE * MAX_NEEDLES) {
            return false;
        }

        // The seal itself is paid for once, when the hold opens; the needles are paid for by the tick.
        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? BASE_COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        // The first needle flies on the eighth tick of the hold, not on the key press, so a caster
        // who lets go at once has thrown nothing and paid only the seal.
        if ((ticksChanneled + 1) % TICKS_PER_NEEDLE != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target
                && canStrike(player, target)) {
            strike(level, player, target);
        }
        if (needlesSpent(ticksChanneled) >= MAX_NEEDLES) {
            player.displayClientMessage(Component.translatable("jutsu.tenketsu_needles.spent",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                    true);
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A hold that got as far as a needle is a cast and carries the cooldown; one that was let go
        // before the eighth tick only cost the chakra it burnt.
        if (ticksActive >= TICKS_PER_NEEDLE - 1) {
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }
    }

    /**
     * A channel is mastered by the time the seal is held, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** How many needles the hold has already thrown by this tick. */
    private static int needlesSpent(int ticksChanneled) {
        return (ticksChanneled + 1) / TICKS_PER_NEEDLE;
    }

    /** One needle: the cut, the chakra it spills, the seal it leaves and the line it came down. */
    private void strike(ServerLevel level, Player player, LivingEntity target) {
        target.hurt(player.damageSources().magic(), NEEDLE_DAMAGE);
        drain(target);
        seal(target, level.getGameTime());

        Vec3 hand = JutsuEffects.handPosition(player);
        Vec3 impact = target.position().add(0.0d, target.getBbHeight() * 0.55d, 0.0d);
        Vec3 span = impact.subtract(hand);
        for (int i = 1; i <= LINE_POINTS; i++) {
            Vec3 at = hand.add(span.scale(i / (double) (LINE_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z,
                6, 0.15d, 0.15d, 0.15d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    /** Spills a ninja's chakra. Anything without a pool is left to the damage and the seal. */
    private static void drain(LivingEntity target) {
        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(NEEDLE_DRAIN, data.getChakra());
            if (lost > 0.0f) {
                data.useChakra(lost, CHAKRA_REGEN_DELAY);
            }
        });
    }

    /**
     * Closes the struck channels until {@link #SEAL_TICKS} from now. The stamp only ever moves later:
     * a palm that lands just before the needle must not have its own, longer, seal shortened by it.
     */
    private static void seal(LivingEntity target, long now) {
        CompoundTag data = target.getPersistentData();
        long until = now + SEAL_TICKS;
        if (data.getLong(SEAL_UNTIL_TAG) < until) {
            data.putLong(SEAL_UNTIL_TAG, until);
        }
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
