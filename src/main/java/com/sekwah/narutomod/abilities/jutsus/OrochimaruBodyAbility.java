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
 * Orochimaru's body replacement: the wounded body is left behind like a shed skin and a fresh one
 * stands where it fell.
 *
 * <p>The technique is not a heal. Nothing is restored - the bar stays where it was - but everything
 * the body was carrying is cast off: fire, poison, wither, the stiffness of a slowed limb. What
 * replaces it is a short shell of hardened chakra that turns the next few seconds of the fight
 * aside, which is the real value: a body dragged out of a losing exchange with a moment to
 * breathe.</p>
 *
 * <p>It is paid for out of what is left rather than a fixed price - a scattering of health and two
 * fifths of whatever chakra remains - so it is a technique that costs most when the caster is
 * fullest and can always be afforded when it matters. The long cooldown is what keeps it from being
 * a dodge button.</p>
 */
public class OrochimaruBodyAbility extends Ability implements Ability.Cooldown {

    /** Shedding a body is loud, wet work; it carries as far as a shout. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** C C B C A B - the body replacement. */
    public static final long COMBO = 2321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "orochimaru_body");

    /**
     * The mark that says the fresh shell is still on. It is the seam the parent reads if anything
     * else needs to know the caster just shed a body.
     */
    public static final String SHELL_UNTIL_TAG = "narutomod:orochimaru_shell_until";

    /** Two fifths of the chakra still in the pool. */
    private static final float CHAKRA_FRACTION = 0.4f;

    /** Health spent to tear the old body off, in points. */
    private static final float HEALTH_COST = 6.0f;

    /** How long the shell of hardened chakra holds, and how strong it is. */
    private static final int SHELL_TICKS = 60;
    private static final int SHELL_AMPLIFIER = 4;

    /** Long enough that it is a decision rather than a habit. */
    private static final int COOLDOWN = 2400;

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
        if (player.getHealth() <= HEALTH_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mode.notenoughhealth",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, ninjaData.getChakra() * CHAKRA_FRACTION);
        ninjaData.useChakra(cost, 20);
        // The old body is torn off rather than mended, so the price is paid in real damage: armour
        // and resistance are allowed to blunt it, as with any other wound.
        player.hurt(player.damageSources().magic(), HEALTH_COST);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // Everything the old body was carrying is left behind with it.
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                SHELL_TICKS, SHELL_AMPLIFIER, true, false));
        player.getPersistentData().putLong(SHELL_UNTIL_TAG, level.getGameTime() + SHELL_TICKS);

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                40, 0.6d, 0.9d, 0.6d, 0.05d);
        level.playSound(null, player, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9f, 0.8f);
        player.displayClientMessage(Component.translatable("jutsu.orochimaru_body.shed")
                .withStyle(ChatFormatting.DARK_GREEN), true);
    }
}
