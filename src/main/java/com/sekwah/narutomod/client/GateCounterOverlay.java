package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.EightGatesAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How many of the eight inner gates are open, kept on screen while any of them is.
 *
 * <p>The gates are the one mode a caster cannot read off their own body. Speed, strength and the
 * rest arrive as effects that every other mode hands out something like, the drain is a health bar
 * that also moves for a hundred ordinary reasons, and the only warning that the eighth gate cannot
 * be sealed is a sentence in chat that has long scrolled away by the time it matters. So the count
 * is written where the mode line already is: one short line under it, in the same corner, drawn only
 * while at least one gate is open.</p>
 *
 * <p>The number is read from the caster's own data when that data carries it. It does not always:
 * the counter is server-side state, and a client that never receives it is shown the number the
 * gates leave on the body instead - the four effects the technique refreshes in lock step every
 * tick, which only agree on one number when they are the gates' own. The last gate is drawn red,
 * because at eight the line is not a status any more, it is a countdown to the caster's death.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GateCounterOverlay {

    /** The same left edge as the mode line, see {@code ModeHudOverlay}. */
    private static final int X = 8;

    /** One line below the mode line, which starts at the top row of the HUD. */
    private static final int Y = 8 + 12;

    private static final int LABEL_COLOUR = 0xFFF2F2F2;

    /** The eighth gate can never be sealed again, so the line turns the colour of a warning. */
    private static final int FINAL_GATE_COLOUR = 0xFFFF5555;

    /** The label, in the same shape the language files use for every other HUD line. */
    private static final String LABEL_KEY = "naruto.gui.gates";

    /** How the open gates are counted out, so the line reads as a rank rather than a number. */
    private static final String[] ROMAN = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII"
    };

    private GateCounterOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null) {
            return;
        }
        int gates = gatesOpen(player, ninjaData);
        if (gates <= 0) {
            return;
        }
        Component line = Component.translatable(LABEL_KEY, ROMAN[gates], EightGatesAbility.MAX_GATES);
        int colour = gates >= EightGatesAbility.MAX_GATES ? FINAL_GATE_COLOUR : LABEL_COLOUR;
        event.getGuiGraphics().drawString(minecraft.font, line, X, Y, colour, true);
    }

    /**
     * How many gates are open, as the client can tell.
     *
     * <p>The counter itself is preferred, since it is the number the server is actually draining
     * from. When the client does not receive it the body is read instead, and the read is strict:
     * only a stack whose amplifiers all agree on the same gate count is believed, so a stray speed
     * or strength buff from somewhere else hides the line rather than making it lie.</p>
     */
    private static int gatesOpen(Player player, INinjaData ninjaData) {
        int counted = ninjaData.getGateCount();
        if (counted > 0) {
            return Math.min(counted, EightGatesAbility.MAX_GATES);
        }
        return gatesFromBody(player, ninjaData);
    }

    /**
     * The gates as the body shows them. {@code EightGatesAbility#maintain} rewrites all four effects
     * from the same number every tick, and sage mode is the only other thing in the mod that hands
     * out the same four at once - at low mastery with exactly the one gate amplifiers - so a sage
     * caster is never read as having a gate open.
     */
    private static int gatesFromBody(Player player, INinjaData ninjaData) {
        if (ninjaData.isSageMode()) {
            return 0;
        }
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
        if (!isGateEffect(speed) || !isGateEffect(strength) || !isGateEffect(resistance) || !isGateEffect(jump)) {
            return 0;
        }
        for (int gates = 1; gates <= EightGatesAbility.MAX_GATES; gates++) {
            if (speed.getAmplifier() == gates - 1
                    && strength.getAmplifier() == (gates - 1) / 2
                    && resistance.getAmplifier() == (gates - 1) / 3
                    && jump.getAmplifier() == Math.min(gates - 1, 3)) {
                return gates;
            }
        }
        return 0;
    }

    /**
     * The gates' effects are hidden ones: no icon and no swirl, so nothing else the caster picks up
     * casually looks like them. A visible or non-ambient effect of the same kind is somebody else's.
     */
    private static boolean isGateEffect(MobEffectInstance effect) {
        return effect != null && effect.isAmbient() && !effect.isVisible();
    }
}
