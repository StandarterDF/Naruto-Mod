package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Chakra Trail: the eye leaves a mark on a body that only the eye can follow.
 *
 * <p>A Sharingan does not need to keep looking at a quarry. Once the caster has read the chakra of
 * a creature the flow of it is pinned to that creature for half a minute, and the mark outlives
 * walls, distance and a lost line of sight: the body glows where it stands, and the chakra it leaves
 * behind can be picked up as a trail leading to wherever it went. It marks and it glows, nothing
 * more - the trail itself is drawn by whoever reads the tags left here, so the technique can hand
 * the same mark to a tracking view instead of owning one.</p>
 *
 * <p>It is deliberately cheap and quiet for an eye technique: half the cost of a strike, and no
 * damage at all. Marking someone is only worth a slot at the table if it does not cost the fight.</p>
 */
public class ChakraTrailAbility extends Ability implements Ability.Cooldown {

    /** A mark is a look, not a shout: it carries a little further than a footstep. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** C V V B B B - the Sharingan combo (C V V B) and two more keys, next to Amaterasu. */
    public static final long COMBO = 122311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_trail");

    /** Game time at which the mark stops being followed. Read by the trail renderer. */
    public static final String UNTIL_TAG = "narutomod:chakra_trail_until";

    /** UUID string of the ninja the mark belongs to. Only their eye can follow it. */
    public static final String SOURCE_TAG = "narutomod:chakra_trail_source";

    /** How far down the look ray the mark can be laid. Walls stop it, as they stop every eye. */
    private static final double RANGE = 20.0d;

    /** What it costs and how long before the eye can mark again. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 300;

    /** How long the mark lasts - the same 30 seconds the tag stores, in ticks. */
    private static final int TRAIL_TICKS = 600;

    /** Where on a body the chakra is read from, as a share of its height. */
    private static final double CHEST = 0.6d;

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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)
                || !isValidTarget(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_trail.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)) {
            return;
        }
        // The look was paid for a moment ago; the body can have moved, died or been swapped since.
        if (!isValidTarget(player, target)) {
            return;
        }

        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + TRAIL_TICKS);
        target.getPersistentData().putString(SOURCE_TAG, player.getUUID().toString());

        // The glow is the visible half of the mark: it is what the caster sees through the wall that
        // is now between them, and it is the only part of the trail that needs no reader.
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, TRAIL_TICKS, 0, false, false));

        // A thin line of light drawn from chest to chest, so the moment the mark lands is legible
        // even when the target is about to break line of sight.
        for (Step step : Step.between(player, target)) {
            level.sendParticles(ParticleTypes.END_ROD, step.x, step.y, step.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    /** A mark is worth nothing on a corpse, an onlooker, an ally or a friend. */
    private boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** One point along the line between caster and target, used to place the trail. */
    private record Step(double x, double y, double z) {

        private static final int POINTS = 16;

        static List<Step> between(LivingEntity from, LivingEntity to) {
            double startX = from.getX();
            double startY = from.getY() + from.getBbHeight() * CHEST;
            double startZ = from.getZ();
            double endX = to.getX();
            double endY = to.getY() + to.getBbHeight() * CHEST;
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
