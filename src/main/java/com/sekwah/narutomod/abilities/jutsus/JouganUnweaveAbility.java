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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Jougan Unweave: the eye picks a disguise apart and leaves the body that was wearing it standing
 * in plain sight.
 *
 * <p>Ninja hide behind more than shadows. A transformation makes a face into somebody else's, a
 * substitution leaves a few ticks of nothing where a body used to be, and a potion of invisibility
 * is the cheapest lie of the three. The Jougan sees the chakra that holds all of them together, so
 * a look can take the lie off: the shape snaps back, the invisibility is torn away and the body is
 * lit up for everyone present to see, which is usually the more useful half of the technique.</p>
 *
 * <p>Like the rest of the eye's work it does no damage. Being seen is the punishment, and the
 * caster's allies are not made to pay it.</p>
 */
public class JouganUnweaveAbility extends Ability implements Ability.Cooldown {

    /** A look is silent. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V V V C V. */
    public static final long COMBO = 2211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "jougan_unweave");

    /** How far the look reaches. The eye reads further than a thrown technique, not through walls. */
    private static final double RANGE = 12.0d;

    private static final float COST = 20.0f;
    private static final int COOLDOWN = 240;

    /** How long the stripped body is lit up for every eye in the fight. */
    public static final int REVEAL_TICKS = 200;

    /**
     * The expiry the reveal is written under, so anything that keeps the body marked - or wants to
     * know why it is marked - can read it without asking this class.
     */
    public static final String REVEAL_TAG = "narutomod:unweave_until";

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
        if (findTarget(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan_unweave.notarget",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
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
        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }

        stripDisguise(target);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_TICKS, 0, false, false));
        target.getPersistentData().putLong(REVEAL_TAG, level.getGameTime() + REVEAL_TICKS);

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                20, 0.4d, 0.6d, 0.4d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    /**
     * Takes off everything the body is hiding behind: the potion, the vanishing act of a
     * substitution, and - for a ninja - the transformation itself.
     *
     * <p>A henge is read from the copied form rather than from its timer, so clearing the timer alone
     * would leave the client still drawing somebody else's face; the form has to be wiped with it.
     * The same goes for the substitution's own invisibility counter, which outlives the potion.</p>
     */
    private static void stripDisguise(LivingEntity target) {
        target.setInvisible(false);
        target.removeEffect(MobEffects.INVISIBILITY);

        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            data.setHengeTicks(0);
            data.setHengeTarget("");
            data.setHengeEntityType("");
            data.setHengeName("");
            data.setInvisibleTicks(0);
        });
    }

    /** @return the living thing under the caster's gaze, or null when it is nothing they may strip. */
    private static LivingEntity findTarget(Player player) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)) {
            return null;
        }
        if (target == player || !target.isAlive() || target.isSpectator()) {
            return null;
        }
        if (target instanceof Player other && other.isCreative()) {
            return null;
        }
        if (target.isAlliedTo(player) || Friends.areFriends(player, target)) {
            return null;
        }
        return target;
    }
}
