package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;

import java.util.Map;

/**
 * Combat Focus: a stance that compresses the long physical seals into two keys.
 *
 * <p>The physical techniques carry six-key seals, which is fine for a duel that has already stopped
 * but useless in a brawl where the next blow is already coming. With this stance held, the same two
 * keys select a technique from the school the caster is actually fighting in: a sword in hand opens
 * the kenjutsu list, an empty hand (or any other weapon) opens the taijutsu list. The long seals
 * still exist and still work; the stance is simply the fast way to reach the same techniques.</p>
 *
 * <p>The mapping is applied on the client before the global combo map is consulted and the server
 * validates the resulting ability once more, so the stance is a convenience rather than a bypass: a
 * technique whose node has not been learned is refused exactly as before.</p>
 */
public class CombatStanceAbility extends Ability implements Ability.Toggled {

    /** Quiet: the stance is a way of standing, not a shout. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** A A A - the shortest seal in the mod, because the stance is meant to be quick to raise. */
    public static final long COMBO = 111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "combat_stance");

    /** Small upkeep so the stance is a choice rather than a permanent buff. */
    private static final float UPKEEP = 0.05f;
    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Empty hand: the taijutsu list. */
    private static final Map<Long, ResourceLocation> TAIJUTSU = Map.of(
            12L, new ResourceLocation(NarutoMod.MOD_ID, "projectile_cut"),
            13L, new ResourceLocation(NarutoMod.MOD_ID, "drunken_fist"),
            21L, new ResourceLocation(NarutoMod.MOD_ID, "inner_strike"),
            23L, new ResourceLocation(NarutoMod.MOD_ID, "palm_block"),
            31L, new ResourceLocation(NarutoMod.MOD_ID, "vacuum_blade"),
            32L, new ResourceLocation(NarutoMod.MOD_ID, "bone_bullets"),
            33L, new ResourceLocation(NarutoMod.MOD_ID, "camellia_dance"));

    /** Sword in hand: the kenjutsu list. */
    private static final Map<Long, ResourceLocation> KENJUTSU = Map.of(
            12L, new ResourceLocation(NarutoMod.MOD_ID, "kusa_giri"),
            13L, new ResourceLocation(NarutoMod.MOD_ID, "kesa_giri"),
            21L, new ResourceLocation(NarutoMod.MOD_ID, "kamae"),
            23L, new ResourceLocation(NarutoMod.MOD_ID, "bleeding_blade"),
            31L, new ResourceLocation(NarutoMod.MOD_ID, "elemental_edge"),
            32L, new ResourceLocation(NarutoMod.MOD_ID, "vacuum_blade"),
            33L, new ResourceLocation(NarutoMod.MOD_ID, "chakra_whetstone"));

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
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
        // The stance is synced; the mapping is read from it by the key handler.
    }

    /** @return whether the caster is in the combat stance right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * The ability a short seal means while the stance is up, or null when the stance is down or the
     * seal is not one of the aliases.
     */
    public static ResourceLocation resolve(Player player, INinjaData ninjaData, long combo) {
        if (player == null || ninjaData == null || !isActive(ninjaData)) {
            return null;
        }
        Map<Long, ResourceLocation> school = player.getMainHandItem().getItem() instanceof SwordItem
                ? KENJUTSU : TAIJUTSU;
        return school.get(combo);
    }

    /** The name of the school the caster is currently in, for the toggle message. */
    public static String schoolKey(Player player) {
        return player.getMainHandItem().getItem() instanceof SwordItem
                ? "narutomod:combat_stance.kenjutsu" : "narutomod:combat_stance.taijutsu";
    }

    /** Called when the stance is switched on so the caster knows which school the keys will use. */
    @Override
    public boolean logInChat() {
        return false;
    }

    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        player.displayClientMessage(Component.translatable("jutsu.combat_stance.on",
                Component.translatable(schoolKey(player)).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }
}
