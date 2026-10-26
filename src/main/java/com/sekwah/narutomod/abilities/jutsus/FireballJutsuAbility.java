package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.FireballJutsuEntity;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * More of a slight speed boost than an actual dash
 */
public class FireballJutsuAbility extends Ability implements Ability.Cooldown {
    /**
     * How far a cast of this technique carries as a game event. An ordinary cast.
     */
    @Override
    public int noiseRadius() {
        return 24;
    }


    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return 121;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, 30f);
        if(ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra", Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 30);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        ninjaData.scheduleDelayedTickEvent((delayedPlayer) -> {
            Vec3 shootSpeed = player.getLookAngle();
            FireballJutsuEntity fireball = new FireballJutsuEntity(player, shootSpeed.x, shootSpeed.y, shootSpeed.z);
            player.level().addFreshEntity(fireball);
            player.level().playSound(null, player, NarutoSounds.FIREBALL_SHOOT.get(), SoundSource.PLAYERS, 1f, 1.0f);
        }, 10);
    }

    @Override
    public int getCooldown() {
        return 3 * 20;
    }

    /** The element this technique belongs to, for the caster's innate affinity. */
    @Override
    public com.sekwah.narutomod.abilities.Nature nature() {
        return com.sekwah.narutomod.abilities.Nature.FIRE;
    }
}
