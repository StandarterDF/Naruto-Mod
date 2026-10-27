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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Tsukuyomi: the eye lending a mind a lifetime it has no time to live.
 *
 * <p>The other Sharingan techniques move a body or cloud a sense; this one takes the mind itself.
 * Whoever is caught by the look spends what feels like days inside an illusion of the caster's
 * making while a second passes outside, and comes back to a body that cannot see, cannot tell up
 * from down and - if it is an ordinary creature - cannot decide to do anything at all. It is the
 * heaviest thing a Sharingan can do to somebody, and it is priced like it: the deepest bite of
 * chakra the eye has, and a cooldown long enough that it decides one exchange rather than a fight.</p>
 *
 * <p>What the body takes is small; the price is paid by the mind. The mark left here is the promise
 * that the mind comes back: a held creature is switched off rather than killed, and the upkeep in
 * {@code TechniqueMarkEvents} that reads {@link #STUN_TAG} hands it back its own behaviour when the
 * illusion ends, exactly as it was when it was caught.</p>
 */
public class TsukuyomiAbility extends Ability implements Ability.Cooldown {

    /** The whole technique happens inside the victim's head: the world hears nothing of it. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B C B - the Sharingan combo (C V V B), then the heaviest illusion on the branch. */
    public static final long COMBO = 3231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tsukuyomi");

    /** Game time at which a held creature gets its mind back. Read by the parent's upkeep hook. */
    public static final String STUN_TAG = "narutomod:tsukuyomi_until";

    /** How far down the look ray the eye can reach. Walls stop it, as they stop every eye. */
    private static final double RANGE = 12.0d;

    /** What it costs and how long before the eye can do it again. */
    private static final float COST = 90.0f;
    private static final int COOLDOWN = 800;

    /** How long the victim is held - three seconds of a lifetime spent elsewhere. */
    private static final int DURATION = 60;

    /** The little the body takes while the mind is away, in half-hearts. */
    private static final float DAMAGE = 6.0f;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.tsukuyomi.notarget",
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

        // The mind goes first: no sight, no bearings, and for a creature with a simple mind no
        // decision at all. Held is not the same as dead, so the tag is what brings it back.
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DURATION, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DURATION, 1, false, true));
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getPersistentData().putLong(STUN_TAG, level.getGameTime() + DURATION);
        }
        target.hurt(player.damageSources().magic(), DAMAGE);

        // The illusion itself has no shape outside the victim's head, so the only thing to see is
        // the chakra of the eye pouring into it: a dark violet crown over the body.
        level.sendParticles(ParticleTypes.WITCH, target.getX(),
                target.getY() + target.getBbHeight() * 0.9d, target.getZ(),
                24, 0.3d, 0.3d, 0.3d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    /** A mind worth breaking is a living one, and never an ally or a friend. */
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
