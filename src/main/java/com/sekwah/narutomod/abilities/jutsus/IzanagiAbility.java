package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Izanagi: the eye spending itself to say that a moment did not happen.
 *
 * <p>Every other Sharingan technique acts on somebody else's mind. This one acts on the caster's
 * own death: for five seconds after the cast the next blow that would finish them simply does not
 * land, and the body is left standing with a little less than half of itself - hurt, but alive. The
 * price is not chakra alone. Closing the eye on a moment that has already happened burns it out,
 * and the caster walks away blind for forty-five seconds, which is a very long time to be blind in
 * a fight that has just proved it can kill them.</p>
 *
 * <p>The grace is a timestamp in the caster's persistent data rather than a stored reference to the
 * blow that has not happened yet, so it survives a relog and cannot outlive the world it was
 * written in. {@code SharinganIllusionEvents} is the half that reads it and spends it; the cast
 * only writes the promise and shows the eye turning.</p>
 */
public class IzanagiAbility extends Ability implements Ability.Cooldown {

    /** The moment being rewritten belongs to the caster alone: the world hears nothing. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B C - the Sharingan combo (C V V B), then the two keys that survive a death. */
    public static final long COMBO = 111223L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "izanagi");

    /** Game time until which a killing blow is refused. Read and spent by the parent's hurt hook. */
    public static final String GRACE_TAG = "narutomod:izanagi_until";

    /** Game time at which the burnt-out eye opens again. Informational for the parent's hook. */
    public static final String BLIND_TAG = "narutomod:izanagi_blind_until";

    /** What it costs and how long before the eye can rewrite another moment. */
    private static final float COST = 120.0f;
    private static final int COOLDOWN = 800;

    /** Five seconds of grace: long enough to cover an exchange, short enough to be spent on purpose. */
    private static final int GRACE_TICKS = 100;

    /** Forty-five seconds of blindness: the eye is not merely tired, it is closed. */
    private static final int BLIND_TICKS = 900;

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
        long now = level.getGameTime();
        // Two promises on the same body: the moment that will be refused, and the eye that will be
        // dark afterwards. Writing both now means the hurt hook never has to decide how long the
        // blindness should last - it only has to read what the cast already decided.
        player.getPersistentData().putLong(GRACE_TAG, now + GRACE_TICKS);
        player.getPersistentData().putLong(BLIND_TAG, now + BLIND_TICKS);

        // The eye's chakra folding back on itself: a column of dark light and a single note of a
        // body arriving somewhere it was not a moment ago.
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                40, 0.35d, 0.6d, 0.35d, 0.02d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.5f);
    }
}
