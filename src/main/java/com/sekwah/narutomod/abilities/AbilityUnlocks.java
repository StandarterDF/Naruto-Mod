package com.sekwah.narutomod.abilities;

import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Which techniques a caster is actually allowed to use.
 *
 * <p>A fresh ninja only gets the handful of basics that keep them moving and refilling - everything
 * else is bought in the skill tree, so the tree is the way the mod teaches its own mechanics rather
 * than a purely numeric board. The set of learned nodes is the single source of truth: the client
 * asks the same question to grey a row out in the jutsu menu, and the server asks it again before it
 * runs a technique, so a hacked client cannot skip the tree.</p>
 */
public final class AbilityUnlocks {

    /**
     * Paths of the techniques every ninja has from the start. Kept deliberately tiny: enough to
     * charge chakra and to get out of trouble, nothing that gives a direction in a fight.
     */
    private static final Set<String> BASIC = Set.of(
            "chakra_charge",
            "substitution",
            "leap");

    /**
     * Techniques that are never on the skill tree and are learned from a scroll alone. See
     * {@code JutsuScrollItem}: the scroll writes its knowledge into the caster's scroll set, and this
     * is the single check both sides share.
     */
    private static final Set<String> SCROLL_ONLY = Set.of(
            "kirin");

    private AbilityUnlocks() {
    }

    /** The skill-tree key a technique is gated behind. */
    public static String unlockKey(ResourceLocation abilityId) {
        return "ability:" + abilityId.getPath();
    }

    /**
     * Whether the caster has learned to gather nature energy. The nature energy pool is not part of
     * a ninja from birth: without this node there is nowhere to put the energy, so
     * {@link INinjaData#getMaxSeninMana()} stays at zero and the senin bar never appears.
     */
    public static boolean hasSenjutsu(INinjaData ninjaData) {
        return ninjaData != null
                && isUnlocked(ninjaData, com.sekwah.narutomod.abilities.jutsus.SenjutsuChargingAbility.ID);
    }

    /** Whether the caster may use this technique right now. */
    public static boolean isUnlocked(INinjaData ninjaData, ResourceLocation abilityId) {
        if (abilityId == null || ninjaData == null) {
            return true;
        }
        if (BASIC.contains(abilityId.getPath())) {
            return true;
        }
        if (SCROLL_ONLY.contains(abilityId.getPath())) {
            return ninjaData.getSkillData().hasScroll(abilityId);
        }
        return SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), unlockKey(abilityId));
    }
}
