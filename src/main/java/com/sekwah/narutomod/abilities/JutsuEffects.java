package com.sekwah.narutomod.abilities;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers for the particle effects of techniques.
 */
public final class JutsuEffects {

    private JutsuEffects() {
    }

    /**
     * Where a technique cast from the caster's hand should put its effect: at their right hand,
     * worked out from the body rather than from the gaze.
     *
     * <p>Particles are spawned on the server, which has no idea where the arm has been animated to,
     * so the hand is approximated: out to the caster's right of the direction they are facing, a
     * little in front of the chest and at shoulder height. Anchoring an effect on the look vector
     * instead (the obvious first guess) puts it in the middle of the screen, because the crosshair is
     * where the eyes point, not where the hand is.</p>
     */
    public static Vec3 handPosition(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0d, look.z);
        flatLook = flatLook.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flatLook.normalize();
        // A quarter turn clockwise from the facing direction is the caster's right hand side.
        Vec3 right = new Vec3(-flatLook.z, 0.0d, flatLook.x);
        return player.position()
                .add(flatLook.scale(0.30d))
                .add(right.scale(0.34d))
                .add(0.0d, 1.15d, 0.0d);
    }
}
