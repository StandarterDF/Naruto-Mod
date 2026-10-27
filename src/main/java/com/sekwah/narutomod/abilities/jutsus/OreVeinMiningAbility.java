package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.PickaxeItem;

/**
 * The mastered form of {@link OreExcavationJutsuAbility}: a stance that follows the pickaxe instead
 * of being cast at a seam.
 *
 * <p>While it is on, every ore the caster breaks by hand takes its whole vein with it. The first
 * block is the ordinary swing - the world breaks it, the pickaxe wears, the drop falls - and the
 * rest of the connected seam is pulled out for chakra per block. That is the difference between the
 * two techniques: the older one is a spell paid up front for every seam in reach, this one is a
 * habit that costs nothing until a vein actually opens.</p>
 *
 * <p>The stance has no visible body of its own. The work is done by {@code OreVeinEvents} on the
 * block break, and the only cost here is a trickle of chakra to keep the stance alive.</p>
 */
public class OreVeinMiningAbility extends Ability implements Ability.Toggled {

    /** Silent: the pickaxe is what is heard. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V C C - the ore excavation seal and one more key. */
    public static final long COMBO = 2313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "ore_vein_mining");

    /** Chakra burnt per tick to keep the stance up. */
    private static final float UPKEEP = 0.05f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        if (!(player.getMainHandItem().getItem() instanceof PickaxeItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.needpickaxe",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, UPKEEP);
        if (ninjaData.getChakra() < cost) {
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The stance is synced; all the work happens on the server when a block breaks.
    }

    /** @return whether the caster is mining whole veins right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    @Override
    public com.sekwah.narutomod.abilities.Nature nature() {
        return com.sekwah.narutomod.abilities.Nature.EARTH;
    }
}
