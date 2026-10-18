package com.sekwah.narutomod.skills;

/**
 * What a skill node actually does. Every node in the tree hands out exactly one of these, and all
 * of them are plain passive bonuses so the tree never has to know about a technique's own code.
 */
public enum SkillEffect {

    /** Extra points of chakra capacity. */
    MAX_CHAKRA,

    /** Extra chakra restored each second. */
    CHAKRA_REGEN,

    /** Extra points of stamina capacity. */
    MAX_STAMINA,

    /** Extra stamina restored each second. */
    STAMINA_REGEN,

    /** Extra points of maximum health, two per heart. */
    MAX_HEALTH,

    /** Extra armour points, shown on the vanilla armour bar. */
    ARMOR,

    /** Fraction taken off every technique's chakra price. */
    COST_REDUCTION,

    /** Fraction taken off every technique's cooldown. */
    COOLDOWN_REDUCTION,

    /** Extra movement speed, added straight onto the player's speed attribute. */
    MOVEMENT_SPEED,

    /** Extra melee attack damage, added straight onto the player's damage attribute. */
    ATTACK_DAMAGE,

    /**
     * A node that teaches a technique or a mechanic instead of granting a number. The key it teaches
     * lives on the node itself; see {@link SkillTree#hasUnlock}.
     */
    UNLOCK
}
