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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Morning Peacock: the first of the gates' finishing blows.
 *
 * <p>With three gates open the body is already faster than it can be followed, and speed alone
 * becomes a weapon. The caster crosses the gap and puts a palm into the same body eight times before
 * the first blow has finished landing, each one wrapped in flame from the sheer heat of the movement.
 * Nothing about the technique is clever; it is simply more punches than a body can be hit with in one
 * breath.</p>
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
    public static final long COMBO = 233331L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gate_peacock");

    /** How many of the inner gates have to be open before the arms can move this fast. */
    private static final int GATES_REQUIRED = 3;

    /** How far down the gaze the flurry can be delivered, and what it costs. */
    private static final double RANGE = 6.0d;
    private static final float COST = 40.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 400;

    /** Eight palms, each worth three points, two of which are the flame rather than the hand. */
    private static final int HITS = 8;
    private static final float DAMAGE_PER_HIT = 3.0f;
    private static final float MAGIC_PER_HIT = 2.0f;

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canStrike(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gate_peacock.notarget",
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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canStrike(player, target)) {
            return;
        }

        float physical = DAMAGE_PER_HIT - MAGIC_PER_HIT;
        for (int hit = 0; hit < HITS && target.isAlive(); hit++) {
            // Every palm has to land: the flurry is one long opening rather than eight separate
            // blows, so the invulnerability window of the one before is cleared rather than waited
            // out. Without that only the first of the eight would count.
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), physical);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().magic(), MAGIC_PER_HIT);

            Vec3 at = target.position().add(0.0d, target.getBbHeight() * 0.6d, 0.0d);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z,
                    4, 0.25d, 0.25d, 0.25d, 0.02d);
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
