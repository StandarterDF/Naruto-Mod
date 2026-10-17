package com.sekwah.narutomod.entity;

/**
 * What a worker clone has been told to do. The job is decided the moment the clone is summoned, by
 * the tool the caster held in their off hand (the seal itself sits in the main hand).
 *
 * @see com.sekwah.narutomod.entity.ai.WorkerBlockGoal
 */
public enum WorkerJob {

    /** A hoe in hand: the worker walks the fields, reaps what is ripe and replants it. */
    FARM,

    /** A pickaxe in hand: the worker digs stone and ore out of the ground around its post. */
    MINE,

    /** An axe in hand: the worker fells the trees around its post and carries the logs back. */
    CHOP
}
