package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Kicking off a wall in mid-air, the way a taijutsu body refuses to come down where it was sent.
 *
 * <p>Three of these are held at a time, and they only come back after the caster has stood still on
 * solid ground long enough for their feet to remember it. That is deliberate: the move is worth
 * nothing from the floor, so the charges are the ceiling on how long a fight can be kept in the air,
 * and landing to recharge is the price of the next exchange.</p>
 *
 * <p>The kick itself is short and hard - away from the face of the stone, with a lift - and it
 * braces the body for the moment it takes to clear the wall, so the caster is not shot out of the
 * air on the way.</p>
 */
public class WallBounceAbility extends Ability implements Ability.Cooldown {

    /** The push off the stone is heard nearby. */
    @Override
    public int noiseRadius() {
        return 6;
    }

    /** B C B C A B - the taijutsu branch's rebound seal. */
    public static final long COMBO = 132132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "wall_bounce");

    /** Charges held at a time, and how long on the ground it takes to get them all back. */
    public static final String CHARGES_TAG = "narutomod:wall_bounce_charges";
    public static final String RESET_TAG = "narutomod:wall_bounce_reset";
    private static final int MAX_CHARGES = 3;
    private static final int RESET_TICKS = 20;

    /** As far as the caster can reach a wall with a foot. */
    private static final double RANGE = 4.0d;

    /** Stamina paid, and the wait before the same wall can be kicked again. */
    private static final float COST = 15.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 80;

    /** Speed away from the face, and the lift added on top of it. */
    private static final double KICK = 0.7d;
    private static final double KICK_LIFT = 0.45d;

    /** A moment of bracing: long enough to clear the wall, short enough to be a kick and not a shield. */
    private static final int GUARD_TICKS = 6;
    private static final int GUARD_AMPLIFIER = 4;

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

    /**
     * Refills the charges first, then asks whether there is anything to kick off. A caster on the
     * ground has nothing to bounce from, and a caster with no wall within reach has nothing to push.
     */
    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        CompoundTag data = player.getPersistentData();
        int charges = refillCharges(player, data, player.level().getGameTime());

        if (player.onGround()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.wall_bounce.airborne",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Targeting.lookedAtBlock(player, RANGE) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.wall_bounce.nowall",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (charges <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughcharges",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        data.putInt(CHARGES_TAG, charges - 1);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult wall = Targeting.lookedAtBlock(player, RANGE);
        if (wall == null) {
            // The wall was there when the cost was paid; if it is gone now there is nothing to kick.
            return;
        }
        Vec3 away = Vec3.atLowerCornerOf(wall.getDirection().getNormal());
        player.setDeltaMovement(away.x * KICK, KICK_LIFT, away.z * KICK);
        // A kick off a wall is not a fall from it.
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, GUARD_TICKS,
                GUARD_AMPLIFIER, false, false));

        level.sendParticles(ParticleTypes.CLOUD, wall.getLocation().x, wall.getLocation().y,
                wall.getLocation().z, 10, 0.15d, 0.15d, 0.15d, 0.02d);
        level.playSound(null, wall.getLocation().x, wall.getLocation().y, wall.getLocation().z,
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    /**
     * Counts the grounded ticks and gives the charges back once the count reaches
     * {@link #RESET_TICKS}. Leaving the ground before then wipes the count, so a caster hopping from
     * wall to wall never refills in the air.
     */
    private static int refillCharges(Player player, CompoundTag data, long now) {
        int charges = data.contains(CHARGES_TAG) ? data.getInt(CHARGES_TAG) : MAX_CHARGES;
        if (!player.onGround()) {
            data.remove(RESET_TAG);
            return charges;
        }
        long resetAt = data.getLong(RESET_TAG);
        if (resetAt == 0L) {
            data.putLong(RESET_TAG, now + RESET_TICKS);
            return charges;
        }
        if (now >= resetAt) {
            data.putInt(CHARGES_TAG, MAX_CHARGES);
            data.remove(RESET_TAG);
            return MAX_CHARGES;
        }
        return charges;
    }
}
