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
import net.minecraft.world.phys.Vec3;

/**
 * Kurai: the eye putting out the lights in one mind.
 *
 * <p>It is the smallest thing a Sharingan can do to somebody and still be called a technique. There
 * is no illusion to live through and nothing to walk away from: the world simply goes dark and
 * starts turning, and for a few seconds the victim is a body with a broken sense of where anything
 * is. No wound is left, so nothing gives the caster away and nothing marks the victim afterwards -
 * which is exactly why it is used before the strike rather than instead of it.</p>
 *
 * <p>The mark it leaves is only a timestamp: whoever draws the victim's panel can read
 * {@link #UNTIL_TAG} and blank it, the way the victim's own eyes are blanked, so the caster never
 * sees numbers the victim cannot.</p>
 */
public class KuraiAbility extends Ability implements Ability.Cooldown {

    /** Nothing here makes a sound of its own: the eye is doing the work. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B C C C - the Sharingan combo (C V V B), then the quietest of its illusions. */
    public static final long COMBO = 122323L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kurai");

    /** Game time at which the darkness lifts. Read by whoever blanks the victim's panel. */
    public static final String UNTIL_TAG = "narutomod:kurai_until";

    /** How far down the look ray the darkness can be laid. Walls stop it, as they stop every eye. */
    private static final double RANGE = 12.0d;

    /** What it costs and how long before the eye can do it again. */
    private static final float COST = 45.0f;
    private static final int COOLDOWN = 600;

    /** Four seconds without sight, three without bearings: the darkness outlasts the spinning. */
    private static final int BLIND_TICKS = 80;
    private static final int CONFUSION_TICKS = 60;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.kurai.notarget",
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
        // The look was paid for a moment ago; the body can have died or been swapped since.
        if (!isValidTarget(player, target)) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, CONFUSION_TICKS, 0, false, true));
        // Informational only: the lightness of this technique is that it needs no upkeep at all, and
        // the tag exists so a view can agree with the effects rather than guess from them.
        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + BLIND_TICKS);

        // The ink is the picture of a light going out, thrown where the victim's eyes are so the
        // caster sees the exact moment it lands - and the sound is the only noise in the exchange.
        Vec3 eyes = target.getEyePosition();
        level.sendParticles(ParticleTypes.SQUID_INK, eyes.x, eyes.y, eyes.z,
                18, 0.2d, 0.2d, 0.2d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.INK_SAC_USE, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** The darkness is for a living mind, and never for an ally or a friend. */
    private boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
