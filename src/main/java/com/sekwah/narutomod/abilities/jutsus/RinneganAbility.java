package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The Rinnegan: the eye of the sage of six paths, opened as a stance rather than cast as a moment.
 *
 * <p>What it gives is capacity. While the eye is open the caster holds more chakra and more nature
 * energy, which is what makes the powers that sit behind it - attraction and repulsion, and the
 * Chibaku Tensei built on top of them - affordable at all. The bonus lives in its own fields on the
 * ninja data (see {@code INinjaData#getEyeChakraBonus()}) so that closing the eye takes it away
 * without touching the armour or the meditation clones.</p>
 *
 * <p>Like every stance it is paid for by the tick, not once. Nothing of it is drawn on the client:
 * the eye is state, and the only visual is the ring of chakra a server sends out now and then. The
 * shared rules - cost, one eye at a time, clearing the bonus - live in
 * {@link AbstractDojutsuAbility}; this class only says what the eye is worth.</p>
 */
public class RinneganAbility extends AbstractDojutsuAbility {

    /**
     * How far a cast of this technique carries as a game event. Opening an eye is quiet work.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V C - free, and not a prefix of any technique that builds on the eye. */
    public static final long COMBO = 2332L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinnegan");

    /** Chakra burnt for every tick the eye stays open. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    /** Extra chakra and nature energy the open eye adds to the caster's pools. */
    private static final float CHAKRA_BONUS = 60.0f;
    private static final float SENIN_BONUS = 30.0f;

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
        return NarutoConfig.rinneganEnabled;
    }

    @Override
    protected String disabledKey() {
        return "jutsu.fail.rinnegan.disabled";
    }

    @Override
    protected float chakraPerTick() {
        return CHAKRA_PER_TICK;
    }

    @Override
    protected void applyEyeBonus(INinjaData ninjaData) {
        ninjaData.setEyeChakraBonus(CHAKRA_BONUS);
        ninjaData.setEyeSeninBonus(SENIN_BONUS);
    }

    @Override
    protected void aura(ServerLevel level, Player player, INinjaData ninjaData) {
        if (player.tickCount % 4 != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.WITCH, player.getX(),
                player.getY() + player.getBbHeight() * 0.85d, player.getZ(),
                4, 0.35d, 0.15d, 0.35d, 0.01d);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(),
                player.getY() + player.getBbHeight() * 0.85d, player.getZ(),
                2, 0.25d, 0.1d, 0.25d, 0.02d);
    }

    /** @return whether the caster has the Rinnegan open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return isEyeActive(ninjaData, ID);
    }

    /**
     * Takes the capacity the eye was handing out back off the caster. Kept as a named entry point
     * because everything that ends an eye goes through the shared helper, and this is what the
     * Rinnegan's own callers expect to find.
     */
    public static void clearBonus(INinjaData ninjaData) {
        clearEyeBonus(ninjaData);
    }
}
