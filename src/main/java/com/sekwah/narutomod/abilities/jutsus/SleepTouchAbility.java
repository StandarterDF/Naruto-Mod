package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Nemuri: a touch that puts a body to sleep where it stands.
 *
 * <p>The gentlest way a ninja ends a fight without ending the creature. A hand's width of contact
 * and the target's mind is switched off: it stops acting, stops tracking, and goes down into a
 * sleep it will not shake on its own for eight seconds. Only an ordinary body goes under - what a
 * village sends a team after carries too much life to be held down by a touch - which keeps this a
 * technique for taking one opponent apart rather than for clearing a room.</p>
 *
 * <p>The sleep is a hold, not a kill. A body that is struck wakes early, and a body that is left
 * alone wakes when its timer runs out; both are handled by the code that watches the tag written
 * here, so this class never has to remember who it put down.</p>
 */
public class SleepTouchAbility extends Ability implements Ability.Cooldown {

    /** A touch is quiet; only the body right in front of the ninja hears anything. */
    @Override
    public int noiseRadius() {
        return 4;
    }

    /** C B C C V - the genjutsu combination (C B C) and two more keys. */
    public static final long COMBO = 13112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "nemuri");

    /** Reach of the touch, in blocks. It is not a thrown technique. */
    private static final double RANGE = 4.0d;

    private static final float COST = 30.0f;
    private static final int COOLDOWN = 400;

    /** How long the body stays down, in ticks. */
    public static final int SLEEP_TICKS = 160;

    /**
     * The tag the watcher reads to know when the body is due to wake, and to know that it is holding
     * the AI off. Public because the expiry lives outside this class.
     */
    public static final String SLEEP_TAG = "narutomod:nemuri_until";

    /** A sleeper is held in place by its own weight as much as by the chakra. */
    private static final int SLOWNESS_AMPLIFIER = 6;

    /**
     * Anything this hardy is simply too much body to switch off: the technique is for people,
     * animals and ordinary monsters, not for what a village sends a team after.
     */
    private static final float MAX_SLEEPABLE_HEALTH = 100.0f;

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
        Mob target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.nemuri.notarget",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!canSleep(target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.nemuri.toostrong",
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
        Mob target = findTarget(player);
        if (target == null || !canSleep(target)) {
            // The check in handleCost already passed, so the target moved or died in between; the
            // cast fizzles rather than charging for a body that is not there any more.
            return;
        }

        target.setNoAi(true);
        target.getPersistentData().putLong(SLEEP_TAG, level.getGameTime() + SLEEP_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLEEP_TICKS,
                SLOWNESS_AMPLIFIER, false, true));
        // A sleeper should not be lit up like a target through the wall it is lying behind.
        target.removeEffect(MobEffects.GLOWING);

        player.displayClientMessage(Component.translatable("jutsu.nemuri.cast",
                target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                target.getY() + target.getBbHeight() * 0.9d, target.getZ(),
                12, 0.3d, 0.25d, 0.3d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 0.35f, 1.3f);
    }

    /** @return the mob the ninja is touching with their gaze, or null when it is not a mob. */
    private static Mob findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob && mob.isAlive()) {
            return mob;
        }
        return null;
    }

    /** @return whether the body is small enough to put down; the caster's own clones are fair game. */
    private static boolean canSleep(Mob target) {
        return target.getMaxHealth() < MAX_SLEEPABLE_HEALTH;
    }
}
