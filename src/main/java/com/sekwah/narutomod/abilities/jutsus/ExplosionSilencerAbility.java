package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.events.SummonMedicineEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * A sealing array that swallows the noise of a blast before it can take the ground with it.
 *
 * <p>Not a shield: the explosion still happens and still hurts. What the seal takes is the part that
 * makes an explosion a disaster - the shock that tears through stone - and half of the force that
 * reaches a body inside it. A battlefield sealed this way can be fought in with fire and paper bombs
 * without the terrain, and whoever is standing in it, being turned into a crater.</p>
 *
 * <p>The seal is anchored where it was drawn rather than on the caster, so it stays behind and keeps
 * working after they have moved on. It also outlives the caster for its full span: a seal is a thing
 * left in the world, not a stance. The zone itself lives in {@code SummonMedicineEvents}, which is
 * what the explosions are checked against.</p>
 */
public class ExplosionSilencerAbility extends Ability implements Ability.Cooldown {

    /** V C V B C C - the sealing line, one step past the sacrifice. */
    public static final long COMBO = 212311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "explosion_silencer");

    /**
     * Marks the caster as the one who drew a working seal. Kept public because the technique is read
     * by the same hooks that enforce it.
     */
    public static final String SILENCER_UNTIL_KEY = "narutomod:explosion_silencer_until";

    /** Chakra for drawing the array. */
    private static final float COST = 40.0f;

    private static final int COOLDOWN = 600;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far from the drawing point the seal reaches. */
    public static final double ZONE_RADIUS = 12.0d;

    /** How long the seal stays live. */
    public static final int DURATION = 900;

    /** A seal that swallows sound makes none of its own. */
    @Override
    public int noiseRadius() {
        return 0;
    }

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
        float cost = this.masteryCost(ninjaData, COST);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        player.getPersistentData().putLong(SILENCER_UNTIL_KEY, level.getGameTime() + DURATION);
        SummonMedicineEvents.addSilencerZone(level, player.position(), DURATION);
    }
}
