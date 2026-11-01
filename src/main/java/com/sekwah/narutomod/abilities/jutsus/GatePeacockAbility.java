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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Morning Peacock: the first of the gates' finishing blows.
 *
 * <p>With three gates open the body is already faster than it can be followed, and speed alone
 * becomes a weapon. The caster crosses the gap and puts a palm into every body on their line eight
 * times before the first blow has finished landing, each one wrapped in flame from the sheer heat of
 * the movement. Nothing about the technique is clever; it is simply more punches than a body can be
 * hit with in one breath.</p>
 *
 * <p>The blows run straight through whatever stands in front of the caster, up to twenty-four blocks
 * away: everything on that line is caught at once. A wall still stops the technique — a body behind
 * solid cover is never reached.</p>
 *
 * <p>It costs more than stamina. Forcing the arms to move that fast leaves the caster weak for five
 * seconds afterwards, so the flurry is an opening or a finisher, never something to trade blows
 * with.</p>
 */
public class GatePeacockAbility extends Ability implements Ability.Cooldown {

    /** Eight palms landing at once carry a long way. */
    @Override
    public int noiseRadius() {
        return 20;
    }

    /** 2 3 3 3 3 1 - the gates' seal with the peacock's tail. */
    public static final long COMBO = 1332L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gate_peacock");

    /** How many of the inner gates have to be open before the arms can move this fast. */
    private static final int GATES_REQUIRED = 3;

    /** How far down the line the flurry can be delivered, and what it costs. */
    private static final double RANGE = 24.0d;
    /** How close a body's centre has to pass to the line of the blows to be caught by them. */
    private static final double LINE_RADIUS = 1.0d;
    private static final float COST = 40.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 400;

    /** Eight palms: physical 2.0 + magic 2.5 per hit, scaled by mastery and open gates. */
    private static final int HITS = 8;
    private static final float PHYSICAL_PER_HIT = 2.0f;
    private static final float MAGIC_PER_HIT = 2.5f;

    /** How long the caster's arms are left shaking after the flurry. */
    private static final int WEAKNESS_TICKS = 100;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.gate_peacock.gates",
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

        float scale = EightGatesAbility.techniqueScale(ninjaData);
        float physical = PHYSICAL_PER_HIT * scale;
        float magic = MAGIC_PER_HIT * scale;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        AABB area = new AABB(eye, end).inflate(1.5d);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, area, t -> canStrike(player, t));
        for (LivingEntity target : caught) {
            Vec3 centre = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d);
            // Distance from the body's centre to the line.
            Vec3 toCentre = centre.subtract(eye);
            double along = toCentre.dot(look);
            if (along < 0.0d || along > RANGE) {
                continue;
            }
            Vec3 onLine = eye.add(look.scale(along));
            if (onLine.distanceToSqr(centre) > LINE_RADIUS * LINE_RADIUS) {
                continue;
            }
            // No punching through walls: a block between the eye and the body stops the line.
            BlockHitResult clip = level.clip(new ClipContext(eye, centre,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (clip.getType() == HitResult.Type.BLOCK
                    && clip.getLocation().distanceToSqr(eye) < centre.distanceToSqr(eye) - 0.25d) {
                continue;
            }

            for (int hit = 0; hit < HITS && target.isAlive(); hit++) {
                // Every palm has to land: the flurry is one long opening rather than eight separate
                // blows, so the invulnerability window of the one before is cleared rather than waited
                // out. Without that only the first of the eight would count.
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), physical);
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().magic(), magic);

                Vec3 at = target.position().add(0.0d, target.getBbHeight() * 0.6d, 0.0d);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z,
                        4, 0.25d, 0.25d, 0.25d, 0.02d);
            }
        }

        // The line the blows travelled down, drawn once so the reach of the technique is readable.
        for (int step = 1; step <= 24; step++) {
            Vec3 at = eye.add(look.scale(step));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            if (step % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }

        // The heat of the movement is paid for by the body that made it.
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, false, true));

        Vec3 chest = player.position().add(0.0d, player.getBbHeight() * 0.6d, 0.0d);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, chest.x, chest.y, chest.z,
                6, 0.4d, 0.4d, 0.4d, 0.0d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.6f);
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
