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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Lightning Release worn as a mode: the body is charged until it moves like a spark and bites back at
 * anyone who touches it.
 *
 * <p>The mode is a third again as fast and leaves a wake of sparks. It also turns the caster into a
 * live circuit: a melee attacker is answered with a shock of its own, once every second, see
 * {@code ModeSealEvents}. That retaliation is the whole point - a lightning user is meant to be a bad
 * thing to hit - and it costs nothing beyond the upkeep.</p>
 *
 * <p>Water is the counter, and it is an immediate one. Stepping into rain, a river or the soaked
 * state a water technique leaves behind grounds the charge and drops the mode where it stands, so a
 * caster cannot simply keep it up through a storm or a fight in the shallows.</p>
 */
public class LightningModeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The mode is a quiet hum until something touches it; it does not post a game event. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B C A B - the lightning mode seal. */
    public static final long COMBO = 332131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "lightning_mode");

    /**
     * The mark that says the mode is up. The parent's retaliation and wet check both read it, and a
     * mark that has run out is what ends the mode after a death or a relog.
     */
    public static final String UNTIL_TAG = "narutomod:lightning_mode_until";

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("e5b3c972-7a14-4d68-90f2-3c8b6d1a4e75");

    /** A third again as fast. */
    private static final double SPEED_BONUS = 0.3d;

    /** Chakra paid for every tick the charge is held, before mastery. */
    private static final float UPKEEP = 0.35f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept while the mode is up. */
    private static final int MARK_GRACE = 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, UPKEEP);
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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        long now = player.level().getGameTime();
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;
        applySpeed(player);
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);

        if (player.level() instanceof ServerLevel level) {
            if (opening) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                        player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                        40, 0.5d, 0.9d, 0.5d, 0.1d);
                level.playSound(null, player, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6f, 1.6f);
            } else if (player.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                        player.getY() + player.getBbHeight() * 0.7d, player.getZ(),
                        2, 0.35d, 0.5d, 0.35d, 0.02d);
            }
        }
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applySpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "narutomod:lightning_mode",
                    SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * Takes the charge off the caster's attributes. Used when the mode ends and when a mark is found
     * to have expired without it (a death, a relog).
     */
    public static void removeModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    /** Whether the charge is currently held, asked by anything that leans on the mode. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** A stance is mastered by the time it is up, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attribute is synced from the server, so there is nothing to do on the client.
    }

    /** Letting the charge go takes its mark and its speed with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        removeModifiers(player);
    }
}
