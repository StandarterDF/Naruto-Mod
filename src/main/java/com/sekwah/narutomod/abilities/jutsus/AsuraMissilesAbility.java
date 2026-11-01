package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.sekwah.narutomod.item.NarutoItems;

import java.util.Comparator;
import java.util.List;

/**
 * The Asura path: the caster's arm opens and three seekers leave it.
 *
 * <p>There is no projectile entity here. A missile is a promise that a body will be struck a
 * moment from now, so the technique hands the server three timed strikes and draws the flight
 * itself with end rods. That keeps the seeker out of the world's collision and damage pipelines:
 * it cannot be deflected, it cannot break a block, and it cannot be caught by anything that hunts
 * entities - all it can do is arrive.</p>
 *
 * <p>The three targets are the nearest creatures the caster is not on the side of, each struck a
 * quarter of a second after the one before it, so the volley reads as three separate arrivals
 * rather than a single burst.</p>
 */
public class AsuraMissilesAbility extends Ability implements Ability.Cooldown {

    /** Three seekers leaving an arm are heard clearly. */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** 2 3 3 2 3 3 - one key past the animal path. */
    public static final long COMBO = 1113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "asura_missiles");

    /** What the volley costs, and how long the arm needs before it can fire another. */
    private static final float COST = 55.0f;
    private static final int COOLDOWN = 440;

    /** How far the seekers may look for a body. */
    private static final double RANGE = 24.0d;

    /** One seeker per target, and never more than this many. */
    private static final int MAX_TARGETS = 3;

    /** What each arrival takes out of the body it finds. */
    private static final float DAMAGE = 5.0f;

    /** Ticks between the three arrivals: 5, 10 and 15 after the cast. */
    private static final int ARRIVAL_STAGGER = 5;

    /** How many motes are drawn along a seeker's flight. */
    private static final int TRAIL_POINTS = 10;

    /** Ticks before the caster's own regeneration resumes after paying for the volley. */
    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Rework: the ability now hands the caster three missiles to keep in the inventory. Each one
        // is thrown at a target by hand, so the caster chooses when and whom to strike.
        ItemStack missiles = new ItemStack(NarutoItems.ASURA_MISSILE.get(), 3);
        if (!player.getInventory().add(missiles)) {
            player.drop(missiles, false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                player.getY() + player.getBbHeight() * 0.7d, player.getZ(),
                16, 0.3d, 0.3d, 0.3d, 0.08d);
    }

    /**
     * The nearest living creatures the caster is not on the side of, closest first. Spectators and
     * creative players are skipped everywhere in the mod for the same reason: they are not part of
     * the fight.
     */
    private static List<LivingEntity> targets(Player caster) {
        return caster.level().getEntitiesOfClass(LivingEntity.class,
                        caster.getBoundingBox().inflate(RANGE), entity -> canStrike(caster, entity))
                .stream()
                .filter(entity -> entity.distanceToSqr(caster) <= RANGE * RANGE)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(caster)))
                .limit(MAX_TARGETS)
                .toList();
    }

    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !Friends.isOwnSide(caster, target);
    }

    /** Fires a single missile at the given target (used by the inventory missile item). */
    public static void fireOne(ServerLevel level, Player caster, LivingEntity target) {
        arrive(level, caster, target);
    }

    /** One seeker arriving: the trail is drawn, then the body is struck. */
    private static void arrive(ServerLevel level, Player caster, LivingEntity target) {
        if (caster.level() != level || target.level() != level || !target.isAlive()) {
            return;
        }
        drawTrail(level, caster, target);
        // Damage scales from 10 at no mastery to 30 at full mastery.
        float mastery = caster.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .map(data -> com.sekwah.narutomod.abilities.mastery.Mastery.level(data,
                        com.sekwah.narutomod.abilities.NarutoAbilities.ASURA_MISSILES.get()))
                .orElse(0.0f);
        float damage = 10.0f + 20.0f * mastery;
        target.hurt(level.damageSources().indirectMagic(caster, caster), damage);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                12, 0.25d, 0.25d, 0.25d, 0.02d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.7f, 1.7f);
    }

    /** A line of end rods from the caster's hand to the body the seeker is about to reach. */
    private static void drawTrail(ServerLevel level, Player caster, LivingEntity target) {
        Vec3 start = JutsuEffects.handPosition(caster);
        Vec3 end = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d);
        Vec3 span = end.subtract(start);
        for (int i = 1; i <= TRAIL_POINTS; i++) {
            Vec3 at = start.add(span.scale(i / (double) (TRAIL_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.01d, 0.01d, 0.01d, 0.0d);
        }
    }
}
