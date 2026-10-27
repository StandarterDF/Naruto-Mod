package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Clay Clone: a copy packed around a charge, spent as a bomb rather than as a fighter.
 *
 * <p>Clay is the one material a ninja can shape into their own face and still be glad to see come
 * apart. The copy walks in like any other and is popped like any other - one hit is enough - but the
 * body it leaves behind is not smoke. A moment after the chakra lets go the packed clay goes off,
 * hurting everything close and breaking nothing: the point is the bodies, not the ground.</p>
 *
 * <p>This class only raises the body and writes the marks below. Detonating it is the world hook's
 * job, because the blast has to happen after the copy is already gone and cannot be trusted to a
 * dying entity.</p>
 */
public class ClayCloneAbility extends Ability implements Ability.Cooldown {

    /** A charge being armed is a small, sharp click, not a shout. */
    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    /** B A B A B A - the clone seal with the clay sign on the end. */
    public static final long COMBO = 1313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clay_clone");

    /** Marks a clone as a clay clone, which the hook turns into a charge when it dies. */
    public static final String CLAY_KEY = "narutomod:clay_clone";

    /** Game time at which the clay has dried out for long enough and the copy pops on its own. */
    public static final String CLAY_UNTIL_KEY = "narutomod:clay_until";

    /**
     * What the blast covers when the clay goes off, and how long the fuse burns after the copy is
     * broken. Exported because the hook owns the explosion.
     */
    public static final float BLAST_RADIUS = 2.5f;
    public static final int FUSE_TICKS = 5;

    private static final float COST = 65.0f;
    private static final int COOLDOWN = 500;

    /**
     * How long the copy lasts, in ticks. It is the number written into the mark; a clay body is
     * meant to stand around long enough that the caster forgets where they left it.
     */
    private static final int DURATION = 600;

    /** Beside the caster, close enough that the clay reads as having been shaped on the spot. */
    private static final double SPAWN_RADIUS = 1.4d;

    private static final int CHAKRA_REGEN_DELAY = 20;

    private static final int NOISE_RADIUS = 20;

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

        // The bare constructor is what keeps the clay empty-handed: copying the caster's gear would
        // give a body that is about to explode a sword to swing in the meantime.
        ShadowCloneEntity clay = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        clay.setOwner(player);
        CompoundTag data = clay.getPersistentData();
        data.putBoolean(CLAY_KEY, true);
        data.putLong(CLAY_UNTIL_KEY, level.getGameTime() + DURATION);

        position(clay, level, player);
        if (!level.addFreshEntity(clay)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.clay_clone.failed",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        level.sendParticles(ParticleTypes.CLOUD, clay.getX(),
                clay.getY() + clay.getBbHeight() * 0.5d, clay.getZ(),
                30, 0.4d, 0.6d, 0.4d, 0.02d);
        // Quiet on purpose: the charge is armed, not announced.
        level.playSound(null, player, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.4f, 1.2f);
    }

    /** Stands the copy beside the caster, closing in if that spot is taken. */
    private static void position(ShadowCloneEntity clay, ServerLevel level, Player player) {
        double angle = Math.toRadians(player.getYRot());
        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() - Math.sin(angle) * radius;
            clay.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            if (level.noCollision(clay, clay.getBoundingBox())) {
                break;
            }
        }
    }
}
