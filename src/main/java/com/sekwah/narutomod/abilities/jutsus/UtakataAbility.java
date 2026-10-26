package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
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
import net.minecraft.world.phys.Vec3;

/**
 * Utakata - a pointed finger that stops a mind in the middle of what it was doing.
 *
 * <p>There is no shape to the technique: one finger, one moment of eye contact, and the victim's
 * body simply stops obeying it. For a second and a half the creature does nothing at all, and for
 * two seconds after that the world is black to it. What makes it worth a ninja's chakra is not the
 * pause - a pause is easy - but that a technique already being held falls apart in it: the
 * concentration that was keeping it up is not there any more.</p>
 *
 * <p>The stun is short and it does no damage by design. A ninja uses it to interrupt a caster, to
 * walk past a sentry, or to open a guard for somebody else's blade. The hold is enforced by the
 * upkeep hook from the mark written here.</p>
 */
public class UtakataAbility extends Ability implements Ability.Cooldown {

    /** Nothing but the caster's voice; the technique itself is silent. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C V B - the genjutsu combination (C B C) and the branch's other road. */
    public static final long COMBO = 13121L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "utakata");

    /** Game time at which the victim's body starts answering again. Read by the upkeep hook. */
    public static final String UNTIL_TAG = "narutomod:utakata_until";

    /** UUID string of the ninja the stunned victim cannot turn on in the meantime. */
    public static final String SOURCE_TAG = "narutomod:utakata_source";

    /** How far down the look ray the finger can point. */
    private static final double RANGE = 10.0d;

    /** What it costs and how long before it can be pointed again. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 200;

    /** One and a half seconds without action. */
    private static final int STUN_TICKS = 30;

    /** Two seconds of darkness: a little longer than the stun, so the victim wakes up blind. */
    private static final int BLIND_TICKS = 40;

    /** Particles are drawn every third of a block so the line reads as one beam. */
    private static final double BEAM_STEP = 0.35d;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.utakata.notarget",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Friends.isOwnSide(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.utakata.ally",
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

        long until = level.getGameTime() + STUN_TICKS;
        target.getPersistentData().putLong(UNTIL_TAG, until);
        target.getPersistentData().putString(SOURCE_TAG, player.getUUID().toString());
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));

        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        } else if (target instanceof Player other) {
            // A body that is being held cannot keep a channel open: the technique it was feeding
            // simply stops, which is the whole point of interrupting a caster mid-cast.
            INinjaData otherData = other.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
            if (otherData != null) {
                otherData.setCurrentlyChanneledAbility(other, null);
            }
        }

        player.displayClientMessage(Component.translatable("jutsu.utakata.cast",
                target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        drawFingerLine(level, player, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.35f, 1.6f);
    }

    /** @return the living body under the ninja's gaze, or null when nothing living is in the way. */
    private static LivingEntity findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    /**
     * Draws the pointing as a thin line of light from the caster's fingertip to the victim's eyes -
     * the picture of where the technique was aimed, which is not otherwise visible.
     */
    private static void drawFingerLine(ServerLevel level, Player player, LivingEntity target) {
        Vec3 from = player.getEyePosition()
                .add(player.getViewVector(1.0f).scale(0.4d))
                .add(0.0d, -0.25d, 0.0d);
        Vec3 to = target.getEyePosition();
        Vec3 path = to.subtract(from);
        double length = path.length();
        int steps = (int) Math.max(1.0d, length / BEAM_STEP);
        for (int step = 0; step <= steps; step++) {
            Vec3 at = from.add(path.scale((double) step / steps));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), to.y, target.getZ(),
                6, 0.15d, 0.1d, 0.15d, 0.0d);
    }
}
