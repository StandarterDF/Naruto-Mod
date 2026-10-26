package com.sekwah.narutomod.skills;

import com.sekwah.narutomod.capabilities.INinjaData;

/**
 * The passive taught by the reading of intent: who in the crowd is looking at the caster, and who is
 * about to swing.
 *
 * <p>It is a mechanic rather than a technique - there is nothing to cast and nothing to pay - so it
 * has no ability class of its own and is asked about by key instead of by id. Everything that reads
 * or acts on the node goes through here, so the overlay that draws it and any later server-side use
 * can never drift apart on the key.</p>
 */
public final class IntentReading {

    /** The tree node that teaches the reading, see {@code Skill}. */
    public static final String UNLOCK = "mechanic:intent_reading";

    private IntentReading() {
    }

    /** @return whether this caster has learned the reading; false for a null or unknown node. */
    public static boolean has(INinjaData ninjaData) {
        return ninjaData != null && SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), UNLOCK);
    }
}
