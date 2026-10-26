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
     * Extra substitution charges the caster's pool can hold. The substitution technique starts at a
     * single charge and the tree is the only way to raise it, one charge per node.
     */
    MAX_SUBSTITUTIONS,

    /**
     * A small round bonus to the pools every caster has - chakra, stamina and health - handed out by
     * the very first node of the tree, the one everything else branches off. Unlike the specific
     * effects above, its number is added to each of them at once, so the entry node reads as "a bit
     * of everything" rather than as a chakra node in disguise.</p>
     */
    ALL_ROUNDED,

    /** Fraction of extra chakra the charge technique puts back each tick. */
    CHAKRA_CHARGE_RATE,

    /** Fraction of extra substitution charge restored each tick, up to the pool's cap. */
    SUBSTITUTION_REGEN,

    /** Extra ticks of invisibility the substitution leaves behind after it is used. */
    SUBSTITUTION_INVISIBILITY,

    /** Fraction of extra lift the double jump pushes the caster up with. */
    DOUBLE_JUMP_POWER,

    /** Fraction of extra life a combat clone gets before it pops on its own. */
    CLONE_LIFETIME,

    /** Fraction of extra shift a working clone gets before it pops on its own. */
    WORKER_LIFETIME,

    /** Fraction of the stamina a cushioned landing costs that is waived. */
    SOFT_LANDING,

    /**
     * Extra blocks of Byakugan circular vision. Only the Byakugan reads it, because it is the one
     * eye whose picture is drawn from a radius rather than from whoever is being looked at.
     */
    BYAKUGAN_RANGE,

    /** Extra points of nature energy (senin mana) the caster can hold. */
    SENIN_MANA,

    /** Fraction of extra nature energy gathered each tick while standing still. */
    SENIN_MANA_REGEN,

    /** Fraction off the nature energy it takes to open Sage Mode. */
    SAGE_ENTRY,

    /**
     * A node that teaches a technique or a mechanic instead of granting a number. The key it teaches
     * lives on the node itself; see {@link SkillTree#hasUnlock}.
     */
    UNLOCK
}
