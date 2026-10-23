package com.sekwah.narutomod.ranks;

import javax.annotation.Nullable;

/**
 * The ladder a ninja climbs: Genin, Chunin, Jonin and S-rank.
 *
 * <p>Ranks are milestones rather than a separate progression system - they are earned by the same
 * two things the rest of the mod already asks for, learned skill nodes and the heads of the world's
 * great monsters. That keeps a rank a summary of what the player has actually done instead of a
 * second currency.</p>
 *
 * <p>They are also advancement triggers (see {@code triggers/NinjaRankTrigger}), so the advancement
 * screen is where the promotion is remembered.</p>
 */
public enum NinjaRank {

    GENIN("genin", 0, 0),
    CHUNIN("chunin", 12, 0),
    JONIN("jonin", 30, 1),
    S_RANK("s_rank", 60, 3);

    private final String id;
    private final int skillNodes;
    private final int bossMilestones;

    NinjaRank(String id, int skillNodes, int bossMilestones) {
        this.id = id;
        this.skillNodes = skillNodes;
        this.bossMilestones = bossMilestones;
    }

    /** The plain id written into NBT and advancement JSON. */
    public String id() {
        return this.id;
    }

    /** The translation key of the rank's name. */
    public String translationKey() {
        return "naruto.rank." + this.id;
    }

    /** How many skill nodes the rank asks for. */
    public int skillNodes() {
        return this.skillNodes;
    }

    /** How many boss milestones the rank asks for. */
    public int bossMilestones() {
        return this.bossMilestones;
    }

    /** Whether the given progress meets this rank. */
    public boolean isReached(int skills, int bosses) {
        return skills >= this.skillNodes && bosses >= this.bossMilestones;
    }

    /** Looks a rank up by id, falling back to genin: an unknown id is a save from a newer version. */
    public static NinjaRank byId(@Nullable String id) {
        if (id != null) {
            for (NinjaRank rank : values()) {
                if (rank.id.equals(id)) {
                    return rank;
                }
            }
        }
        return GENIN;
    }
}
