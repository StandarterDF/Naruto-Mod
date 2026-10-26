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
import net.minecraft.world.entity.player.Player;

/**
 * Face swap: the illusion that the caster is everybody.
 *
 * <p>A mind already unsure of itself can be shown a whole room wearing the wrong face. The victim
 * sees the caster's face on every other body - the friend beside them, the enemy in front of them,
 * the empty air at the edge of the clearing - and cannot tell which one is the hand holding the
 * technique. For six seconds nobody is who they were, which is exactly long enough for the caster to
 * walk out of the room, and the caster is left unseen while it happens.</p>
 *
 * <p>Nothing is copied and nothing is transformed: the face is a lie told to one mind, so this class
 * only marks the victim and leaves the rest to the renderer of that mind. Turning it on another
 * player is a political act on a server, so it sits behind the same flag as the rest of the
 * player-facing illusions.</p>
 */
public class FaceSwapAbility extends Ability implements Ability.Cooldown {

    /** Nothing here disturbs the world: the whole technique is a picture in one head. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C C A B - the genjutsu combination (C B C), then the branch's fourth step. */
    public static final long COMBO = 13132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "face_swap");

    /**
     * Game time at which the victim's sight settles back into their own head. The illusion itself is
     * carried by the effects, so the mark is informational: it is what a later hook reads to tell
     * that the body is still wearing somebody else's face.
     */
    public static final String UNTIL_TAG = "narutomod:face_swap_until";

    /** How far the caster can reach a mind they can see. */
    private static final double RANGE = 12.0d;

    /** What it costs and how long before the mind can be shown the same room again. */
    private static final float COST = 80.0f;
    private static final int COOLDOWN = 600;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Six seconds of a room full of the wrong face. */
    private static final int DURATION = 120;

    /** Vertigo enough to lose a fight's rhythm, not enough to fall over. */
    private static final int CONFUSION_AMPLIFIER = 1;

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
        if (!NarutoConfig.genjutsuOnPlayers) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.players_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (target(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.face_swap.notarget",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        Player victim = target(player);
        if (victim == null || !(player.level() instanceof ServerLevel level)) {
            // The look was paid for a moment ago; the other ninja can have stepped out of it since.
            return;
        }

        // The victim's world is the one that has to change: their eyes lose their footing and every
        // body in them takes the caster's face, while the caster is simply not there to be seen.
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DURATION,
                CONFUSION_AMPLIFIER, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DURATION, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0, false, true));
        victim.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + DURATION);

        victim.displayClientMessage(Component.translatable("jutsu.face_swap.victim",
                player.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        player.displayClientMessage(Component.translatable("jutsu.face_swap.cast",
                victim.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        level.sendParticles(ParticleTypes.WITCH, victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.85d, victim.getZ(),
                30, 0.5d, 0.6d, 0.5d, 0.02d);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.65f);
    }

    /**
     * @return the other ninja under the caster's gaze - not a friend, not on the caster's side, and
     *         not somebody who cannot be touched - or null when the look lands anywhere else.
     */
    private static Player target(Player caster) {
        return Targeting.lookedAtEntity(caster, RANGE) instanceof Player other
                && other != caster
                && !other.isCreative()
                && !other.isSpectator()
                && !other.isAlliedTo(caster)
                && !Friends.areFriends(caster, other)
                ? other : null;
    }
}
