package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.events.GenjutsuEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Genjutsu: the illusion that turns a hostile mob into a friend, or at least into somebody else's
 * problem.
 *
 * <p>It is cast at whatever the ninja is looking at, within a short range, and it takes hold of the
 * mob's senses for half a minute. For that half minute the victim cannot see players at all - it will
 * not acquire them as targets and any target it already has on one is dropped - and it is instead
 * pointed at the nearest other mob, which it attacks with whatever it normally attacks with. The
 * actual behaviour lives in {@link GenjutsuEvents}; this class is only the cast.</p>
 *
 * <p>It is deliberately not a combat technique on its own: a genjutsu ninja does not kill with it,
 * they make a fight break out somewhere else and walk away from it.</p>
 */
public class GenjutsuJutsuAbility extends Ability implements Ability.Cooldown {

    /** C B C */
    public static final long COMBO = 131L;

    /** Chakra for one cast. */
    public static final float CHAKRA_COST = 35.0f;

    /** How far the ninja can reach with the technique. */
    public static final double RANGE = 24.0d;

    /** How long the illusion lasts, in ticks. */
    public static final int DURATION = 20 * 30;

    private static final int COOLDOWN = 20 * 10;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        Mob target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (GenjutsuEvents.isUnderGenjutsu(target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.active",
                    target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ninjaData.getChakra() < CHAKRA_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_COST, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        Mob target = findTarget(player);
        if (target == null) {
            // The check in handleCost already passed, so this should not happen; refund nothing, the
            // cast simply fizzles rather than charging for nothing.
            return;
        }
        GenjutsuEvents.apply(player, target, DURATION);

        player.displayClientMessage(Component.translatable("jutsu.genjutsu.cast",
                target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                    target.getY() + target.getBbHeight(), target.getZ(),
                    30, 0.5d, 0.4d, 0.5d, 0.4d);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ILLUSIONER_CAST_SPELL, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
        }
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /** @return the mob the ninja is looking at, or null when they are looking at anything else. */
    public static Mob findTarget(Player player) {
        HitResult hit = player.pick(RANGE, 0.0f, false);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Mob mob) {
            return mob.isAlive() ? mob : null;
        }
        return null;
    }
}
