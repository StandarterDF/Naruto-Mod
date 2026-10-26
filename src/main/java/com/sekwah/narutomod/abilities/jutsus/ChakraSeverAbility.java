package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Chakra Sever: a look that cuts the chakra running through a body.
 *
 * <p>The Jougan does not strike, it <em>reads</em>, and once a thing is read it can be interrupted.
 * This technique fixes on whoever the caster is looking at and severs the flow for a few seconds:
 * every boon the target was carrying is stripped, and what is left is a body that cannot hit hard,
 * cannot move quickly and has nowhere to hide. Against a ninja it also spills their chakra out into
 * the air, which is what makes the technique hurt a caster rather than only frighten them.</p>
 *
 * <p>It deals no damage of its own. The window it opens is the damage, and it is deliberately short
 * enough that the caster has to use it.</p>
 */
public class ChakraSeverAbility extends Ability implements Ability.Cooldown {

    /** The cut carries as far as a shout. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B V V C V - the Jougan combo (B V V C) and one more key. */
    public static final long COMBO = 32212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_sever");

    /** How far down the look ray the cut can be made. Walls stop it, as they stop every eye. */
    private static final double RANGE = 20.0d;

    /** What it costs and how long before the eye can do it again. */
    private static final float COST = 45.0f;
    private static final int COOLDOWN = 240;

    /** How long the severed body is left weak, slow and lit up. */
    private static final int SEVER_TICKS = 100;

    /** How much chakra a ninja target loses, capped by what they actually have. */
    private static final float DRAIN = 80.0f;

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
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_sever.notarget",
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
        if (!isValidTarget(player, target)) {
            return;
        }

        stripBoon(target);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SEVER_TICKS, 1, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SEVER_TICKS, 1, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, SEVER_TICKS, 0, false, false));
        drain(target);

        // A line of sparks from the caster's eye to the severed body, so the cut is visible where it
        // landed rather than only on the target's HUD.
        for (Step step : Step.between(player, target)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, step.x, step.y, step.z,
                    1, 0.05d, 0.05d, 0.05d, 0.0d);
        }
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                20, 0.4d, 0.6d, 0.4d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    /** Takes every beneficial effect off the target, leaving its curses where they were. */
    private void stripBoon(LivingEntity target) {
        List<MobEffect> harmful = List.of(
                MobEffects.MOVEMENT_SLOWDOWN,
                MobEffects.WEAKNESS,
                MobEffects.POISON,
                MobEffects.WITHER,
                MobEffects.BLINDNESS,
                MobEffects.DARKNESS,
                MobEffects.HUNGER,
                MobEffects.UNLUCK,
                MobEffects.BAD_OMEN,
                MobEffects.DIG_SLOWDOWN,
                MobEffects.CONFUSION,
                MobEffects.LEVITATION);
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) {
            if (!harmful.contains(effect.getEffect())) {
                target.removeEffect(effect.getEffect());
            }
        }
    }

    /** Spills a ninja's chakra. Anything not a ninja has no pool to cut, and is left to the debuffs. */
    private void drain(LivingEntity target) {
        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(DRAIN, data.getChakra());
            if (lost > 0.0f) {
                data.setChakra(data.getChakra() - lost);
            }
        });
    }

    private boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** One point along the line between caster and target, used to place the spark trail. */
    private record Step(double x, double y, double z) {

        private static final int POINTS = 16;

        static List<Step> between(LivingEntity from, LivingEntity to) {
            double startX = from.getX();
            double startY = from.getY() + from.getBbHeight() * 0.85d;
            double startZ = from.getZ();
            double endX = to.getX();
            double endY = to.getY() + to.getBbHeight() * 0.5d;
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
