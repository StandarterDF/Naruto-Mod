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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Seize and throw: a body taken by the collar and sent away from the fight.
 *
 * <p>Taijutsu is not only what a fist does to a body; the ground a body lands on does half the work.
 * The caster takes hold of whatever is at arm's length, drives a fist into it so it comes loose, and
 * throws it four paces down the line they are looking - and the landing is the second blow, arriving
 * ten ticks later, when the target has already stopped expecting it.</p>
 *
 * <p>What is left behind is a body that has to get its feet back under it: the throw comes with a
 * slow, long enough for the caster to be somewhere else before the target has turned around.</p>
 */
public class GrabThrowAbility extends Ability implements Ability.Cooldown {

    /** A body thrown into a wall is heard across the street. */
    @Override
    public int noiseRadius() {
        return 10;
    }

    /** 1 3 2 3 1 3 - the kenjutsu seals and the two keys of the throw. */
    public static final long COMBO = 132313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "grab_throw");

    /** Arm's length: beyond this the caster cannot take hold of anything. */
    private static final double RANGE = 3.0d;

    /** What the throw costs, and how long before the arms are free again. */
    private static final float COST = 20.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 200;

    /** The grip: what the fist does before the throw. */
    private static final float GRIP_DAMAGE = 5.0f;

    /** The throw itself: four paces down the look, with enough lift to clear the ground. */
    private static final double THROW_SPEED = 0.8d;
    private static final double THROW_LIFT = 0.35d;
    private static final int THROW_SLOW_TICKS = 60;

    /** The landing, and how long it takes to arrive. */
    private static final float LANDING_DAMAGE = 3.0f;
    private static final int LANDING_DELAY = 10;

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canGrab(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.grab_throw.notarget",
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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canGrab(player, target)) {
            return;
        }

        target.hurt(player.damageSources().playerAttack(player), GRIP_DAMAGE);
        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                12, 0.25d, 0.3d, 0.25d, 0.1d);
        if (!target.isAlive()) {
            return;
        }

        Vec3 look = player.getViewVector(1.0f);
        target.setDeltaMovement(look.x * THROW_SPEED, THROW_LIFT, look.z * THROW_SPEED);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, THROW_SLOW_TICKS, 0, false, true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);

        MinecraftServer server = level.getServer();
        if (server != null) {
            // The landing is a second blow of its own, taken ten ticks later: it belongs to the
            // throw rather than to the moment the target left the caster's hands.
            server.tell(new TickTask(server.getTickCount() + LANDING_DELAY,
                    () -> land(level, player, target)));
        }
    }

    /** What the ground does to a thrown body when it arrives. */
    private static void land(ServerLevel level, Player caster, LivingEntity target) {
        if (target.isRemoved() || !target.isAlive() || target.level() != level) {
            return;
        }
        // The fall is the technique's, not the world's: the landing is counted once here rather
        // than also as fall damage on top of it.
        target.fallDistance = 0.0f;
        target.invulnerableTime = 0;
        target.hurt(caster.damageSources().playerAttack(caster), LANDING_DAMAGE);
        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                target.getY() + 0.2d, target.getZ(),
                10, 0.35d, 0.15d, 0.35d, 0.05d);
    }

    /** Anything alive can be taken hold of except the caster, their allies and the untouchable. */
    private static boolean canGrab(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
