package com.sekwah.narutomod.abilities;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
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
            "kirin",
            "six_paths_mode");

    private static final Map<String, String> BASE_REQUIREMENTS = new HashMap<>();

    static {
        // Jougan techniques all grow out of the eye itself.
        String[] jougan = {
                "ability:dimension_step", "ability:jougan_stasis", "ability:jougan_unweave",
                "ability:seal_portal", "ability:sewing_pocket", "ability:sewing_lock",
                "ability:chakra_shear", "ability:structure_cut", "ability:jougan_phase",
                "ability:chakra_sever"
        };
        for (String k : jougan) BASE_REQUIREMENTS.put(k, "ability:jougan");

        // Senrigan techniques all grow out of the eye itself.
        String[] senrigan = {
                "ability:senrigan_sight", "ability:senrigan_flight",
                "ability:senrigan_evasion", "ability:senrigan_guidance"
        };
        for (String k : senrigan) BASE_REQUIREMENTS.put(k, "ability:senrigan");

        // Sharingan techniques that are Mangekyo-level still need the eye itself.
        String[] sharingan = {
                "ability:amaterasu", "ability:kagutsuchi", "ability:susanoo",
                "ability:tsukuyomi", "ability:izanagi", "ability:izanami",
                "ability:kamui", "ability:kamui_phasing", "ability:kotoamatsukami"
        };
        for (String k : sharingan) BASE_REQUIREMENTS.put(k, "ability:sharingan");

        // Rinnegan techniques need the Rinnegan itself.
        String[] rinnegan = {
                "ability:universal_force", "ability:chibaku_tensei", "ability:chakra_transfer",
                "ability:naraka_path", "ability:asura_missiles", "ability:outer_chains",
                "ability:outer_armor", "ability:momoshiki_absorption", "ability:limbo",
                "ability:rinnegan_prison", "ability:item_attraction", "ability:black_rod"
        };
        for (String k : rinnegan) BASE_REQUIREMENTS.put(k, "ability:rinnegan");

        // Byakugan techniques need the Byakugan itself.
        String[] byakugan = {
                "ability:rotation", "ability:tenketsu_needles", "ability:tenketsu_blind",
                "ability:chakra_grip", "ability:node_rupture", "ability:byakugan_farsight",
                "ability:byakugan_mist_sight", "ability:void_cast", "ability:mechanism_sight",
                "ability:hidden_seal", "ability:doctor_sight", "ability:sixty_four_palms",
                "ability:byakugan_mark", "ability:air_palm", "ability:circular_block",
                "ability:soshiken", "ability:palm_lance", "ability:eight_trigrams_collapse"
        };
        for (String k : byakugan) BASE_REQUIREMENTS.put(k, "ability:byakugan");

        // Mode techniques need their base mode/technique.
        BASE_REQUIREMENTS.put("ability:curse_seal_two", "ability:curse_seal_one");
        BASE_REQUIREMENTS.put("ability:tailed_cloak", "ability:chakra_cloak");
        BASE_REQUIREMENTS.put("ability:perfect_cloak", "ability:tailed_cloak");
        BASE_REQUIREMENTS.put("ability:divided_cloak", "ability:tailed_cloak");
        BASE_REQUIREMENTS.put("ability:seal_vessel", "ability:perfect_cloak");
        BASE_REQUIREMENTS.put("ability:tailed_ball", "ability:perfect_cloak");
    }

    private AbilityUnlocks() {
    }

    /** The base eye/mode an unlock key depends on, or null when none. */
    public static String baseRequirement(String unlockKey) {
        return unlockKey == null ? null : BASE_REQUIREMENTS.get(unlockKey);
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
            if (!ninjaData.getSkillData().hasScroll(abilityId)) {
                return false;
            }
            // Kirin is scroll-only, but a scroll alone is not enough: the caster has to have grown
            // into the lightning nature first, or the technique is just words on paper.
            if ("kirin".equals(abilityId.getPath())) {
                return SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "ability:lightning_mode");
            }
            if ("six_paths_mode".equals(abilityId.getPath())) {
                return SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "ability:rinnegan");
            }
            return true;
        }
        // Shadow shuriken leaves a shadow clone where it lands, so it is only usable once the caster
        // has actually learned to shape one. The tree alone only asks for the chakra node next to it.
        if (abilityId != null && "shuriken_shadow".equals(abilityId.getPath())) {
            if (!SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "ability:shadow_clone")) {
                return false;
            }
        }
        // The Rinnegan is the eye the Sharingan grows into: it no longer needs the whole Sharingan
        // branch, only the Sharingan itself and at least one Mangekyo technique such as Susanoo.
        if (abilityId != null && "rinnegan".equals(abilityId.getPath())) {
            Set<ResourceLocation> unlocked = ninjaData.getSkillData().getUnlocked();
            if (!SkillTree.hasUnlock(unlocked, "ability:sharingan")) {
                return false;
            }
            boolean hasMangekyo = SkillTree.hasUnlock(unlocked, "ability:tsukuyomi")
                    || SkillTree.hasUnlock(unlocked, "ability:susanoo")
                    || SkillTree.hasUnlock(unlocked, "ability:izanagi")
                    || SkillTree.hasUnlock(unlocked, "ability:izanami");
            if (!hasMangekyo) {
                return false;
            }
        }
        // Kamui is a Sharingan technique: it cannot be learned without the Sharingan itself.
        if (abilityId != null && ("kamui".equals(abilityId.getPath())
                || "kamui_phasing".equals(abilityId.getPath()))) {
            if (!SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "ability:sharingan")) {
                return false;
            }
        }
        // Direct fallback for the Sharingan: the eye is taught by the sharingan_eye node, and a
        // lookup through SkillTree must never silently hide an already bought eye.
        if (abilityId != null && "sharingan".equals(abilityId.getPath())) {
            return ninjaData.getSkillData().getUnlocked()
                    .contains(new ResourceLocation(NarutoMod.MOD_ID, "sharingan_eye"));
        }
        return SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), unlockKey(abilityId));
    }
}
