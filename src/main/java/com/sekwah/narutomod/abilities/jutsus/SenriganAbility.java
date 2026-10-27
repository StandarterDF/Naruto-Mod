package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The Senrigan: the far eye, worn as a stance.
 *
 * <p>It gives the body nothing - no extra chakra, no armour, no aura worth writing home about - and
 * that is the point. What it opens is a viewpoint: with the eye held, the caster can send their
 * sight through any shadow clone of their own and look out of that clone's eyes for as long as the
 * stance lasts. The technique that actually moves the viewpoint is {@link SenriganSightAbility}.</p>
 *
 * <p>Because it is a viewpoint rather than a weapon, it is the cheap eye to hold: the price is paid
 * every tick, but a caster who is only watching does not need to spend anything else.</p>
 */
public class SenriganAbility extends AbstractDojutsuAbility {

    /** Pure sight, so the eye itself makes no noise. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V B B - the eye, and one key away from the sight technique. */
    public static final long COMBO = 323L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "senrigan");

    /** The cheapest eye: it hands out a viewpoint, not power. */
    private static final float CHAKRA_PER_TICK = 0.3f;

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
        return NarutoConfig.senriganEnabled;
    }

    @Override
    protected String disabledKey() {
        return "jutsu.fail.senrigan.disabled";
    }

    @Override
    protected float chakraPerTick() {
        return CHAKRA_PER_TICK;
    }

    @Override
    protected void aura(ServerLevel level, Player player, INinjaData ninjaData) {
        // A thin gold ring around the head, slow enough to read as attention rather than a buff.
        if (player.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + player.getBbHeight() * 0.95d, player.getZ(),
                    1, 0.16d, 0.06d, 0.16d, 0.005d);
        }
    }

    /** @return whether the caster has the Senrigan open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return isEyeActive(ninjaData, ID);
    }
}
