package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.item.weapons.BoneMultitoolItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Grows a bone multitool from the caster's own skeleton: one implement that digs, fights, tills,
 * strips and flattens, with a short life of its own.
 */
public class BoneToolsAbility extends Ability implements Ability.Cooldown {

    /** A handful of bone tools is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** B C B - a short seal, then the two keys of the growth. */
    public static final long COMBO = 312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "bone_tools");

    private static final float COST = 25.0f;
    private static final int COOLDOWN = 600;
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
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.RED), true);
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
        // A practised hand grows a better tool: wood-like at first, a netherite sword's equal at 90%+.
        float mastery = Mastery.level(ninjaData, ID);
        int tier = mastery >= 0.9f ? 4 : Math.min(3, (int) (mastery * 5.0f));
        ItemStack tool = BoneMultitoolItem.create(tier);
        if (!player.getInventory().add(tool)) {
            player.drop(tool, false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}