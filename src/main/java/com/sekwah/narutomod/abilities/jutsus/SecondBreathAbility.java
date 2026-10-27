package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
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
 * The second life trick: every drop of chakra is spent up front to buy a single escape from death.
 *
 * <p>It does nothing at all until the caster is about to be killed, at which point the killing blow
 * is simply undone and the caster is left standing on a few hearts instead of a death screen. The
 * price is paid twice: the chakra is gone the moment the trick is armed, and once it has saved the
 * caster they are left half blind for a minute and cannot arm it again for ten.</p>
 */
public class SecondBreathAbility extends Ability implements Ability.Cooldown {
    /**
     * How far a cast of this technique carries as a game event. An ordinary cast.
     */
    @Override
    public int noiseRadius() {
        return 24;
    }


    /** V V B */
    public static final long COMBO = 3113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "second_breath");

    /** Chakra needed before the trick is worth arming; the whole bar goes when it is. */
    public static final float MIN_CHAKRA = 60.0f;

    /** Health the caster is left on when a killing blow is undone. */
    public static final float SECOND_LIFE_HEALTH = 10.0f;

    /** How long the caster is left half blind for, in ticks. */
    public static final int DARKNESS_DURATION = 20 * 60;

    private static final int COOLDOWN = 20 * 60 * 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (ninjaData.isSecondBreathArmed()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.secondbreath",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getChakra() < MIN_CHAKRA) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        ninjaData.setChakra(0.0f);
        ninjaData.setSecondBreathArmed(true);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(),
                    player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                    30, 0.4d, 0.6d, 0.4d, 0.02d);
            level.playSound(null, player, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    /**
     * Called when a killing blow is undone: blinds the caster and makes sure they cannot simply do it
     * again straight away.
     */
    public static void spendSecondLife(Player player, INinjaData ninjaData) {
        ninjaData.setSecondBreathArmed(false);
        // Mastery dulls the price: a seasoned caster comes back with their sight mostly intact
        // rather than half blind for a full minute, down to half that at the top of the curve.
        float mastery = 0.0f;
        int darkness = Math.round(DARKNESS_DURATION * (1.0f - 0.5f * mastery));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darkness, 0));
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}
