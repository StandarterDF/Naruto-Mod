package com.sekwah.narutomod.client;

import com.sekwah.narutomod.network.s2c.ClientboundRevealPacket;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Client end of the Byakugan's deeper sight: the blocks the server found, held until they fade.
 *
 * <p>The picture is deliberately short-lived. A reveal is a glance through a wall, not a map the
 * caster keeps; once the timer runs down the list is dropped and the renderer stops drawing. A new
 * cast replaces the old picture rather than stacking on it, so two looks in a row do not leave a
 * smear of old boxes behind.</p>
 */
public final class ClientRevealData {

    private static final float FADE_OUT_TICKS = 10f;

    private static List<ClientboundRevealPacket.Marker> markers = List.of();
    private static float remaining;
    private static float total = 1f;

    private ClientRevealData() {
    }

    /** Called from the packet handler. */
    public static void apply(List<ClientboundRevealPacket.Marker> incoming, int duration) {
        markers = new ArrayList<>(incoming);
        remaining = duration;
        total = Math.max(1f, duration);
    }

    /** Ticked by the client; the list clears itself when the glance is over. */
    public static void tick() {
        if (remaining <= 0f) {
            if (!markers.isEmpty()) {
                markers = List.of();
            }
            return;
        }
        remaining -= 1f;
    }

    public static List<ClientboundRevealPacket.Marker> markers() {
        return markers;
    }

    /** How solid the picture is, 1 at the moment of the cast and 0 when it is gone. */
    public static float alpha() {
        if (remaining <= 0f) {
            return 0f;
        }
        return Math.min(1f, remaining / FADE_OUT_TICKS) * Math.min(1f, remaining / total + 0.35f);
    }

    /** Whether a block position is currently part of the picture. Used by the field's HP overlay. */
    public static boolean holds(BlockPos pos) {
        for (ClientboundRevealPacket.Marker marker : markers) {
            if (marker.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
