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
import net.minecraft.world.phys.Vec3;

/**
 * Magen: Narakumi - the illusion of the thing the victim is most afraid of.
 *
 * <p>Nothing is held and nothing is hurt. The creature is simply shown its own fear, and a charge
 * that was a heartbeat away from reaching the caster turns into a rout: it drops whoever it was
 * fighting, refuses to pick the caster back up, and runs. Against a line of attackers this is worth
 * more than a wound, because the one thing an enemy cannot do with a broken nerve is keep a
 * formation.</p>
 *
 * <p>Only an ordinary body is suggestible enough. A creature that can absorb a hundred points of
 * punishment has too much life in it for a picture in its head, so the technique is aimed at the
 * rank and file and not at what a village sends a team after.</p>
 *
 * <p>The fear itself is not enforced here: this class stamps the mark, and the upkeep hook does the
 * rest, so a victim that is saved and reloaded still runs for exactly as long as it should.</p>
 */
public class NarakumiAbility extends Ability implements Ability.Cooldown {

    /** An illusion is laid in the mind, not shouted into the air. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C C V C - the genjutsu combination (C B C) and the branch's third step. */
    public static final long COMBO = 13113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "narakumi");

    /** Game time at which the creature comes back to its senses. Read by the upkeep hook. */
    public static final String UNTIL_TAG = "narutomod:narakumi_until";

    /** UUID string of the ninja the creature is running from. */
    public static final String SOURCE_TAG = "narutomod:narakumi_source";

    /** How far down the look ray the fear can be planted; a wall stops a gaze like any other ray. */
    private static final double RANGE = 12.0d;

    /** What it costs and how long before the mind can be shown the same picture again. */
    private static final float COST = 35.0f;
    private static final int COOLDOWN = 280;

    /**
     * How long the creature keeps running from the caster - five seconds, long enough to leave a
     * fight and not long enough to matter once the caster has gone.
     */
    private static final int FEAR_TICKS = 100;

    /** The first two seconds are spent in a flat sprint, so the rout is visible from a distance. */
    private static final int BOLT_TICKS = 40;
    private static final int BOLT_AMPLIFIER = 1;

    /** Anything this hardy is too much body to be frightened by a picture. */
    private static final float BOSS_HEALTH = 100.0f;

    /** How much of the gaze is drawn, and how finely. */
    private static final double GAZE_STEP = 0.9d;
    private static final int BURST = 12;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.narakumi.notarget",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (isTooStrong(target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.narakumi.toostrong",
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
        if (target == null || isTooStrong(target)) {
            // The look was paid for a moment ago; the creature can have died or turned since.
            return;
        }

        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + FEAR_TICKS);
        target.getPersistentData().putString(SOURCE_TAG, player.getUUID().toString());

        // Both halves of the memory of the fight are dropped: the target, and the body that last
        // hurt it, or the hurt-by goal would hand the caster straight back to it.
        target.setTarget(null);
        target.setLastHurtByMob(null);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BOLT_TICKS,
                BOLT_AMPLIFIER, false, true));

        player.displayClientMessage(Component.translatable("jutsu.narakumi.cast",
                target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        drawGaze(level, player, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /** @return the mob under the ninja's gaze, or null when a player or nothing is in the way. */
    private static Mob findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob && mob.isAlive()) {
            return mob;
        }
        return null;
    }

    /** @return whether the body carries too much life for a small picture to frighten it. */
    private static boolean isTooStrong(Mob mob) {
        return mob.getMaxHealth() >= BOSS_HEALTH;
    }

    /**
     * Draws the gaze as witch-light from the caster's eyes to the victim's, then a burst around the
     * head. The line is what tells the caster the cast landed on the creature they meant.
     */
    private static void drawGaze(ServerLevel level, Player player, Mob target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length >= 1.0e-4d) {
            for (double travelled = 0.0d; travelled < length; travelled += GAZE_STEP) {
                Vec3 at = from.add(path.scale(travelled / length));
                level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        level.sendParticles(ParticleTypes.WITCH, target.getX(),
                target.getY() + target.getBbHeight() * 0.9d, target.getZ(),
                BURST, 0.3d, 0.25d, 0.3d, 0.01d);
    }
}
