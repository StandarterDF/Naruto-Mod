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

/**
 * The Senrigan remembering what has just been lost.
 *
 * <p>The far eye reads a place's past rather than its present, and death is the sharpest thing a
 * place remembers. For ten seconds after the caster opens this window, every living body that falls
 * within sixteen blocks leaves its name standing where it died - a grey silhouette that stays long
 * enough for the caster to read the field after the fighting has stopped.</p>
 *
 * <p>This class only opens the window. The deaths themselves are caught by
 * {@code DojutsuLeftoverEvents}, because a technique should not have to listen to the world to do
 * its work, and the mark is per caster so two Senrigan users do not share one another's echoes.</p>
 */
public class SenriganEchoAbility extends Ability implements Ability.Cooldown {

    /** Pure sight, so opening the window makes no noise. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V B B V V - the Senrigan (B V B B) and two more keys. */
    public static final long COMBO = 323311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "senrigan_echo");

    /** The window in which deaths are remembered, written as game time on the caster. */
    public static final String UNTIL_TAG = "narutomod:death_echo_until";

    /** Marks a silhouette that belongs to this technique, and when the world may forget it. */
    public static final String ECHO_TAG = "narutomod:death_echo";
    public static final String ECHO_UNTIL_TAG = "narutomod:death_echo_expires";

    /** How long the window stays open, and how far around the caster it sees. */
    public static final int WINDOW_TICKS = 200;
    public static final double RANGE = 16.0d;

    /** What it costs and how long before the eye can be opened to death again. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 600;

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
        if (!NarutoConfig.senriganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SenriganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.required",
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
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + WINDOW_TICKS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.8d, player.getZ(),
                    18, 0.6d, 0.7d, 0.6d, 0.01d);
        }
    }

    /** @return whether a caster's window is still open at the given game time. */
    public static boolean isWindowOpen(Player player, long gameTime) {
        return player.getPersistentData().getLong(UNTIL_TAG) > gameTime;
    }
}
