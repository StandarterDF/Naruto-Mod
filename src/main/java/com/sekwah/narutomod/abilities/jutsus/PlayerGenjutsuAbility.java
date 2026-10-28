package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
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
import net.minecraft.world.entity.player.Player;

/**
 * The illusion turned on another ninja.
 *
 * <p>Mobs are easy: they have one pair of eyes and a short memory. A player is harder, and on a
 * server it is also a political act, so this technique is off unless the server turns it on. What it
 * does is simple and deliberately short - the victim's world goes dark and unstable for a few
 * seconds, long enough to break a duel's rhythm but not long enough to be a death sentence on its
 * own.</p>
 */
public class PlayerGenjutsuAbility extends Ability implements Ability.Cooldown {

    /** C B C C B - one key off the other illusions. */
    public static final long COMBO = 12312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "player_genjutsu");

    private static final double RANGE = 12.0d;
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 600;

    @Override
    public int noiseRadius() {
        return 0;
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
        if (!NarutoConfig.genjutsuOnPlayers) {
            player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.players_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Player target = target(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.player_genjutsu.notarget",
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
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        Player target = target(player);
        if (target == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 1, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, true));
        target.getPersistentData().putLong("narutomod:player_genjutsu_until",
                level.getGameTime() + 160L);
        level.sendParticles(ParticleTypes.WITCH, target.getX(),
                target.getY() + target.getBbHeight() * 0.8d, target.getZ(),
                24, 0.5d, 0.7d, 0.5d, 0.02d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    /** A player under the gaze who is neither a friend nor on the caster's team. */
    private Player target(Player caster) {
        return Targeting.lookedAtEntity(caster, RANGE) instanceof Player other
                && other != caster
                && !other.isCreative()
                && !other.isSpectator()
                && !other.isAlliedTo(caster)
                && !Friends.areFriends(caster, other)
                ? other : null;
    }
}
