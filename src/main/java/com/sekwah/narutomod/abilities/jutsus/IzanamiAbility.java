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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Izanami: the loop that decides a mind by refusing to move on.
 *
 * <p>Where Tsukuyomi breaks a mind in a second, Izanami waits. It marks whoever is under the look
 * for ten seconds and then watches what that body chooses to do: three blows from the same hand and
 * the illusion closes, the victim's body freezes where it stands for four seconds and its strikes
 * fall weak. It kills nothing and hurts nothing by itself - the technique's whole argument is that
 * a creature that keeps swinging at the same answer will eventually notice the answer never
 * changes.</p>
 *
 * <p>The mark is a timestamp plus a hit counter in the victim's persistent data. A loop has to
 * outlast the fight around it: the victim may die and come back, the chunk may unload, the server
 * may restart between the first blow and the third, and none of that should hand the mind its
 * freedom. {@code SharinganIllusionEvents} increments the counter and closes the loop; this class
 * only lays the mark and shows the eye working.</p>
 */
public class IzanamiAbility extends Ability implements Ability.Cooldown {

    /** A look, not a shout: nothing outside the two bodies is meant to notice. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B V - the Sharingan combo (C V V B), then the loop closing back on itself. */
    public static final long COMBO = 111231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "izanami");

    /** Game time after which the loop stops counting and lets the mind go. */
    public static final String UNTIL_TAG = "narutomod:izanami_until";

    /** How many blows from the marked hand have already been recorded against the loop. */
    public static final String HITS_TAG = "narutomod:izanami_hits";

    /** The hand whose repeated blows close the loop. Written on the first hit that counts. */
    public static final String SOURCE_TAG = "narutomod:izanami_source";

    /** How far down the look ray the mark can be laid. Walls stop it, as they stop every eye. */
    private static final double RANGE = 10.0d;

    /** What it costs and how long before the eye can lay another loop. */
    private static final float COST = 100.0f;
    private static final int COOLDOWN = 800;

    /** Ten seconds in which the victim has to repeat itself three times. */
    private static final int DURATION = 200;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.izanami.notarget",
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

        // The counter starts empty and the hand is not named until it actually strikes: a loop that
        // guessed its author at cast time could be escaped by simply letting somebody else hit first.
        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        target.getPersistentData().putInt(HITS_TAG, 0);
        target.getPersistentData().remove(SOURCE_TAG);

        // The mark has no shape of its own, so it is drawn as chakra settling over the body: a slow
        // violet drift that reads as something waiting rather than something happening.
        level.sendParticles(ParticleTypes.WITCH, target.getX(),
                target.getY() + target.getBbHeight() * 0.7d, target.getZ(),
                30, 0.4d, 0.6d, 0.4d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** A mind that can be caught in a loop is a living one, and never an ally or a friend. */
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
