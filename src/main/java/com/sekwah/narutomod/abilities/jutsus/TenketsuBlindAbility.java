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
 * Tenketsu Blind: a palm driven into the channels beside the eyes.
 *
 * <p>Every technique of the Gentle Fist reads the same map of channels and closes a different part
 * of it. The ones by the eyes carry sight, so a strike there takes the target's world away for a few
 * seconds: blind, then swimming with vertigo long enough to be walked around. It does almost no
 * damage, because the point was never the blow - a blinded body loses a fight it cannot see coming
 * far more completely than a bruised one loses it.</p>
 *
 * <p>It reaches as far as an arm and a step, so it is the technique a caster uses once they are
 * already inside; the needles are what opens the distance. See {@link TenketsuNeedlesAbility}.</p>
 */
public class TenketsuBlindAbility extends Ability implements Ability.Cooldown {

    /** A palm into a face is heard, but only close by. */
    @Override
    public int noiseRadius() {
        return 6;
    }

    /** V B V V B C - the needles' seal with one more key, the strike itself. */
    public static final long COMBO = 232212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tenketsu_blind");

    /** Arm's length and a step: this is the technique of a body already in reach. */
    private static final double RANGE = 4.0d;

    /** What it costs and how long the eye needs before it can be done again. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 300;

    /** The blind and the vertigo it leaves behind, in ticks. The vertigo outlasts the blindness. */
    private static final int BLIND_TICKS = 60;
    private static final int CONFUSION_TICKS = 100;

    /** Enough to make the strike land, not enough to be the reason the target fell. */
    private static final float DAMAGE = 1.0f;

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)
                || !canStrike(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.tenketsu_blind.notarget",
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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)
                || !canStrike(player, target)) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, CONFUSION_TICKS, 0, false, true));
        target.hurt(player.damageSources().magic(), DAMAGE);

        Vec3 head = target.position().add(0.0d, target.getBbHeight() * 0.9d, 0.0d);
        level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z,
                8, 0.2d, 0.12d, 0.2d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.1f);
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
