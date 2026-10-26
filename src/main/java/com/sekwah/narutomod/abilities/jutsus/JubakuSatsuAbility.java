package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
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
 * Magen: Jubaku Satsu - the illusion of being bound to a tree that is not there.
 *
 * <p>The victim is not wrapped in anything. Its body is simply convinced that it is: the legs will
 * not answer, the arms will not lift, and the creature stands exactly where it was when the look
 * landed. For three seconds a fight stops being a fight and becomes a choice of where to stand and
 * how hard to hit something that cannot flinch.</p>
 *
 * <p>The illusion breaks the first time the victim is actually struck - pain is a fine argument
 * against an imaginary tree - and that first blow lands harder than it should, because a body that
 * believes it cannot move does not brace for it. What the technique buys is one free strike; any
 * longer and it would be an execution rather than an illusion.</p>
 *
 * <p>Allies are left alone: a ninja who rooted their own team would only be handing the enemy the
 * same free hit. The hold is enforced by the upkeep hook from the mark written here.</p>
 */
public class JubakuSatsuAbility extends Ability implements Ability.Cooldown {

    /** Roots break dirt audibly; the cast carries about as far as that sound. */
    @Override
    public int noiseRadius() {
        return 6;
    }

    /** C B C C V B - the genjutsu combination (C B C) and the branch's fourth step. */
    public static final long COMBO = 13114L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "jubaku_satsu");

    /** Game time at which the body remembers how to move. Read by the upkeep hook. */
    public static final String UNTIL_TAG = "narutomod:jubaku_until";

    /** How far down the look ray the hold can be laid. */
    private static final double RANGE = 10.0d;

    /** What it costs and how long before the eye can bind another body. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 320;

    /** How long the body is held - three seconds, one free strike. */
    private static final int ROOT_TICKS = 60;

    /** The hold is heavier than any ordinary slowness: the body is not meant to move at all. */
    private static final int ROOT_AMPLIFIER = 6;

    /** How many points of brown dust make the ring at the feet. */
    private static final int SNARE_POINTS = 12;

    /** Half-width of the snare ring, in blocks. */
    private static final double SNARE_RADIUS = 0.7d;

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
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jubaku.notarget",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Friends.isOwnSide(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jubaku.ally",
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
        if (target == null || Friends.isOwnSide(player, target)) {
            // The look was paid for a moment ago; the target can have moved out of reach since.
            return;
        }

        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + ROOT_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS,
                ROOT_AMPLIFIER, false, true));
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }

        player.displayClientMessage(Component.translatable("jutsu.jubaku_satsu.cast",
                target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        drawSnare(level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** @return the living body under the ninja's gaze, or null when nothing living is in the way. */
    private static LivingEntity findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    /**
     * Draws the imaginary tree as a ring of dust and splinters at the victim's feet, so what the
     * caster sees matches what the victim believes is holding it.
     */
    private static void drawSnare(ServerLevel level, LivingEntity target) {
        for (int point = 0; point < SNARE_POINTS; point++) {
            double angle = 2.0d * Math.PI * point / SNARE_POINTS;
            double x = target.getX() + Math.cos(angle) * SNARE_RADIUS;
            double z = target.getZ() + Math.sin(angle) * SNARE_RADIUS;
            level.sendParticles(ParticleTypes.CRIT, x, target.getY() + 0.15d, z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
            if (point % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, x, target.getY() + 0.05d, z,
                        1, 0.05d, 0.02d, 0.05d, 0.01d);
            }
        }
    }
}
