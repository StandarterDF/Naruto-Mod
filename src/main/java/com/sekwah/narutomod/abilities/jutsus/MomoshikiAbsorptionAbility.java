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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Momoshiki's absorption: the eye in the palm that eats a technique instead of being hit by it.
 *
 * <p>The palm is <b>held</b> open, not switched on. While the channel lasts, any of the mod's thrown
 * techniques that lands on the caster is swallowed whole - no damage, no knockback, the projectile
 * simply stops existing - and its nature is kept in the palm. The caster then hands it back without
 * meaning to: the next technique of that same nature to land deals half again as much damage for
 * every charge held, and spends all of them at once. Letting go shuts the palm and drops whatever it
 * was holding.</p>
 *
 * <p>The stance is built on the Rinnegan for the same reason the rest of the eye's techniques are:
 * the palm eye in the setting is a Rinnegan, so a caster without an open eye has nothing to catch a
 * technique with. Its charges live in {@code AbsorptionData}; the catching and the handing back are
 * done by {@code AbsorptionEvents}.</p>
 */
public class MomoshikiAbsorptionAbility extends Ability implements Ability.Channeled {

    /** B V B V - one key off the eye itself, and easy to reach in a fight. */
    public static final long COMBO = 3232L;

    public static final ResourceLocation ID =
            new ResourceLocation(NarutoMod.MOD_ID, "momoshiki_absorption");

    /** Cheap to hold: the palm only pays for being open, not for what it catches. */
    private static final float CHAKRA_PER_TICK = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** A palm holding chakra shines a little; nothing here disturbs the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The palm is a hold, not a switch: a tap produces nothing at all rather than opening and
     * closing it in the same breath.
     */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * Called every tick the palm is actually held open. Mastery is earned by the time it stays open,
     * not by the key press that started it - and the palm is shut the moment the channel ends, which
     * also drops whatever it was holding.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        this.aura(player);
    }

    /**
     * The channel has ended, whether the caster let go or ran out of chakra: the palm closes and the
     * borrowed chakra goes with it.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        ninjaData.getAbsorptionData().clear();
    }

    /**
     * A hold is mastered by the time it is held, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** A slow swirl of borrowed chakra around the open hand. */
    private void aura(Player player) {
        if (!(player.level() instanceof ServerLevel level) || player.tickCount % 4 != 0) {
            return;
        }
        Vec3 hand = player.getEyePosition()
                .add(player.getLookAngle().scale(0.7d))
                .add(0.0d, -0.35d, 0.0d);
        level.sendParticles(ParticleTypes.ENCHANT,
                hand.x, hand.y, hand.z, 1, 0.12d, 0.12d, 0.12d, 0.01d);
    }

    /** @return whether this caster is holding the palm open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ID.equals(ninjaData.getCurrentlyChanneledAbility());
    }
}
