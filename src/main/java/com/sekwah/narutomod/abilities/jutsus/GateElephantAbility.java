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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Evening Elephant: the last thing the gates can be asked for without dying.
 *
 * <p>Six gates open, and the arm can move so fast that the air ahead of it is compressed into a
 * single step of a stair. The fist lands and the shockwave follows it outward for eight paces,
 * lifting everything it touches off the ground and throwing it back. It is not aimed: a body inside
 * the ring is a body inside the ring.</p>
 *
 * <p>The price is the caster's own body. The technique is paid for out of the flesh that made it -
 * the caster is left standing on two points of health whatever they walked in with - because a
 * technique that spends everything short of the eighth gate has to spend something real. It breaks
 * no blocks: the shockwave is aimed at bodies, and the ground is only where they were standing.</p>
 */
public class GateElephantAbility extends Ability implements Ability.Cooldown {

    /** The last of the three is heard across a valley. */
    @Override
    public int noiseRadius() {
        return 40;
    }

    /** 2 3 3 3 3 3 - the gates' seal, closed at the far end. */
    public static final long COMBO = 1331L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gate_elephant");

    /** How many of the inner gates have to be open before the arm can carry this much force. */
    private static final int GATES_REQUIRED = 6;

    /** How far the shockwave reaches, what it costs and how long the body needs to be worth casting again. */
    private static final double RADIUS = 8.0d;
    private static final float COST = 50.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 500;

    /** What the shockwave does: the damage, the throw up and back, and the health the caster keeps. */
    private static final float DAMAGE = 18.0f;
    private static final double LIFT = 0.6d;
    private static final double PUSH = 1.5d;
    private static final float HEALTH_LEFT = 2.0f;

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
            player.displayClientMessage(Component.translatable("jutsu.fail.gate_elephant.gates",
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
            if (target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d)
                    .distanceToSqr(centre) > RADIUS * RADIUS) {
                continue;
            }
            shockwave(level, player, target);
        }

        // Drawn rather than detonated, for the same reason as the Daytime Tiger: the ground stays
        // where it is, and the numbers stay exactly the ones written above.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                60, RADIUS * 0.5d, RADIUS * 0.35d, RADIUS * 0.5d, 0.1d);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, 0.7f);

        // The body is spent by the technique, not wounded by anything the target did: nothing may
        // soak this up, which is why it goes straight onto the health bar.
        player.setHealth(HEALTH_LEFT);
    }

    /** One body in the shockwave: struck, thrown up and back. */
    private static void shockwave(ServerLevel level, Player caster, LivingEntity target) {
        target.hurt(caster.damageSources().playerAttack(caster), DAMAGE);
        target.knockback(PUSH, target.getX() - caster.getX(), target.getZ() - caster.getZ());
        // The lift is set after the knockback so the body leaves the ground whatever the shove did
        // to its height; a shockwave that only rolled bodies along the floor would not be one.
        Vec3 momentum = target.getDeltaMovement();
        target.setDeltaMovement(momentum.x, LIFT, momentum.z);
        target.hurtMarked = true;

        level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                10, 0.3d, 0.4d, 0.3d, 0.06d);
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
