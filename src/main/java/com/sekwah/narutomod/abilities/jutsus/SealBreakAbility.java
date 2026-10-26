package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
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
 * The seal cracked open on purpose: eight seconds of the beast driving instead of the ninja.
 *
 * <p>It costs nothing to let go, because it is not a technique that is paid for - it is a debt. The
 * caster is faster, stronger and harder to hurt while it lasts, and is no longer careful about who is
 * standing next to them: every second the frenzy throws a sweep out from the body, and the caster's own
 * blows stop sparing allies. When it ends the body pays for the eight seconds with its own blood and
 * the vessel loses a fifth of itself.</p>
 *
 * <p>The technique only writes the mark and the boons; the sweep, the missing friendly fire and the
 * price at the end are consequences of the mark and live in {@code events/TailedBeastEvents}. Unlike
 * the other forms it does not need the vessel to be strong - only to exist - because a crack is
 * exactly what a nearly empty vessel is good for.</p>
 */
public class SealBreakAbility extends Ability implements Ability.Cooldown {

    /** The roar of the crack carries across a fight. */
    @Override
    public int noiseRadius() {
        return 24;
    }

    /** C C B B B A - the seal, then the three keys that break it. */
    public static final long COMBO = 332221L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "seal_break");

    /** Tick the frenzy lets go. */
    public static final String UNTIL_TAG = "narutomod:seal_break_until";

    /** Eight seconds is long enough to win a fight or lose one. */
    public static final int DURATION = 160;

    /** A minute before the cracked seal can be forced again. */
    private static final int COOLDOWN = 1200;

    /** What the frenzy throws out every second and how far it reaches. */
    public static final int SWEEP_INTERVAL = 20;
    public static final double SWEEP_RADIUS = 2.0d;
    public static final float SWEEP_DAMAGE = 5.0f;

    /** What the body pays when it is handed back, and what the vessel loses. */
    public static final float BACKLASH = 6.0f;
    public static final int INTEGRITY_COST = 20;

    /** The boons of the frenzy: speed II, force II, and the hide of a beast. */
    private static final int SPEED_AMPLIFIER = 1;
    private static final int STRENGTH_AMPLIFIER = 1;
    private static final int RESISTANCE_AMPLIFIER = 0;

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

    /** Free to cast: the vessel and the body pay for it instead. */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (SealVesselAbility.integrity(player) <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        long now = player.level().getGameTime();
        player.getPersistentData().putLong(UNTIL_TAG, now + DURATION);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, SPEED_AMPLIFIER, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, STRENGTH_AMPLIFIER, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, RESISTANCE_AMPLIFIER, true, false));

        player.displayClientMessage(Component.translatable("jutsu.seal_break.start")
                .withStyle(ChatFormatting.RED), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    60, 0.7d, 1.0d, 0.7d, 0.06d);
            level.playSound(null, player, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    /** @return whether the frenzy is currently driving this caster. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /**
     * The frenzy announces itself and pays for itself, so the generic cast line is dropped rather than
     * arriving in the same breath as the roar.
     */
    @Override
    public boolean logInChat() {
        return false;
    }
}
