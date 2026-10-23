package com.sekwah.narutomod.skills;

/**
 * The branches of the skill tree - the second level of the tab bar, one board each.
 *
 * <p>Every branch is a self-contained constellation with exactly one centre node: the node without
 * prerequisites. There are deliberately no prerequisites across branches, so a caster who opens a
 * branch is free to walk anywhere inside it. A branch belongs to one {@link SkillSection}, which is
 * only a grouping of branches on the first row of the tab bar - it holds no nodes and has no centre
 * of its own.</p>
 *
 * <p>The order of the enum is the order of the sub-tabs inside a section, so it is grouped by
 * section on purpose. {@link Skill} only ever names the branch a node belongs to; where the node
 * sits inside that branch is purely {@link Skill#x()} and {@link Skill#y()}.</p>
 */
public enum SkillBranch {

    // -----------------------------------------------------------------------------------------
    // 1. PHYSICAL PATH
    // -----------------------------------------------------------------------------------------

    /** Raw pools and stats: health, armour, chakra, regeneration, cost and cooldown. */
    CORE(SkillSection.PHYSICAL, "naruto.gui.skill.branch.core", 0xFFE8E8E8),

    /** Mobility and stamina: speed, the substitution, the double jump, water walking. */
    ATHLETICS(SkillSection.PHYSICAL, "naruto.gui.skill.branch.athletics", 0xFF66DD88),

    /** Hand-to-hand: unarmed damage, the chakra fist and the Eight Gates. */
    TAIJUTSU(SkillSection.PHYSICAL, "naruto.gui.skill.branch.taijutsu", 0xFFFF6B6B),

    /** Medical ninjutsu: feeding, regeneration, the chakra shield and the second breath. */
    MEDICINE(SkillSection.PHYSICAL, "naruto.gui.skill.branch.medicine", 0xFF7FD8FF),

    // -----------------------------------------------------------------------------------------
    // 2. NINJUTSU - one board per nature
    // -----------------------------------------------------------------------------------------

    FIRE(SkillSection.NINJUTSU, "naruto.gui.skill.branch.fire", 0xFFFF7043),

    WATER(SkillSection.NINJUTSU, "naruto.gui.skill.branch.water", 0xFF4FC3F7),

    EARTH(SkillSection.NINJUTSU, "naruto.gui.skill.branch.earth", 0xFFB08D57),

    WIND(SkillSection.NINJUTSU, "naruto.gui.skill.branch.wind", 0xFF9BE7A0),

    WOOD(SkillSection.NINJUTSU, "naruto.gui.skill.branch.wood", 0xFF8BC34A),

    // -----------------------------------------------------------------------------------------
    // 3. SPECIAL TECHNIQUES
    // -----------------------------------------------------------------------------------------

    /** Every clone variant, the supply chest and the worker trades. */
    CLONES(SkillSection.SPECIAL, "naruto.gui.skill.branch.clones", 0xFFFFB04D),

    /** The marker, the thread, body adaptation and Kamui. */
    SPACE(SkillSection.SPECIAL, "naruto.gui.skill.branch.space", 0xFF9C8CFF),

    /** Sensing, night vision, genjutsu, henge, smoke and silence. */
    MIND(SkillSection.SPECIAL, "naruto.gui.skill.branch.mind", 0xFFFF77CC),

    /** Nature energy, its gathering and Sage Mode. */
    SAGE(SkillSection.SPECIAL, "naruto.gui.skill.branch.sage", 0xFF6FD3A0),

    // -----------------------------------------------------------------------------------------
    // 4. DOJUTSU - the eye bloodlines, reserved for later
    // -----------------------------------------------------------------------------------------

    /** The Sharingan: the copy wheel, its perception and Amaterasu. */
    SHARINGAN(SkillSection.DOJUTSU, "naruto.gui.skill.branch.sharingan", 0xFFE53935),

    /** The Byakugan: the circular vision and the Revolving Heaven. */
    BYAKUGAN(SkillSection.DOJUTSU, "naruto.gui.skill.branch.byakugan", 0xFFD7E3FF),

    /** The Jougan: the pure eye, its capacity, the dimensional step and the chakra sever. */
    JOUGAN(SkillSection.DOJUTSU, "naruto.gui.skill.branch.jougan", 0xFF64B5F6),

    /** The Rinnegan. */
    RINNEGAN(SkillSection.DOJUTSU, "naruto.gui.skill.branch.rinnegan", 0xFFB39DDB),

    /** The Senrigan: the far eye that borrows the sight of the caster's own clones. */
    SENRIGAN(SkillSection.DOJUTSU, "naruto.gui.skill.branch.senrigan", 0xFFE0C080);

    private final SkillSection section;
    private final String translationKey;
    private final int colour;

    SkillBranch(SkillSection section, String translationKey, int colour) {
        this.section = section;
        this.translationKey = translationKey;
        this.colour = colour;
    }

    /** The section whose sub-tab row this branch appears on. */
    public SkillSection section() {
        return this.section;
    }

    public String translationKey() {
        return this.translationKey;
    }

    /** ARGB colour used for the branch's nodes and links. */
    public int colour() {
        return this.colour;
    }

    /**
     * The branches of one section, in declaration order. Used by the screen to build the sub-tab
     * row, so adding a branch is one enum constant and nothing else.
     */
    public static SkillBranch[] of(SkillSection section) {
        return java.util.Arrays.stream(values())
                .filter(branch -> branch.section == section)
                .toArray(SkillBranch[]::new);
    }
}
