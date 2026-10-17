package com.sekwah.narutomod.entity;

/**
 * What a worker clone has been told to do. The job is decided the moment the clone is summoned, by
 * the item the caster held in their main hand (the seal itself sits in the off hand).
 *
 * @see com.sekwah.narutomod.entity.ai.WorkerBlockGoal
 */
public enum WorkerJob {

    /** A hoe in hand: the worker walks the fields, reaps what is ripe and replants it. */
    FARM,

    /** A pickaxe in hand: the worker looks for ore in the rock around itself and carries it back. */
    MINE,

    /** An axe in hand: the worker fells the trees around its post and carries the logs back. */
    CHOP,

    /**
     * A sword in hand: the worker hunts the animals around itself for meat, and picks the fruit that
     * grows on its own - sweet berries and melons. It leaves proper crops alone: those are the
     * farmhand's job.
     */
    HUNT
}
