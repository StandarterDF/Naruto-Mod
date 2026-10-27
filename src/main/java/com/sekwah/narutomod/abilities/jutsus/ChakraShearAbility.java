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
 * Chakra Shear: the Jougan's reading of a body worn on the hands instead of aimed once.
 *
 * <p>The eye already sees where a body's chakra runs thin; this stance keeps that picture in front
 * of the caster's own blows, so an ordinary punch lands on the seam and takes a little of the flow
 * with it. Against a ninja the cut also spills chakra, which is what turns the stance from a small
 * damage bonus into a way of wearing an opponent out.</p>
 *
 * <p>Nothing of the cut lives here. The stance only keeps {@link #UNTIL_TAG} in front of the clock;
 * {@code DojutsuLeftoverEvents} reads it on every melee blow and does the damage and the drain. That
 * keeps the ability a stance and the hit hook a consequence, and it is why the mark is stamped a few
 * ticks ahead: the frame a hit is registered in is not always the frame the stance was paid for.</p>
 *
 * <p>The eye has to stay open. A closed eye ends the stance on the next upkeep tick, because the
 * reading of the seam is the whole technique.</p>
 */
public class ChakraShearAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A stance is held, not shouted: the cut is heard when it lands. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V V C V V - the Jougan (B V V C) and two more keys. */
    public static final long COMBO = 1132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_shear");

    /**
     * The stamp the melee hook reads, see {@code DojutsuLeftoverEvents}. Written as game time so it
     * lapses on its own when the stance ends or the caster logs out mid-swing.
     */
    public static final String UNTIL_TAG = "narutomod:chakra_shear_until";

    /** The extra damage every cut blow carries. */
    public static final float BONUS_DAMAGE = 1.0f;

    /** Chakra taken from a ninja body by every cut blow, capped by what the target has left. */
    public static final float DRAIN = 4.0f;

    /** What holding the stance costs, and how long regeneration waits after a tick of it. */
    private static final float CHAKRA_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead of the clock the mark is kept while the stance is held. */
    private static final long MARK_TICKS = 5L;

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
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
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

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        // Pushed forward every tick rather than once, so a hit that lands between two upkeep ticks is
        // still inside the stance; letting go simply stops the stamp being renewed.
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_TICKS);

        if (player.level() instanceof ServerLevel level && player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                    player.getY() + player.getBbHeight() * 0.55d, player.getZ(),
                    2, 0.3d, 0.3d, 0.3d, 0.01d);
        }
    }

    /**
     * A stance is mastered by holding it, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The mark is server side; there is nothing for the client to draw.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
