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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Node Rupture: a strike placed on the chakra node a body is feeding its own health through.
 *
 * <p>The Gentle Fist usually closes channels from the outside - no casting, no boons, a few seconds
 * of being ordinary. This is the harsher reading of the same map: every blessing running through the
 * target is cut off the body at once, and the node that stitches wounds shut is torn so that it
 * stays shut for eight seconds. Healing potions, regeneration and golden apples all land on a body
 * that cannot use them; the listener on the receiving end reads {@link #RUPTURE_UNTIL_TAG}.</p>
 *
 * <p>It deals little damage of its own. Like {@link ChakraSeverAbility} it is a technique about the
 * window it opens, and it needs the eye to find the node: see {@link ByakuganAbility}.</p>
 */
public class NodeRuptureAbility extends Ability implements Ability.Cooldown {

    /** Torn chakra rings like struck crystal; it carries as far as a shout. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /**
     * V B V V B B B - the needles' seal and then the strike. The tag it leaves is
     * {@code narutomod:node_rupture_until}; see {@link #RUPTURE_UNTIL_TAG}.
     */
    public static final long COMBO = 2233L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "node_rupture");

    /** How far down the look ray the node can be found. Walls stop it, as they stop every eye. */
    private static final double RANGE = 6.0d;

    /** What it costs and how long the eye needs before it can tear another node. */
    private static final float COST = 45.0f;
    private static final int COOLDOWN = 500;

    /** How long the torn node refuses to mend, in ticks. Longer than the seal, shorter than a fight. */
    private static final int RUPTURE_TICKS = 160;

    /**
     * The tag the parent's regeneration block reads. The name is the contract between this cast and
     * that listener, so it is spelled out here rather than built from anything.
     */
    public static final String RUPTURE_UNTIL_TAG = "narutomod:node_rupture_until";

    /** The tear itself only hurts; the closed node is what the technique is for. */
    private static final float DAMAGE = 4.0f;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.node_rupture.notarget",
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

        stripBoons(target);
        target.hurt(player.damageSources().magic(), DAMAGE);
        target.getPersistentData().putLong(RUPTURE_UNTIL_TAG,
                level.getGameTime() + RUPTURE_TICKS);

        level.sendParticles(ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                12, 0.35d, 0.5d, 0.35d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.1f, 0.7f);
    }

    /**
     * Takes every beneficial effect off the target, leaving its curses where they were. The same cut
     * as {@code ChakraSeverAbility#stripBoon}: an effect is harmful if it is one of the ones a body
     * would rather not carry, and everything else is a boon.
     */
    private static void stripBoons(LivingEntity target) {
        List<MobEffect> harmful = List.of(
                MobEffects.MOVEMENT_SLOWDOWN,
                MobEffects.DIG_SLOWDOWN,
                MobEffects.WEAKNESS,
                MobEffects.POISON,
                MobEffects.WITHER,
                MobEffects.BLINDNESS,
                MobEffects.DARKNESS,
                MobEffects.HUNGER,
                MobEffects.UNLUCK,
                MobEffects.BAD_OMEN,
                MobEffects.CONFUSION,
                MobEffects.LEVITATION);
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) {
            if (!harmful.contains(effect.getEffect())) {
                target.removeEffect(effect.getEffect());
            }
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
