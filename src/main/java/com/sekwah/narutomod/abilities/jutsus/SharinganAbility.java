package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.GenjutsuEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The Sharingan: the copy wheel, worn as a stance.
 *
 * <p>It is the eye of reading a fight. While it is open the caster can weigh up whoever they are
 * looking at - their health, their armour and every effect on them are shown in the corner of the
 * screen - and an illusion cannot hide from it: a mob held in a genjutsu is lit up with chakra, and
 * the blindness an illusion leans on is shaken off the caster's own eyes every tick. What it does
 * not do is hand out capacity, which is the Rinnegan's trick; the Sharingan is cheap to hold and
 * the two eyes are not interchangeable.</p>
 *
 * <p>Its offensive half is {@link AmaterasuAbility}, which only works through an open eye.</p>
 */
public class SharinganAbility extends AbstractDojutsuAbility {

    /** The eye is pure perception, so opening it makes almost no noise. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B - free, and one key away from Amaterasu. */
    public static final long COMBO = 31L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "sharingan");

    /** Chakra burnt for every tick the eye stays open. Cheaper than the Rinnegan: it gives less. */
    private static final float CHAKRA_PER_TICK = 0.35f;

    /** How far the eye picks out a mob that is already caught in an illusion. */
    private static final double ILLUSION_RANGE = 20.0d;

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
        return NarutoConfig.sharinganEnabled;
    }

    @Override
    protected String disabledKey() {
        return "jutsu.fail.sharingan.disabled";
    }

    @Override
    protected float chakraPerTick() {
        return CHAKRA_PER_TICK;
    }

    @Override
    protected void aura(ServerLevel level, Player player, INinjaData ninjaData) {
        // The red chakra of the eye itself: sparse, so it reads as an eye and not as a torch.
        if (player.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(),
                    player.getY() + player.getBbHeight() * 0.9d, player.getZ(),
                    2, 0.18d, 0.08d, 0.18d, 0.01d);
        }

        // An illusion is visible to this eye: every mob the caster can see that is held in one is
        // marked with a slow drift of chakra, so the caster knows which of the bodies in front of
        // them is fighting something that is not there.
        if (player.tickCount % 10 == 0) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(ILLUSION_RANGE))) {
                if (!GenjutsuEvents.isUnderGenjutsu(mob) || !player.hasLineOfSight(mob)) {
                    continue;
                }
                level.sendParticles(ParticleTypes.ENCHANT, mob.getX(),
                        mob.getY() + mob.getBbHeight() + 0.25d, mob.getZ(),
                        4, 0.25d, 0.15d, 0.25d, 0.02d);
            }
        }

        // The eye sees through the blind an illusion puts on the mind. Nothing in the mod can put a
        // player under genjutsu today, but the counter is part of what the eye is, and it costs one
        // effect lookup to keep it honest.
        if (player.hasEffect(MobEffects.DARKNESS)) {
            player.removeEffect(MobEffects.DARKNESS);
        }
    }

    /** @return whether the caster has the Sharingan open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return isEyeActive(ninjaData, ID);
    }
}
