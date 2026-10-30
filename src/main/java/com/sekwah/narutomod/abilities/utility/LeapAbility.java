package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.triggers.NarutoAdvancements;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.sounds.NarutoSounds;
import com.sekwah.sekclib.util.PlayerUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class LeapAbility extends Ability {
    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public boolean logInChat() {
        return false;
    }

    @Override
    public SoundEvent castingSound() {
        return null;
    }

    @Override
    public SoundEvent castingFailSound() {
        return null;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // Six open gates lift the restriction that the leap may only be taken from the ground.
        boolean sixGates = ninjaData.getGateCount() >= 6;
        if(!sixGates && !player.onGround()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notonground", Component.translatable("jutsu.leap").withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        // Tailed Beast modes, the Eight Gates and Six Paths mode carry the leap for free.
        boolean free = TailedCloakAbility.isActive(player)
                || DividedCloakAbility.isActive(player)
                || PerfectCloakAbility.isActive(player)
                || ninjaData.getGateCount() > 0
                || SixPathsModeAbility.isActive(player);
        if(free) {
            return true;
        }
        float mastery = Mastery.level(ninjaData, this);
        float cost = 10.0f - 5.0f * mastery;
        if(cost < 5.0f) {
            cost = 5.0f;
        }
        if(ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina", Component.translatable("jutsu.leap").withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, 40);
        return true;
    }

    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
        // Leap is spammed often; slow its mastery down so it is not maxed in a few minutes.
        Mastery.recordCredit(player, ninjaData, this, 0.2f);
        NarutoAdvancements.onTechniqueCast(player, ninjaData, this);
        if (SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "mechanic:residual_force")) {
            player.getPersistentData().putLong("narutomod:residual_force_until",
                    player.level().getGameTime() + 100L);
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        Vec3 lookVector = player.getLookAngle();
        float mastery = Mastery.level(ninjaData, this);
        float horScale = 2.41f * (1.0f + 0.25f * mastery);
        PlayerUtil.setVelocity(player, lookVector.x * horScale, (lookVector.y * 0.6 + 0.8F)
                , lookVector.z * horScale, true);
        player.level().playSound(null,
                player, NarutoSounds.LEAP.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
        player.level().gameEvent(player, NarutoGameEvents.LEAP.get(), player.position());
    }
}
