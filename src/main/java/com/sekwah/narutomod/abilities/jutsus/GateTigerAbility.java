package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Daytime Tiger: a fistful of air punched into a body that is standing too close.
 *
 * <p>The fourth gate makes the arm fast enough that the air in front of it cannot get out of the
 * way. The punch closes a fist inside a bubble of compressed air and lets it go, and what arrives is
 * not a blow but a blast: everything within five paces is thrown back and left sluggish, with no
 * wound to show for it. The caster is not harmed by their own shockwave.</p>
 *
 * <p>It breaks nothing. The force is aimed at bodies, not at the world, and a technique that
 * rearranged the terrain every time it was used would be a technique nobody could use indoors.</p>
 */
public class GateTigerAbility extends Ability implements Ability.Cooldown {

    /** A blast of compressed air is heard a long way off. */
    @Override
    public int noiseRadius() {
        return 32;
    }

    /** 2 3 3 3 3 2 - the gates' seal with the tiger's claw. */
    public static final long COMBO = 1333L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gate_tiger");

    /** How many of the inner gates have to be open before the air can be punched. */
    private static final int GATES_REQUIRED = 4;

    /** How far the blast reaches, what it costs and how long the fist needs afterwards. */
    private static final double RADIUS = 5.0d;
    private static final float COST = 35.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 300;

    /** What the blast does to a body: the damage, the throw, and the weight left in the limbs. */
    private static final float DAMAGE = 12.0f;
    private static final double KNOCKBACK = 1.2d;
    private static final int SLOW_TICKS = 80;
    private static final int SLOW_AMPLIFIER = 1;

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
        if (ninjaData.getGateCount() < GATES_REQUIRED) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gate_tiger.gates",
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
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = player.position().add(0.0d, player.getBbHeight() * 0.5d, 0.0d);

        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS), target -> canStrike(player, target));
        for (LivingEntity target : caught) {
            // The box is square; the blast is not, so the corner of the box is not inside it.
            if (target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d)
                    .distanceToSqr(centre) > RADIUS * RADIUS) {
                continue;
            }
            blast(level, player, target);
        }

        // The blast is drawn by hand rather than detonated: a real explosion would break the world
        // and would decide its own damage, and this one has to leave both alone.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                40, RADIUS * 0.5d, RADIUS * 0.4d, RADIUS * 0.5d, 0.08d);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    /** One body in the blast: struck, thrown away from the caster and left heavy. */
    private static void blast(ServerLevel level, Player caster, LivingEntity target) {
        target.hurt(caster.damageSources().playerAttack(caster), DAMAGE);
        target.knockback(KNOCKBACK, target.getX() - caster.getX(), target.getZ() - caster.getZ());
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SLOW_TICKS, SLOW_AMPLIFIER, false, true));
        level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                8, 0.3d, 0.3d, 0.3d, 0.05d);
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
