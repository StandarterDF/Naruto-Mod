package com.sekwah.narutomod.abilities;

import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealOneAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealTwoAbility;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.LightningModeAbility;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one-at-a-time rule for the body modes: a cloak, a seal, the six paths and the lightning charge.
 *
 * <p>These are not techniques that happen to be stances, they are different shapes a body can be
 * forced into, and a body has room for one of them. The eyes are deliberately not part of this: an
 * eye is a stance on the sight rather than on the body, it already keeps itself to one open at a
 * time (see {@code AbstractDojutsuAbility#eyes()}), and a dojutsu is meant to burn alongside a cloak
 * rather than instead of it. Nothing here reads, adds or removes an eye id.</p>
 *
 * <p>Two of the modes are layered by design and have to survive the switch: the tailed cloak is the
 * chakra cloak drawn out into a tail, and the second curse seal is the first one let off its leash.
 * A mode is therefore closed unless it is the one being opened or one of its own prerequisites, which
 * is what {@link #PREREQUISITES} is for.</p>
 *
 * <p>Nothing is charged, disabled or announced here. This helper only decides which other stances
 * have to be let go, and it lets them go the ordinary way - through their own ability's end handler -
 * so their marks, attribute modifiers and particles all come off in the same step.</p>
 */
public final class ModeBus {

    /**
     * Every stance that takes the whole body. Declaration order is the order they are closed in, which
     * only matters if a caster somehow has more than one up, and that cannot happen once this rule is
     * wired in.
     */
    private static final List<ResourceLocation> MAJOR_MODES = List.of(
            ChakraCloakAbility.ID,
            TailedCloakAbility.ID,
            DividedCloakAbility.ID,
            PerfectCloakAbility.ID,
            CurseSealOneAbility.ID,
            CurseSealTwoAbility.ID,
            SixPathsModeAbility.ID,
            LightningModeAbility.ID);

    /**
     * The modes that may stay up under another one because the second is built on the first. Removing
     * these instead of closing them would leave the caster wearing a tail with nothing under it.
     */
    private static final Map<ResourceLocation, Set<ResourceLocation>> PREREQUISITES = Map.of(
            TailedCloakAbility.ID, Set.of(ChakraCloakAbility.ID),
            CurseSealTwoAbility.ID, Set.of(CurseSealOneAbility.ID));

    private ModeBus() {
    }

    /** @return every body mode, in the order they are closed in. */
    public static List<ResourceLocation> majorModes() {
        return MAJOR_MODES;
    }

    /** @return whether this id is one of the body modes this helper arbitrates, eyes excluded. */
    public static boolean isMajor(ResourceLocation abilityId) {
        return MAJOR_MODES.contains(abilityId);
    }

    /** @return whether any body mode is currently up on this caster. */
    public static boolean hasMajor(INinjaData ninjaData) {
        Set<ResourceLocation> active = ninjaData.getToggleAbilityData().getAbilitiesHashSet();
        for (ResourceLocation modeId : MAJOR_MODES) {
            if (active.contains(modeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes every body mode the caster has up except the one being opened and whatever that one is
     * built on.
     *
     * <p>Call this with the id of the stance that was just switched on, before it is added to the
     * toggle set, so the caster never pays two upkeeps for a single body. If the id is not a body
     * mode - an eye, a weapon stance, a utility toggle - nothing is closed: those have their own
     * rules and this one has no business in them.</p>
     *
     * @param keep the mode that is being opened and must be left alone
     */
    public static void closeOthers(Player player, INinjaData ninjaData, ResourceLocation keep) {
        if (!isMajor(keep)) {
            return;
        }
        Set<ResourceLocation> prerequisites = PREREQUISITES.getOrDefault(keep, Set.of());
        Set<ResourceLocation> active = ninjaData.getToggleAbilityData().getAbilitiesHashSet();
        for (ResourceLocation modeId : MAJOR_MODES) {
            if (modeId.equals(keep) || prerequisites.contains(modeId) || !active.contains(modeId)) {
                continue;
            }
            Ability mode = NarutoRegistries.ABILITIES.getValue(modeId);
            if (mode != null) {
                // The ordinary end path: the stance takes its own mark and its own modifiers with it.
                ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, mode);
            }
        }
    }
}
