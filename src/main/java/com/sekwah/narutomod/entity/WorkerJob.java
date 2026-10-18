package com.sekwah.narutomod.entity;

/**
 * What a worker clone has been told to do. The job is decided the moment the clone is summoned, by
 * the item the caster held in their main hand (the seal itself sits in the off hand).
 *
 * <p>Each shift is its own node in the skill tree, so a caster starts out with no worker trade at
 * all and buys the ones they want. The unlock key is what the tree stores and what the summon checks
 * before it will read a tool.</p>
 *
 * @see com.sekwah.narutomod.entity.ai.WorkerBlockGoal
 */
public enum WorkerJob {

    /** A hoe in hand: the worker walks the fields, reaps what is ripe and replants it. */
    FARM("worker:farm"),

    /** A pickaxe in hand: the worker looks for ore in the rock around itself and carries it back. */
    MINE("worker:mine"),

    /** An axe in hand: the worker fells the trees around its post and carries the logs back. */
    CHOP("worker:chop"),

    /**
     * A sword in hand: the worker hunts the animals around itself for meat, and picks the fruit that
     * grows on its own - sweet berries and melons. It leaves proper crops alone: those are the
     * farmhand's job.
     */
    HUNT("worker:hunt");

    private final String unlockKey;

    WorkerJob(String unlockKey) {
        this.unlockKey = unlockKey;
    }

    /** The skill-tree key that has to be learned before this shift can be handed out. */
    public String unlockKey() {
        return this.unlockKey;
    }

    /** The name shown to the player when the shift is talked about, e.g. in a locked message. */
    public String labelKey() {
        return "naruto.worker." + this.name().toLowerCase(java.util.Locale.ROOT);
    }
}
