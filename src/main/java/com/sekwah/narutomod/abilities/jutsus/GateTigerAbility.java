package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Daytime Tiger: a fistful of air punched into a body that is standing too close.
 *
 * <p>The fourth gate makes the arm fast enough that the air in front of it cannot get out of the
 * way. The punch closes a fist inside a bubble of compressed air and drives it into the body under
 * the caster's gaze, and what arrives is not a blow but a blast: the aimed body takes it full in the
 * chest, everything packed tighter around that point takes half of it, and all of them are thrown
 * back hard. The caster is not harmed by their own shockwave.</p>
 *
 * <p>It is a single strike, not a wave: the caster has to be looking at the body that will wear it,
 * within eight paces, and the air only reaches four paces from the point of impact.</p>
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

    /** How far the gaze reaches, how wide the impact is, what it costs and how long the fist needs afterwards. */
    private static final double RANGE = 8.0d;
    private static final double RADIUS = 4.0d;
    private static final float COST = 35.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 300;

    /** What the impact does to a body: the damage and the throw away from the caster. */
    private static final float DAMAGE = 30.0f;
    private static final double KNOCKBACK = 2.6d;

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity aimed) || !canStrike(player, aimed)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gate_tiger.notarget")
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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity aimed) || !canStrike(player, aimed)) {
            return;
        }
        // The air is packed into the chest of the body that was aimed at, not into the caster's
        // own footing: this strike lands somewhere, and that somewhere is what gets thrown back.
        Vec3 centre = aimed.position().add(0.0d, aimed.getBbHeight() * 0.5d, 0.0d);

        float scale = EightGatesAbility.techniqueScale(ninjaData);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                aimed.getBoundingBox().inflate(RADIUS), target -> canStrike(player, target));
        for (LivingEntity target : caught) {
            // The box is square; the impact is not, so the corner of the box is not inside it.
            if (target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d)
                    .distanceToSqr(centre) > RADIUS * RADIUS) {
                continue;
            }
            blast(level, player, target, scale, target == aimed);
        }

        // The impact is drawn by hand rather than detonated: a real explosion would break the world
        // and would decide its own damage, and this one has to leave both alone.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                40, RADIUS * 0.5d, RADIUS * 0.4d, RADIUS * 0.5d, 0.08d);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    /** One body in the impact: the aimed body takes it whole, the ones packed against it take half,
     *  and every one of them is thrown away from the caster. */
    private static void blast(ServerLevel level, Player caster, LivingEntity target, float scale,
                              boolean primary) {
        float damage = primary ? DAMAGE * scale : DAMAGE * scale * 0.5f;
        target.hurt(caster.damageSources().playerAttack(caster), damage);
        target.knockback(KNOCKBACK, target.getX() - caster.getX(), target.getZ() - caster.getZ());
        target.hurtMarked = true;
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
