package com.sekwah.narutomod.skills;

/**
 * The four sections of the skill tree - the first level of the two-level tab bar.
 *
 * <p>A section is only a grouping: it holds no nodes of its own and has no centre. Each section
 * contains a handful of {@link SkillBranch branches}, and every branch is one self-contained board
 * with a single centre. The screen shows the sections on the top row and the branches of the active
 * section on the row below, so a caster first picks a discipline ("Physical Path") and then a
 * speciality inside it ("Athletics").</p>
 *
 * <p>The branch-to-section link lives on {@link SkillBranch#section()}, not here: that keeps the two
 * enums free of an initialisation cycle, and it means a new branch is declared in exactly one place.
 * The screen derives the per-section branch lists by filtering {@link SkillBranch#values()}.</p>
 */
public enum SkillSection {

    /** The body: raw pools, mobility, hand-to-hand combat and medical ninjutsu. */
    PHYSICAL("naruto.gui.skill.section.physical", 0xFFE8E8E8),

    /** The Eight Gates: the inner gates and the techniques that open them, on their own board. */
    GATES("naruto.gui.skill.section.gates", 0xFF8B0000),

    /** The five natures. Each element is its own board so it can grow without crowding the others. */
    NINJUTSU("naruto.gui.skill.section.ninjutsu", 0xFFFFB04D),

    /** Everything that is not a plain element: clones, space, the mind and senjutsu. */
    SPECIAL("naruto.gui.skill.section.special", 0xFF9C8CFF),

    /** The eye bloodlines. The branches exist but are still empty - the frame for what comes next. */
    DOJUTSU("naruto.gui.skill.section.dojutsu", 0xFFFF6B6B);

    private final String translationKey;
    private final int colour;

    SkillSection(String translationKey, int colour) {
        this.translationKey = translationKey;
        this.colour = colour;
    }

    public String translationKey() {
        return this.translationKey;
    }

    /** ARGB colour used for the section's tab. */
    public int colour() {
        return this.colour;
    }
}
