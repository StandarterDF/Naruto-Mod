package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.world.entity.player.Player;

/**
 * More of a slight speed boost than an actual dash
 */
public class ChakraChargeAbility extends Ability implements Ability.Channeled {

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return 1;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // This jutsu can always be cast
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Add particle effects n stuff
    }


    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
       float rate = (player.isSprinting() || !player.onGround()) ? 0.2f : 1f;
       // Mastery is earned by the chakra this actually put back into the bar, so standing still and
       // charging a full bar is a real piece of practice while tapping the keys on a full bar is
       // worth nothing. The gain is read back off the bar rather than assumed, so chakra that never
       // fit - a full or nearly full bar - does not count as work.
       float before = ninjaData.getChakra();
       ninjaData.addChakra(rate);
       float restored = ninjaData.getChakra() - before;
       this.recordMasteryCredit(player, ninjaData, restored / Math.max(1f, ninjaData.getMaxChakra()));
    }

    /**
     * A channelled gathering technique is not mastered by how often it is cast but by the chakra it
     * restored, which {@link #handleChannelling} already paid in - so the end-of-cast credit that
     * every other technique gets is deliberately left empty here.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }
}
