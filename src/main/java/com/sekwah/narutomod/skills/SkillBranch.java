package com.sekwah.narutomod.skills;

/**
 * The trunks of the skill tree. They are only a grouping and a colour - the branches are not
 * separate progress bars - so a caster is free to spread their experience across all of them.
 */
public enum SkillBranch {

    ROOT("naruto.gui.skill.branch.root", 0xFFE8E8E8),
    CHAKRA("naruto.gui.skill.branch.chakra", 0xFF55CCFF),
    BODY("naruto.gui.skill.branch.body", 0xFFFF7755),
    CONTROL("naruto.gui.skill.branch.control", 0xFFCCAA55),
    AGILITY("naruto.gui.skill.branch.agility", 0xFF66DD88),
    CLONES("naruto.gui.skill.branch.clones", 0xFFB08CFF),
    ELEMENTAL("naruto.gui.skill.branch.elemental", 0xFFFFB04D),
    LABOUR("naruto.gui.skill.branch.labour", 0xFFBFB06A),
    SPACE("naruto.gui.skill.branch.space", 0xFF8C9EFF),
    MIND("naruto.gui.skill.branch.mind", 0xFFFF77CC);

    private final String translationKey;
    private final int colour;

    SkillBranch(String translationKey, int colour) {
        this.translationKey = translationKey;
        this.colour = colour;
    }

    public String translationKey() {
        return this.translationKey;
    }

    /** ARGB colour used for the branch's nodes and links. */
    public int colour() {
        return this.colour;
    }
}
