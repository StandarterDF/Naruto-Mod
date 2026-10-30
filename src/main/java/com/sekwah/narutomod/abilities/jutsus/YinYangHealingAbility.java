package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.SixPathsEsotericEvents;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Yin-Yang Release: the caster's own life held in their hands and poured into somebody else.
 *
 * <p>This is not medical ninjutsu. A medic repairs what is broken; the Six Paths simply declare that
 * a body is whole, and the body agrees - wounds close, poison and withering are peeled away, and
 * sight, balance and footing come back. The price is paid for as long as the seal is held, and it is
 * paid whether or not the ally still needs it, so the technique rewards a caster who knows when to
 * let go.</p>
 *
 * <p>The hands are held on a living ally under the crosshair, or on the caster's own chest while
 * sneaking. Nothing is healed through a wall, since the target is found with the same look ray every
 * other aimed technique uses, see {@link Targeting}.</p>
 */
public class YinYangHealingAbility extends Ability implements Ability.Channeled {

    /**
     * How far a cast of this technique carries as a game event. Quiet work: the seal does not shout.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C C C C C with the last key turned again - the seal that closes the Six Paths block. */
    public static final long COMBO = 3322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "yin_yang_heal");

    /** How far the seal reaches. */
    private static final double RANGE = 4.0d;

    /** Chakra to open the seal. */
    private static final float ENTRY_COST = 40.0f;

    /** Chakra burnt for every tick the seal is kept. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Health restored per pulse, and how many ticks apart the pulses are. */
    private static final float HEAL_AMOUNT = 4.0f;
    private static final int HEAL_INTERVAL = 10;

    /** The share of the pulse the caster takes for themselves while mending somebody else. */
    private static final float SELF_SHARE = 0.5f;

    /** The ailments the seal peels off the ally, every tick it is held. */
    private static final List<MobEffect> CURED = List.of(
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN);

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A tap is allowed: it grants a burst of regeneration and saturation. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SixPathsEsotericEvents.isSixPathsActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sixpaths.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (patient(player) == null) {
            // Only the first tick explains itself; a target that walks away mid seal ends the
            // channel quietly rather than filling the chat every tick.
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.yinyang.noally",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        float cost = this.masteryCost(ninjaData, chargeAmount < 0 ? ENTRY_COST : (chargeAmount == 0 ? ENTRY_COST : CHAKRA_PER_TICK));
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * The work is done tick by tick, so releasing the seal has nothing left to do. Mastery is paid
     * for the time the seal is actually held, see {@link #handleChannelling}.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive >= 0) {
            return;
        }
        // Tap: a burst of regeneration and saturation.
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 4, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 30, 255, false, true));
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity ally = patient(player);
        if (ally == null) {
            return;
        }

        for (MobEffect effect : CURED) {
            ally.removeEffect(effect);
        }
        if (ticksChanneled % HEAL_INTERVAL == 0) {
            ally.heal(HEAL_AMOUNT);
            // The seal runs through the caster as well: mending somebody else no longer leaves the
            // healer bleeding beside them.
            if (ally != player) {
                player.heal(HEAL_AMOUNT * SELF_SHARE);
            }
            level.playSound(null, ally.getX(), ally.getY(), ally.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 1.4f);
        }

        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        drawSeal(level, player, ally, ticksChanneled);

        // Visual field wall: a ring of particles around the caster, shown for the whole hold.
        if (ticksChanneled % 4 == 0) {
            double radius = 8.0d;
            for (int i = 0; i < 36; i++) {
                double angle = (Math.PI * 2.0d / 36.0d) * i;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 0.1d, z,
                        1, 0.05d, 0.05d, 0.05d, 0.0d);
                level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 1.0d, z,
                        1, 0.05d, 0.05d, 0.05d, 0.0d);
            }
        }

        // Held field: drain enemies, heal allies/creator, swap ailments. Scales with enemy count
        // and mastery: more enemies (and higher mastery) drain and heal harder.
        if (ticksChanneled % 20 == 0) {
            double radius = 8.0d;
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius));
            float mastery = this.masteryLevel(ninjaData);
            float perEnemy = 2.0f + 4.0f * mastery;
            float drained = 0.0f;
            int enemies = 0;
            for (LivingEntity e : nearby) {
                if (e == player || e.isSpectator() || !e.isAlive()) continue;
                if (Friends.isOwnSide(player, e)) {
                    for (MobEffect effect : CURED) {
                        e.removeEffect(effect);
                    }
                    e.heal(1.0f + 2.0f * mastery);
                } else {
                    e.hurt(player.damageSources().magic(), perEnemy);
                    drained += perEnemy;
                    enemies++;
                    for (MobEffect effect : CURED) {
                        e.addEffect(new MobEffectInstance(effect, 60, 0, false, true));
                    }
                }
            }
            // If no enemies, still heal moderately.
            if (enemies == 0) {
                player.heal(2.0f + 2.0f * mastery);
            } else {
                player.heal(drained * (0.25f + 0.25f * mastery));
            }
        }
    }

    /**
     * A held seal is mastered by holding it, not by opening it, so the credit is paid per active
     * tick and a bare activation records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Who the seal is meant for: the living ally under the crosshair, or the caster themselves when
     * there is nobody there or they are sneaking. The caster is always a valid patient, so the seal
     * can be turned on their own wounds without hunting for a target.
     */
    private static LivingEntity patient(Player caster) {
        if (caster.isShiftKeyDown()) {
            return caster;
        }
        if (Targeting.lookedAtEntity(caster, RANGE) instanceof LivingEntity aimed
                && aimed.isAlive() && !aimed.isSpectator() && Friends.isOwnSide(caster, aimed)) {
            return aimed;
        }
        return caster;
    }

    /**
     * The warm light between the hands, and a thread of it running to the body being mended so it is
     * plain who the seal is working on.
     */
    private static void drawSeal(ServerLevel level, Player caster, LivingEntity patient, int ticks) {
        if (ticks % 4 != 0) {
            return;
        }
        Vec3 seal = sealPosition(caster);
        level.sendParticles(ParticleTypes.HEART, seal.x, seal.y, seal.z, 1, 0.06d, 0.06d, 0.06d, 0.0d);
        level.sendParticles(ParticleTypes.END_ROD, seal.x, seal.y, seal.z, 2, 0.12d, 0.12d, 0.12d, 0.01d);

        if (patient == caster) {
            return;
        }
        Vec3 chest = patient.position().add(0.0d, patient.getBbHeight() * 0.6d, 0.0d);
        for (int i = 1; i <= 4; i++) {
            Vec3 at = seal.add(chest.subtract(seal).scale(i / 5.0d));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    /** Where the two hands meet: in front of the chest, centred rather than off to one side. */
    private static Vec3 sealPosition(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        flat = flat.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flat.normalize();
        return player.position().add(flat.scale(0.30d)).add(0.0d, 1.15d, 0.0d);
    }
}
