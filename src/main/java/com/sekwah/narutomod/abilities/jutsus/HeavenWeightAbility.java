package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
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
 * The Rinnegan pressing the sky down.
 *
 * <p>Gravity is the eye's plainest tool, and this is the version used to end a chase rather than
 * start one. Everyone caught in the bubble is made heavy: they drag their feet, they cannot leave
 * the ground, and anything that was flying is brought down. The mark left on each body is what the
 * support hook uses to keep the jump and the wings from working, because vanilla has no effect for
 * either.</p>
 */
public class HeavenWeightAbility extends Ability implements Ability.Cooldown {

    /** The weight of a whole sky is not a quiet thing. */
    @Override
    public int noiseRadius() {
        return 20;
    }

    /** V V C B V - the Rinnegan stance and one more key. */
    public static final long COMBO = 23322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "heaven_weight");

    /** The bubble the weight presses on. */
    private static final double RADIUS = 8.0d;

    /** How long the bodies stay heavy. */
    private static final int DURATION = 100;

    private static final float COST = 45.0f;
    private static final int COOLDOWN = 320;

    /** Read by the support hook to keep the affected bodies on the ground. */
    public static final String UNTIL_TAG = "narutomod:heaven_weight_until";

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
        level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                        entity -> entity != player && entity.isAlive() && !entity.isSpectator())
                .forEach(target -> press(level, player, target));

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + 0.2d, player.getZ(), 60, RADIUS * 0.6d, 0.2d, RADIUS * 0.6d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
    }

    /** Pins one body to the floor and marks it so the hooks can keep it there. */
    private void press(ServerLevel level, Player caster, LivingEntity target) {
        if (Friends.areFriends(caster, target)) {
            return;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 2, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, DURATION, 1, false, true));
        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        target.setDeltaMovement(target.getDeltaMovement().x, Math.min(0.0d, target.getDeltaMovement().y), target.getDeltaMovement().z);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1d, target.getZ(),
                6, 0.25d, 0.05d, 0.25d, 0.01d);
    }
}
