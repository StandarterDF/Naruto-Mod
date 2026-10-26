package com.sekwah.narutomod.client;

import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraSensingAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.network.s2c.ClientboundSensingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client end of the chakra sense: which creatures the caster is feeling out and how far each has
 * resolved, plus the structure markers the server found.
 *
 * <p>The sense is staged. Every creature the caster can feel is carried here with an intensity from
 * zero to one: below one it is shown as a shell of particles that thickens as the intensity climbs,
 * and at one it is shown as the creature's own body through the walls. Only the creatures of a kind
 * the caster has mastered are listed at all, and {@code ChakraSensingAbility} owns those thresholds -
 * this class only asks it. The work of actually drawing all this lives in {@code SensingEspRenderer};
 * this is the state it reads.</p>
 *
 * <p>When the sense is let go the picture does not blink out: the last set of creatures is kept and
 * the whole thing is faded to nothing over a fraction of a second, so it reads as the sense letting
 * go of the world rather than a light being switched off.</p>
 */
public final class ClientSensingData {

    private ClientSensingData() {
    }

    /** How long the sense takes to dissolve once it is dropped, in client ticks (about a second). */
    private static final float FADE_OUT_TICKS = 20f;

    /** The creatures being felt out, each mapped to how far it has resolved (0..1). */
    private static Map<LivingEntity, Float> sensed = new IdentityHashMap<>();

    /** The structure markers the server last sent, and a copy kept so they can outlive the sense. */
    private static List<ClientboundSensingPacket.Marker> markers = List.of();
    private static List<ClientboundSensingPacket.Marker> lastMarkers = List.of();

    private static boolean active;
    private static float radius;
    private static float mastery;

    /** 1 while the sense is fully shown, sliding to 0 as it dissolves. Everything drawn is scaled by it. */
    private static float fade;

    /** Stores the structure markers the server just sent; an empty list drops them while the sense is up. */
    public static void applyMarkers(List<ClientboundSensingPacket.Marker> incoming) {
        markers = new ArrayList<>(incoming);
        if (!incoming.isEmpty()) {
            // Keep the last real picture so the markers can dissolve along with everything else.
            lastMarkers = markers;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static float getRadius() {
        return radius;
    }

    public static float getMastery() {
        return mastery;
    }

    /** How solid the sense still is, 0 when gone. The renderer scales all it draws by this. */
    public static float getFade() {
        return fade;
    }

    /** The creatures being felt out and how far each has resolved, for the renderer. */
    public static Map<LivingEntity, Float> sensed() {
        return sensed;
    }

    public static List<ClientboundSensingPacket.Marker> markers() {
        // While the sense is up the server is the authority; once it is let go the last picture is
        // held on to so the markers dissolve with the creatures instead of vanishing on their own.
        return active ? markers : lastMarkers;
    }

    /**
     * Refreshes the picture from the caster's synced sense, once per client tick.
     *
     * <p>While the sense is up the set of creatures is rebuilt every tick, so a creature that steps
     * out of reach simply drops out of it. Once the sense is dropped the set is left frozen and the
     * fade is run down instead, and only when it reaches nothing is everything cleared for good.</p>
     */
    public static void tick(Minecraft mc) {
        Player self = mc.player;
        if (self == null || mc.level == null) {
            clear();
            return;
        }
        INinjaData data = self.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        radius = data == null ? 0f : data.getSensingRadius();
        // The Byakugan is a sense of its own: it does not go through the chakra sense's mastery or
        // its staged resolution, it simply shows every body in its bubble as itself. While it is
        // open it takes over the picture, and the shared renderer draws it.
        boolean byakugan = data != null && ByakuganAbility.isActive(data);
        if (byakugan) {
            radius = Math.max(radius, ByakuganAbility.visionRadius(data));
        }
        active = radius > 0f;
        if (data != null) {
            mastery = Mastery.level(data, ChakraSensingAbility.ID);
        }
        if (!active) {
            fade -= 1f / FADE_OUT_TICKS;
            if (fade <= 0f) {
                clear();
            }
            return;
        }

        fade = 1f;
        double radiusSqr = (double) radius * radius;
        Map<LivingEntity, Float> next = new IdentityHashMap<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == self || !living.isAlive()) {
                continue;
            }
            if (living.distanceToSqr(self) > radiusSqr) {
                continue;
            }
            float intensity = byakugan ? 1f : intensityOf(living);
            if (intensity > 0f) {
                next.put(living, intensity);
            }
        }
        sensed = next;
    }

    /** How far a creature of its kind has resolved at the caster's mastery. */
    private static float intensityOf(LivingEntity living) {
        if (living instanceof Player) {
            return ChakraSensingAbility.playerIntensity(mastery);
        }
        if (living instanceof Enemy) {
            return ChakraSensingAbility.hostileIntensity(mastery);
        }
        return ChakraSensingAbility.animalIntensity(mastery);
    }

    private static void clear() {
        sensed = new IdentityHashMap<>();
        markers = List.of();
        lastMarkers = List.of();
        active = false;
        radius = 0f;
        fade = 0f;
    }
}
