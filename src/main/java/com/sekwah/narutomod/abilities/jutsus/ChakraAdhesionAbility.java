package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Chakra spread under the hands and feet so the caster sticks to whatever they are pressed against
 * and walks up it.
 *
 * <p>This is not the chakra thread. The thread latches onto one point and reels the caster in, which
 * is fast but only works where there is something to latch onto and open air to be pulled through. A
 * tight cave is exactly where a thread is useless: adhesion needs no anchor and no room, only a
 * surface to hold, and it moves slowly and under the caster's own control. The two cover each other -
 * the thread for a gap you can see across, adhesion for a wall you have to climb.</p>
 *
 * <p>What is written each tick is only the vertical part of the motion, and only while the caster is
 * actually pressed into something ({@code horizontalCollision}); the horizontal speed they had is
 * left alone, so they can shuffle sideways along the wall while hanging over a drop. The fall is
 * forgotten every tick it holds, so letting go is the only way to fall. The body is rewritten on both
 * sides - server side here and client side in {@code ClientAdhesionHandler} - because a player's
 * velocity is theirs to send, and a nudge that only lives on the server is thrown away by the client
 * on the next movement packet.</p>
 */
public class ChakraAdhesionAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C C V. */
    public static final long COMBO = 113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_adhesion");

    /** Chakra burnt for every tick the hold is kept. */
    private static final float CHAKRA_PER_TICK = 0.15f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How fast the caster rises, in blocks per tick, from untrained to fully mastered. */
    private static final double BASE_CLIMB_SPEED = 0.12d;
    private static final double MASTERY_CLIMB_SPEED = 0.08d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** There is nothing to hold for an instant; the stance has to be kept. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!NarutoConfig.adhesionEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.adhesion.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * Climbs the caster up whatever they are pressed against. Only the vertical part of the motion is
     * touched, so the sideways speed they carried into the wall keeps working along it.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!player.horizontalCollision) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, climbSpeed(ninjaData), motion.z);
        // The client owns its own velocity; without this the nudge would be overwritten next tick.
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
    }

    /**
     * The hold ends when the keys come up or the chakra runs dry. There is nothing to clean up - the
     * fall is allowed to count again from the moment the grip is gone, which is the point.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
    }

    /** How fast the climb is at the caster's mastery. Shared with the client end of the movement. */
    public static double climbSpeed(INinjaData ninjaData) {
        return BASE_CLIMB_SPEED + MASTERY_CLIMB_SPEED * Mastery.level(ninjaData, ID);
    }
}
