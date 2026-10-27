package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.AmaterasuEvents;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Amaterasu: the black flame that is willed onto a target rather than thrown at it.
 *
 * <p>Cast through an open Sharingan, it fixes on the creature the caster is looking at and sets it
 * burning with a fire that is not fire. The flame does not care about water, rain or a fire
 * resistance potion, because it is not burning the body - it is a chakra fire that gnaws at it for
 * ten seconds and then goes out on its own. See {@link AmaterasuEvents} for the burn itself.</p>
 *
 * <p>The price is deliberately steep and the cooldown long: one cast is meant to be a decision, not
 * a rotation. The black flame is not a thrown fire but a bonfire's worth of chakra forced onto a
 * body, so it burns a large part of the bar at once. It also heats the ground around the victim:
 * furnaces nearby are stoked to ten times their normal speed for thirty seconds, the same kind of
 * heat the fire breath pours into a furnace. Like every power built on an eye it refuses to cast
 * without the eye open.</p>
 */
public class AmaterasuAbility extends Ability implements Ability.Cooldown {

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.amaterasu.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // The third eye reads the black flame more cheaply while it is open.
        float base = RinneSharinganAbility.isActive(ninjaData) ? COST * 0.7f : COST;
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
        // The black flame is a furnace's worth of heat: everything that cooks around the victim is
        // driven at ten times its speed for half a minute, the way the fire breath stokes a furnace.
        AmaterasuEvents.heatFurnaces(level, target.position(), 8.0d);
    }
}
