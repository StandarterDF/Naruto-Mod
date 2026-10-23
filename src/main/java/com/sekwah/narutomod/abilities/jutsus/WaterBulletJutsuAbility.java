package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.WaterBulletJutsuEntity;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * More of a slight speed boost than an actual dash
 */
public class WaterBulletJutsuAbility extends Ability implements Ability.Cooldown {
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
        return 132;
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
        for (int i = 0; i < 3; i++) {
            ninjaData.scheduleDelayedTickEvent((delayedPlayer) -> {
                Vec3 shootSpeed = player.getLookAngle();
                WaterBulletJutsuEntity waterBullet = new WaterBulletJutsuEntity(player, shootSpeed.x, shootSpeed.y, shootSpeed.z);
                waterBullet.setYRot(player.getYRot() - 180);
                player.level().addFreshEntity(waterBullet);
                player.level().playSound(null, player, NarutoSounds.WATER_BULLET_SHOOT.get(), SoundSource.PLAYERS, 1f, 1.0f);
            }, 10 + i * 15);
        }

    }

    @Override
    public int getCooldown() {
        return 4 * 20;
    }
}
