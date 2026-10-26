package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ByakuganFarsightAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of the Byakugan's far sight: the eye draws the world closer.
 *
 * <p>The technique itself is a stance the server keeps alive and charges for, so the client only has
 * to answer one question - is it running on this player right now - and narrow the field of view
 * while the answer is yes. The narrowing is eased rather than snapped, because a hard cut reads as a
 * bug and an instant zoom is disorienting in a fight; the ease is the point, not decoration.</p>
 *
 * <p>Nothing here decides what the eye can see. The picture of bodies through walls is the sensing
 * renderer's job, and it already reads the same synced stance.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFarsightHandler {

    /** How much of the normal field of view is left at full focus - roughly a four times zoom. */
    private static final float FOCUS = 0.28f;

    /** How many client ticks the eye takes to come to full focus and to let go again. */
    private static final float EASE_TICKS = 6f;

    /** 0 when the eye is relaxed, 1 when it is fully focused. */
    private static float focus;

    private ClientFarsightHandler() {
    }

    /** Tracks the stance once a tick so the field of view can be eased towards it. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float target = isFocused(minecraft) ? 1f : 0f;
        float step = 1f / EASE_TICKS;
        if (focus < target) {
            focus = Math.min(target, focus + step);
        } else if (focus > target) {
            focus = Math.max(target, focus - step);
        }
    }

    /**
     * Narrows the field of view while the stance is up. Third person and a spyglass are left alone:
     * the far sight is a first-person technique, and a spyglass already owns the field of view.
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (focus <= 0f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON || minecraft.player == null) {
            return;
        }
        if (minecraft.player.isScoping()) {
            return;
        }
        float scale = 1f - (1f - FOCUS) * focus;
        event.setFOV(event.getFOV() * scale);
    }

    /** @return whether the far sight is running on the local player right now. */
    private static boolean isFocused(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || !NarutoConfig.byakuganEnabled) {
            return false;
        }
        INinjaData data = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        return data != null && data.getToggleAbilityData().getAbilitiesHashSet()
                .contains(ByakuganFarsightAbility.ID);
    }
}
