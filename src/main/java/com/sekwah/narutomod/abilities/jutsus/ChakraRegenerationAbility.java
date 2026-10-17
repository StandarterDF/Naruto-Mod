package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Knits the caster's wounds shut with chakra and whatever is in their stomach.
 *
 * <p>Vanilla healing is paid for out of saturation, so a starving ninja heals slowly and one who has
 * not eaten at all heals not at all. This runs the other way round: it burns chakra to close the
 * wound and food to feed the process, so a caster who is full of chakra and has a meal in them can
 * put themselves back together in the middle of a fight instead of waiting for the hunger bar.</p>
 *
 * <p>The balance is deliberately flat: one point of health per mouthful of food and a small handful
 * of chakra, two points of health a second. Food is the real cost, so running out of it makes the
 * technique roughly twice as expensive in chakra and eventually impossible.</p>
 *
 * <p>It closes wounds fast, faster than any potion does, because the price is paid twice over for
 * every point it knits shut: it is meant to be the thing a ninja falls back on when they are losing
 * a fight, not an out-of-combat top-up, and the chakra and the hunger it burns are meant to hurt.</p>
 */
public class ChakraRegenerationAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** V B B C */
    public static final long COMBO = 2331L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_regeneration");

    /**
     * How often a point of health is knitted back, in ticks. Ten ticks is two points of health a
     * second, which is far quicker than any potion - it used to be one every two seconds, which was
     * too slow to save anyone, hence the shortened interval.
     */
    private static final int HEAL_INTERVAL = 10;

    /** Chakra for one point of health while there is food to burn as well. */
    private static final float CHAKRA_PER_HEAL = 6.0f;

    /** Chakra for one point of health with an empty stomach, where chakra has to do all the work. */
    private static final float CHAKRA_PER_HEAL_STARVING = 15.0f;

    /** Food points burned per point of health. Two of these make one drumstick on the hunger bar. */
    private static final int FOOD_PER_HEAL = 1;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * The stance only needs enough chakra for one more wound; when even a starving caster could not
     * pay for the next point of health the technique turns itself off rather than sit there doing
     * nothing.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (ninjaData.getChakra() < CHAKRA_PER_HEAL) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (player.tickCount % HEAL_INTERVAL != 0 || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        FoodData food = player.getFoodData();
        boolean fed = food.getFoodLevel() > 0;
        float cost = fed ? CHAKRA_PER_HEAL : CHAKRA_PER_HEAL_STARVING;
        if (ninjaData.getChakra() < cost) {
            return;
        }

        ninjaData.useChakra(cost, HEAL_INTERVAL);
        if (fed) {
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - FOOD_PER_HEAL));
        }
        player.heal(1.0f);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                    player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                    6, 0.35d, 0.35d, 0.35d, 0.01d);
        }
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Nothing to show on the client, the particles come from the server.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to clean up.
    }
}
