package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The Byakugan: the all-seeing eye of the Hyuga, worn as a stance.
 *
 * <p>It is the eye of the circle. Almost everything the caster could want to know is already in the
 * world around them - a mob behind a wall, a player in a hollow under the floor, a hunt gathering at
 * their back - and the Byakugan simply stops pretending those things are hidden. The picture itself
 * is drawn on the client by the same renderer the chakra sense uses: while the eye is open every
 * living body within {@link #VISION_RADIUS} blocks is shown as its own outline through the terrain,
 * which is exactly the "circular vision" the eye is famous for.</p>
 *
 * <p>It hands out no capacity and no damage. What it buys is information, and information has a
 * price like anything else: a cheap trickle of chakra for as long as the eye is open.</p>
 *
 * <p>Its close-quarters half is {@link RotationAbility}.</p>
 */
public class ByakuganAbility extends AbstractDojutsuAbility {

    /** An eye that only watches is quiet. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V - free, and one key away from Rotation. */
    public static final long COMBO = 2322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "byakugan");

    /**
     * How far the circular vision reaches, in blocks. Deliberately short: the eye shows everything
     * in that bubble through walls, so a large number would turn every fight into a map.
     */
    public static final float VISION_RADIUS = 16.0f;

    /** Chakra burnt for every tick the eye stays open. */
    private static final float CHAKRA_PER_TICK = 0.4f;

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    protected ResourceLocation eyeId() {
        return ID;
    }

    @Override
    protected boolean enabled() {
        return NarutoConfig.byakuganEnabled;
    }

    @Override
    protected String disabledKey() {
        return "jutsu.fail.byakugan.disabled";
    }

    @Override
    protected float chakraPerTick() {
        return CHAKRA_PER_TICK;
    }

    @Override
    protected void aura(ServerLevel level, Player player, INinjaData ninjaData) {
        // A pale ring of chakra around the head, thin enough that the vision itself stays the
        // noticeable part of the eye.
        if (player.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + player.getBbHeight() * 0.92d, player.getZ(),
                    2, 0.2d, 0.05d, 0.2d, 0.005d);
        }
    }

    /** @return whether the caster has the Byakugan open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return isEyeActive(ninjaData, ID);
    }
}
