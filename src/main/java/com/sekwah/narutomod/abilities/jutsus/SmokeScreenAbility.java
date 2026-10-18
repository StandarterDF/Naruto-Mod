package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.SmokeCloudEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Bursts a bank of smoke into the air and vanishes into it.
 *
 * <p>It is not the substitution: substitution swaps the caster out for a log they left behind
 * somewhere, which needs a log and swaps positions. The smoke is an area - everyone inside it is
 * hidden, the caster included - so it can hide a doorway, break a chase, or cover somebody else
 * without the caster giving up their place in the fight. It is also not genjutsu: genjutsu holds one
 * mind and turns it on the victim's allies, where the smoke holds nobody and simply takes away the
 * ability to see.</p>
 *
 * <p>The cloud is left where it is cast, so the caster has to step out of it; that is the escape. It
 * lasts eight seconds, and hostile players caught inside are darkened unless the server asks for the
 * mob-only version.</p>
 */
public class SmokeScreenAbility extends Ability implements Ability.Cooldown {

    /** B B B. */
    public static final long COMBO = 222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "smoke_screen");

    /** Chakra for one cloud. */
    private static final float COST = 25.0f;

    /** Cooldown in ticks: twenty seconds. Mastery shortens it like any other technique. */
    private static final int COOLDOWN = 20 * 20;

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
        if (!NarutoConfig.smokeEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.smoke.disabled",
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
        SmokeCloudEntity cloud = new SmokeCloudEntity(NarutoEntities.SMOKE_CLOUD.get(), level);
        cloud.setOwner(player);
        cloud.moveTo(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(cloud);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}
