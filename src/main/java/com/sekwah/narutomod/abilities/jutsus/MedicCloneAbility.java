package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Medical clone: a copy that stands beside its caster and treats them instead of fighting.
 *
 * <p>Where a shadow clone is a second pair of hands, this one is a field medic - it keeps its hands
 * free, it never picks a target, and it spends itself closing the caster's wounds a little at a time.
 * That is the trade the technique is built on: chakra spent now, health returned slowly, and no help
 * at all if the caster is killed before the treatment finishes.</p>
 *
 * <p>The healing itself runs from the tick hook that watches {@link #MEDIC_UNTIL_KEY}, so a clone
 * keeps working even if the caster runs out of sight. The counters live in the clone's persistent
 * data: {@link #HEALED_KEY} is how much has already been given back, which is what stops a medic
 * from being an infinite source of health.</p>
 */
public class MedicCloneAbility extends Ability implements Ability.Cooldown {

    /** B A A C - the shadow clone seal with the medical hand on the end. */
    public static final long COMBO = 2113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clone_medic");

    /** Marks a clone as a medic, read by the tick hook that does the healing. */
    public static final String MEDIC_KEY = "narutomod:medic_clone";

    /** Game time at which the medic has used itself up and pops. */
    public static final String MEDIC_UNTIL_KEY = "narutomod:medic_until";

    /** Health already restored by this clone, in hit points; the treatment stops at eight. */
    public static final String HEALED_KEY = "narutomod:medic_healed";

    private static final float COST = 55.0f;
    private static final int COOLDOWN = 600;

    /**
     * The medic outlives the treatment it can give away on purpose: the hook pops it a little after
     * the last point of health, so the caster sees the clone run dry rather than blink out early.
     */
    private static final int DURATION = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Just outside the caster's shoulder, close enough to read as treating them. */
    private static final double SPAWN_RADIUS = 1.2d;

    /** A quiet technique: no shout, no flash, just a copy stepping out of the caster. */
    private static final int NOISE_RADIUS = 8;

    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        ShadowCloneEntity medic = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        medic.setOwner(player);
        // Hands stay free for the healing seal, so nothing is copied into them.
        medic.setTarget(null);
        // A medic is not a bodyguard: taking its target goals away is what keeps it from wandering
        // off into a fight the caster did not ask for.
        medic.targetSelector.removeAllGoals(goal -> true);

        medic.getPersistentData().putBoolean(MEDIC_KEY, true);
        medic.getPersistentData().putLong(MEDIC_UNTIL_KEY, level.getGameTime() + DURATION);
        medic.getPersistentData().putFloat(HEALED_KEY, 0.0f);

        position(medic, level, player);
        level.addFreshEntity(medic);

        level.sendParticles(ParticleTypes.HEART, medic.getX(),
                medic.getY() + medic.getBbHeight() * 0.75d, medic.getZ(),
                8, 0.3d, 0.4d, 0.3d, 0.02d);
        level.playSound(null, player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35f, 1.6f);
    }

    /** Stands the medic beside the caster, closing in if that spot is taken. */
    private static void position(ShadowCloneEntity medic, ServerLevel level, Player player) {
        double angle = Math.toRadians(player.getYRot());
        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() - Math.sin(angle) * radius;
            medic.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            if (level.noCollision(medic, medic.getBoundingBox())) {
                break;
            }
        }
    }
}
