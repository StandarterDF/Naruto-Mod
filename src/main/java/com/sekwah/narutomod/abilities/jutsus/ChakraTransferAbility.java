package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The Rinnegan handing chakra across a gap.
 *
 * <p>The eye of the sage is not only a hand that takes. This is the same path turned outwards: a
 * thread of chakra from the caster's pool to an ally's, paid for tick by tick and delivered at the
 * same rate it is drawn - nothing is created and nothing is lost, so a caster cannot use it to
 * multiply a battery. The beam holds only while both bodies stay close enough to be connected.</p>
 */
public class ChakraTransferAbility extends Ability implements Ability.Channeled {

    /** A shared thread of chakra is quiet work, not a shout. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V V C B B - the Rinnegan stance (V V C V) with the last key changed. */
    public static final long COMBO = 23321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_transfer");

    /** How far the beam can reach. */
    private static final double RANGE = 12.0d;

    /** Chakra drawn per tick; the ally receives the same amount, capped by their own pool. */
    private static final float PER_TICK = 8.0f;

    /** How long one cast may run before the caster has to start it again. */
    private static final int MAX_TICKS = 240;

    /** Ticks before the caster's own regeneration resumes. */
    private static final int REGEN_DELAY = 20;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
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
        if (chargeAmount >= MAX_TICKS) {
            return false;
        }
        if (recipient(player) == null) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.chakra_transfer.noally",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        float cost = this.masteryCost(ninjaData, PER_TICK);
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // The work happens per tick in handleChannelling; a completed channel is worth one cast.
        this.recordMastery(player, ninjaData);
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        Player ally = recipient(player);
        if (ally == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ally.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(allyData -> {
            float room = allyData.getMaxChakra() - allyData.getChakra();
            float moved = Math.min(PER_TICK, room);
            if (moved > 0.0f) {
                allyData.setChakra(allyData.getChakra() + moved);
            }
        });
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (ticksChanneled % 3 == 0) {
            for (Step step : Step.between(player, ally)) {
                level.sendParticles(ParticleTypes.END_ROD, step.x, step.y, step.z,
                        1, 0.02d, 0.02d, 0.02d, 0.0d);
            }
        }
        if (ticksChanneled % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.35f, 1.6f);
        }
    }

    /**
     * The ally the beam is meant for: whoever is under the crosshair, or the nearest friend/team mate
     * if the caster is not aiming at anyone in particular.
     */
    private Player recipient(Player caster) {
        if (caster.level().isClientSide) {
            return null;
        }
        if (com.sekwah.narutomod.util.Targeting.lookedAtEntity(caster, RANGE) instanceof Player aimed
                && aimed != caster && isAlly(caster, aimed)) {
            return aimed;
        }
        Player best = null;
        double bestDistance = RANGE * RANGE;
        for (Player other : caster.level().players()) {
            if (other == caster || !isAlly(caster, other) || other.isSpectator()) {
                continue;
            }
            double distance = other.distanceToSqr(caster);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private boolean isAlly(Player caster, Player other) {
        return other.isAlliedTo(caster) || Friends.areFriends(caster, other);
    }

    /** One point along the beam, used to place the particles. */
    private record Step(double x, double y, double z) {

        private static final int POINTS = 10;

        static List<Step> between(Player from, Player to) {
            double startX = from.getX();
            double startY = from.getY() + from.getBbHeight() * 0.6d;
            double startZ = from.getZ();
            double endX = to.getX();
            double endY = to.getY() + to.getBbHeight() * 0.6d;
            double endZ = to.getZ();
            return java.util.stream.IntStream.rangeClosed(1, POINTS)
                    .mapToObj(i -> {
                        double t = (double) i / POINTS;
                        return new Step(startX + (endX - startX) * t,
                                startY + (endY - startY) * t,
                                startZ + (endZ - startZ) * t);
                    })
                    .toList();
        }
    }
}
