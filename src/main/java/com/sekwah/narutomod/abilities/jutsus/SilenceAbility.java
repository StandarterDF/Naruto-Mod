package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Holds a thin shell of chakra over the caster that swallows the noise they make.
 *
 * <p>While it is up the caster's footsteps, jumps, blows and everything else they do stop feeding the
 * world's game events, which is what the skulk listens to: sculk sensors stay silent, shriekers never
 * scream, and the warden below never hears the caster at all. Mob ears are dulled the same way - a
 * creature will still spot someone right in front of it, but not from across the room. It mutes the
 * caster only, not the world: a torch dropped or a sheep that bleats is still heard.</p>
 *
 * <p>It is not the smoke screen, which hides the caster to the eye and only for as long as the cloud
 * stands, and it is not a calming effect - nothing loses a target it already has. The caster is simply
 * not heard while the chakra is paid. The game-event half lives in
 * {@code GameEventDispatcherMixin} and the dulled ears in {@code SilenceEvents}; all this class owns is
 * the stance and its upkeep.</p>
 */
public class SilenceAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** V V V. */
    public static final long COMBO = 333L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "silence");

    /** Chakra burnt for every tick the shell is held. */
    private static final float DRAIN_PER_TICK = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How close a mob has to be before it can still notice a silenced caster, in blocks. Anything
     * further than this is refused as a target; something standing right in front of them is not.
     */
    public static final double DETECTION_RANGE = 8.0d;

    /**
     * How far out existing targets are culled, in blocks. A mob that already had the caster in its
     * sights from further away gives them up within this reach, which is wider than the notice range
     * so a chase that started before the silence was up also ends.
     */
    public static final double CULL_RANGE = 48.0d;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * A stance the caster can run out of: when the chakra is gone the next tick ends it, which is a
     * cleaner way out than letting the bar go dry mid-sneak.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.silenceEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.silence.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, DRAIN_PER_TICK);
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
        // A stance is mastered by holding it, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Nothing to draw; the world simply does not hear the caster.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to clean up.
    }

    /** @return whether the given player is currently holding the silence stance. */
    public static boolean isSilenced(Player player) {
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .map(SilenceAbility::isSilenced)
                .orElse(false);
    }

    /** @return whether the given ninja data has the silence stance up. */
    public static boolean isSilenced(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
