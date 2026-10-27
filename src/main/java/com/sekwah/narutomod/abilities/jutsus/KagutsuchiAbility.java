package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.events.AmaterasuEvents;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Kagutsuchi: the second half of Amaterasu. Amaterasu puts the black flame somewhere; Kagutsuchi is
 * the hand that moves it afterwards.
 *
 * <p>It has two readings, chosen by whether the caster is sneaking:</p>
 * <ul>
 *     <li><strong>Gather</strong> (default) - every black flame near the caster is pulled off the
 *     bodies carrying it and heaped onto the creature under the crosshair. The flame on the focus
 *     grows with each one gathered, and the sudden weight of it bites once as it lands.</li>
 *     <li><strong>Snuff</strong> (sneaking) - the same hand closes instead, putting out every black
 *     flame in reach. This is the only way to save a friend who was caught by somebody else's
 *     Amaterasu, short of killing them or waiting ten seconds.</li>
 * </ul>
 *
 * <p>Both readings need the Sharingan open, for the same reason Amaterasu does: the flame is shaped
 * by the eye, not thrown by the hand.</p>
 */
public class KagutsuchiAbility extends Ability implements Ability.Cooldown {

    /** C V V B B - one key past Amaterasu. */
    public static final long COMBO = 2212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kagutsuchi");

    /** How far the shaping hand reaches. */
    private static final double RANGE = 24.0d;

    /** Radius the loose flames are gathered from. */
    private static final double RADIUS = 12.0d;

    /** What each reading costs. Gathering is the expensive one; putting a fire out is cheap. */
    private static final float GATHER_COST = 40.0f;
    private static final float SNUFF_COST = 20.0f;

    /** Every gathered flame adds this much burn time, and this much bite on landing. */
    private static final int TICKS_PER_FLAME = 60;
    private static final float DAMAGE_PER_FLAME = 3.0f;

    /** The eye needs a moment after reshaping fire. */
    private static final int COOLDOWN = 120;

    /** Fire is loud in the chakra sense. */
    @Override
    public int noiseRadius() {
        return 10;
    }

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
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean snuff = player.isShiftKeyDown();
        if (snuff) {
            if (burningNear(level, player).isEmpty()) {
                this.fail(player, ninjaData, "jutsu.fail.kagutsuchi.noflame");
                return false;
            }
        } else {
            if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity focus)
                    || focus == player || !focus.isAlive() || focus.isSpectator()) {
                this.fail(player, ninjaData, "jutsu.fail.kagutsuchi.notarget");
                return false;
            }
            if (focus instanceof Player other && other.isCreative()) {
                return false;
            }
            if (Friends.areFriends(player, focus)) {
                this.fail(player, ninjaData, "jutsu.fail.friend");
                return false;
            }
            // A flame has to exist somewhere first; the focus already wearing it counts, and the
            // cast feeds that fire rather than finding nothing to shape.
            if (!AmaterasuEvents.isBurning(focus) && this.looseFlames(level, player, focus).isEmpty()) {
                this.fail(player, ninjaData, "jutsu.fail.kagutsuchi.noflame");
                return false;
            }
        }

        // The third eye shapes the black flame more cheaply while it is open.
        float base = snuff ? SNUFF_COST : GATHER_COST;
        if (RinneSharinganAbility.isActive(ninjaData)) {
            base *= 0.7f;
        }
        float cost = this.masteryCost(ninjaData, base);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            this.snuff(level, player);
        } else {
            this.gather(level, player);
        }
    }

    /** Pulls every loose flame in reach onto the looked-at target, feeding a fire already there. */
    private void gather(ServerLevel level, Player player) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity focus)) {
            return;
        }
        if (focus == player || !focus.isAlive() || focus.isSpectator() || Friends.areFriends(player, focus)) {
            return;
        }
        List<LivingEntity> flames = this.looseFlames(level, player, focus);
        boolean focusBurning = AmaterasuEvents.isBurning(focus);
        if (flames.isEmpty() && !focusBurning) {
            return;
        }
        for (LivingEntity burning : flames) {
            AmaterasuEvents.extinguish(burning);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, burning.getX(),
                    burning.getY() + burning.getBbHeight() * 0.5d, burning.getZ(),
                    8, 0.25d, 0.25d, 0.25d, 0.06d);
        }
        int gathered = flames.size();
        // Shaping fire onto a body that is already alight still puts something into it: the cast is
        // never a no-op when the target is wearing the black flame.
        int fed = Math.max(gathered, 1);
        AmaterasuEvents.refresh(level, focus, player, TICKS_PER_FLAME * gathered);
        focus.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.AMATERASU, player, player),
                DAMAGE_PER_FLAME * fed);
        level.playSound(null, focus.getX(), focus.getY(), focus.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 0.45f);
    }

    /** Puts out every black flame in reach, friend or not. */
    private void snuff(ServerLevel level, Player player) {
        int snuffed = AmaterasuEvents.snuffAround(level, player.position(), RADIUS);
        if (snuffed == 0) {
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 1.1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(),
                player.getY() + 1.0d, player.getZ(), 30, RADIUS * 0.4d, 0.6d, RADIUS * 0.4d, 0.01d);
    }

    /** Black flames in reach that are not already on the focus. */
    private List<LivingEntity> looseFlames(ServerLevel level, Player player, LivingEntity focus) {
        List<LivingEntity> flames = new ArrayList<>();
        for (LivingEntity burning : burningNear(level, player)) {
            if (burning != focus) {
                flames.add(burning);
            }
        }
        return flames;
    }

    /** Every body in reach that is carrying the black flame, the caster included. */
    private static List<LivingEntity> burningNear(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                AmaterasuEvents::isBurning);
    }

    private void fail(Player player, INinjaData ninjaData, String key) {
        player.displayClientMessage(Component.translatable(key,
                Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.RED), true);
    }
}
