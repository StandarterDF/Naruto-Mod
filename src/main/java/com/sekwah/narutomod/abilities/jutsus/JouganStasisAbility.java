package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Jougan Stasis: the eye sees the seams in space, and for a few seconds it holds the things falling
 * through them still.
 *
 * <p>What it catches is everything already in the air and already burning: thrown weapons, arrows,
 * lit charges, blocks coming down out of a technique. None of it is destroyed, it is simply stopped
 * mid-flight inside the caster's reach, which turns an incoming volley into a picture of one. The
 * caster then has the whole of the frozen moment to walk out from under it, or to be somewhere else
 * when it starts again.</p>
 *
 * <p>The hold is written onto the stopped body as an expiry, exactly as the mod's other timed states
 * are, so a body that is unloaded and loaded again still knows when it is due to move. Restoring
 * motion is the business of whatever watches that tag; this class only stops and marks.</p>
 */
public class JouganStasisAbility extends Ability implements Ability.Cooldown {

    /** Freezing a fight is not quiet. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B V V V C C. */
    public static final long COMBO = 322211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "jougan_stasis");

    /** The bubble the eye holds still, measured from the caster. */
    private static final double RANGE = 12.0d;

    private static final float COST = 45.0f;
    private static final int COOLDOWN = 500;

    /** How long anything caught stays held, in ticks. */
    public static final int STASIS_TICKS = 80;

    /**
     * The expiry the restoring code reads. Public because the hold outlives this cast: keeping the
     * bodies still and letting them go again is done every tick by the code that watches this tag.
     */
    public static final String STASIS_TAG = "narutomod:stasis_until";

    /**
     * Set when the freeze had to switch gravity off, so the restore can switch it back on. A body
     * that never had gravity is left alone.
     */
    public static final String GRAVITY_RESTORE_TAG = "narutomod:stasis_restore_gravity";

    /**
     * A lit charge keeps burning its fuse while it hangs there, so it is given at least a full hold
     * worth of fuse: it must not go off in the middle of the moment it is meant to be frozen in.
     */
    private static final int MIN_FUSE = STASIS_TICKS;

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
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || caught(level, player).isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan_stasis.nothing",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long until = level.getGameTime() + STASIS_TICKS;

        for (Entity entity : caught(level, player)) {
            freeze(entity, until);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5d, entity.getZ(),
                    6, 0.2d, 0.2d, 0.2d, 0.0d);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    /** @return everything inside the bubble whose motion the eye can take away. */
    private static List<Entity> caught(ServerLevel level, Player caster) {
        return level.getEntitiesOfClass(Entity.class, caster.getBoundingBox().inflate(RANGE),
                entity -> entity.isAlive()
                        && (entity instanceof Projectile
                        || entity instanceof PrimedTnt
                        || entity instanceof FallingBlockEntity));
    }

    /**
     * Stops one body. A charge is given its fuse back instead of being held with gravity, because a
     * frozen charge is still a charge and the whole point is that it does not go off yet.
     */
    private static void freeze(Entity entity, long until) {
        entity.getPersistentData().putLong(STASIS_TAG, until);
        entity.setDeltaMovement(Vec3.ZERO);
        // The stop itself has to reach the clients or they keep drawing it halfway across the field.
        entity.hurtMarked = true;

        if (entity instanceof PrimedTnt tnt) {
            tnt.setFuse(Math.max(tnt.getFuse(), MIN_FUSE));
            return;
        }
        if (!entity.isNoGravity()) {
            entity.getPersistentData().putBoolean(GRAVITY_RESTORE_TAG, true);
        }
        entity.setNoGravity(true);
    }
}
