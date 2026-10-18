package com.sekwah.narutomod.client;

import com.sekwah.narutomod.network.s2c.ClientboundOreSensePacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Client end of the ore sense pulse: the blocks the server found and how long the picture has left.
 *
 * <p>A pulse is a moment, not a stance. The positions arrive once, are drawn for five seconds and
 * then dropped; nothing asks the server for a refresh. The last second fades out so the boxes do not
 * blink off all at once. The state is cleared when the level goes away, so leaving a world does not
 * leave the previous pulse hanging in the next one.</p>
 */
public final class ClientOreSenseData {

    /** How long a pulse stays visible, in client ticks. */
    private static final int DURATION = 20 * 5;

    /** How long the fade at the end of the pulse lasts, in ticks. */
    private static final float FADE_TICKS = 20f;

    private static List<ClientboundOreSensePacket.Marker> markers = List.of();
    private static long expiresAt;

    private ClientOreSenseData() {
    }

    /** Stores a freshly found pulse and starts its clock. */
    public static void apply(List<ClientboundOreSensePacket.Marker> incoming) {
        markers = new ArrayList<>(incoming);
        Minecraft mc = Minecraft.getInstance();
        expiresAt = mc.level == null ? 0L : mc.level.getGameTime() + DURATION;
    }

    /** The blocks still being drawn. Empty once the pulse has run out. */
    public static List<ClientboundOreSensePacket.Marker> markers() {
        if (alpha() <= 0f) {
            return List.of();
        }
        return markers;
    }

    /** 1 while the pulse is solid, sliding to 0 over its final second, and 0 once it is gone. */
    public static float alpha() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || expiresAt <= 0L) {
            return 0f;
        }
        long left = expiresAt - mc.level.getGameTime();
        if (left <= 0L) {
            return 0f;
        }
        return Math.min(1f, left / FADE_TICKS);
    }

    /** Drops the pulse when the level is gone or the time is up. Called once per client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            markers = List.of();
            expiresAt = 0L;
            return;
        }
        if (expiresAt > 0L && mc.level.getGameTime() >= expiresAt) {
            markers = List.of();
            expiresAt = 0L;
        }
    }
}
