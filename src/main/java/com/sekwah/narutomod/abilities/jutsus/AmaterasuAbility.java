package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.AmaterasuEvents;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import com.sekwah.narutomod.abilities.jutsus.BreathCone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Amaterasu: the black flame that is willed onto a target rather than thrown at it.
 *
 * <p>Cast through an open Sharingan, it fixes on the creature the caster is looking at and sets it
 * burning with a fire that is not fire. The flame does not care about water, rain or a fire
 * resistance potion, because it is not burning the body - it is a chakra fire that gnaws at it for
 * ten minutes and then goes out on its own. See {@link AmaterasuEvents} for the burn itself.</p>
 *
 * <p>The price is deliberately steep and the cooldown long: one cast is meant to be a decision, not
 * a rotation. The black flame is not a thrown fire but a bonfire's worth of chakra forced onto a
 * body, so it burns a large part of the bar at once. While channelling it heats only the furnace
 * the caster is looking at: that single furnace is stoked to ten times its normal speed for thirty
 * seconds, the same kind of heat the fire breath pours into a furnace. Like every power built on
 * an eye it refuses to cast without the eye open.</p>
 */
public class AmaterasuAbility extends Ability implements Ability.Cooldown, Ability.Channeled {

    /** Black fire is loud in the chakra sense; it carries as far as a shout. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** C V V B C - the Sharingan, then one more key. */
    public static final long COMBO = 1112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "amaterasu");

    /** How far down the look ray the flame can be fixed. Walls stop it, as they stop every eye. */
    private static final double RANGE = 24.0d;

    /** Chakra for one cast, and how long the eye needs before it can do it again. */
    private static final float COST = 150.0f;
    private static final int COOLDOWN = 400;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // A tap (chargeAmount < 0) needs a target; a held breath does not.
        if (chargeAmount < 0 && !(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.amaterasu.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float baseCost;
        if (chargeAmount < 0) {
            baseCost = RinneSharinganAbility.isActive(ninjaData) ? COST * 0.7f : COST;
        } else {
            baseCost = 2.0f;
        }
        float cost = this.masteryCost(ninjaData, baseCost);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ticksChanneled == 0) {
            level.playSound(null, player, net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // Visual black-flame beam: solid stream from the eye out to 24 blocks.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        for (double dist = 0.5d; dist <= 24.0d; dist += 0.5d) {
            double progress = dist / 24.0d;
            double spread = 0.05d + 0.25d * progress;
            Vec3 point = eye.add(look.scale(dist));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z,
                    2, spread, spread, spread, 0.01d);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z,
                    1, spread, spread, spread, 0.01d);
        }
        if (ticksChanneled % 10 == 0) {
            for (LivingEntity target : BreathCone.entities(level, player, 24.0d, 0.7d)) {
                if (target == player || target.isSpectator() || !target.isAlive()) {
                    continue;
                }
                if (target instanceof Player other && other.isCreative()) {
                    continue;
                }
                if (Friends.areFriends(player, target)) {
                    continue;
                }
                AmaterasuEvents.ignite(level, target, player);
            }
            net.minecraft.world.phys.BlockHitResult hit1 = Targeting.lookedAtBlock(player, 8.0d);
            if (hit1 != null) {
                net.minecraft.core.BlockPos pos = hit1.getBlockPos();
                if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
                    AmaterasuEvents.heatFurnace(level, pos);
                }
            }
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive >= 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The black flame is a furnace's worth of heat: only the furnace under the caster's crosshair
        // is driven at ten times its speed for half a minute, the way the fire breath stokes a furnace.
        // This is done before the entity check so a tap aimed at a furnace works without a victim.
        net.minecraft.world.phys.BlockHitResult hit2 = Targeting.lookedAtBlock(player, 8.0d);
        if (hit2 != null) {
            net.minecraft.core.BlockPos pos = hit2.getBlockPos();
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
                AmaterasuEvents.heatFurnace(level, pos);
            }
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)) {
            return;
        }
        if (target == player || target.isSpectator() || !target.isAlive()) {
            return;
        }
        if (target instanceof Player other && other.isCreative()) {
            return;
        }
        if (Friends.areFriends(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.friend")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        AmaterasuEvents.ignite(level, target, player);
    }
}
