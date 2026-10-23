package com.sekwah.narutomod.capabilities.absorption;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Chakra that was taken out of somebody else's technique and is waiting in the caster's palm.
 *
 * <p>Momoshiki does not block a jutsu - he eats it, and then hands it back harder. That is what this
 * holds: how much of each nature a caster has swallowed and not yet spent. A charge is spent the
 * moment the caster's own technique of the same nature lands, which is why the counts are kept per
 * nature instead of as one number: absorbing a fireball should make the next fire technique worse,
 * not the next water one.</p>
 *
 * <p>Kept in memory only (synced for the HUD, never written to disk): an open palm full of borrowed
 * chakra is a stance, and dropping the stance - or dying, or logging out - is supposed to lose it.</p>
 */
public class AbsorptionData {

    /** The natures that can be swallowed today: exactly the ones a technique is thrown with. */
    public enum Nature {
        FIRE,
        WATER,
        WIND
    }

    /** How many charges of one nature can be held at once. */
    public static final int MAX_PER_NATURE = 3;

    private final Map<Nature, Integer> charges = new EnumMap<>(Nature.class);

    public int charges(Nature nature) {
        return this.charges.getOrDefault(nature, 0);
    }

    /** @return everything held, across every nature. */
    public int total() {
        int total = 0;
        for (int count : this.charges.values()) {
            total += count;
        }
        return total;
    }

    /**
     * Swallows one technique of this nature.
     *
     * @return true if it was kept, false if that nature is already full - the caller still absorbed
     *         the technique, there was just nowhere to put it.
     */
    public boolean add(Nature nature) {
        int held = this.charges(nature);
        if (held >= MAX_PER_NATURE) {
            return false;
        }
        this.charges.put(nature, held + 1);
        return true;
    }

    /**
     * Spends everything held of one nature, which is what landing a technique of that nature does.
     *
     * @return how many charges were spent, so the caller can scale the hit.
     */
    public int take(Nature nature) {
        Integer held = this.charges.remove(nature);
        return held == null ? 0 : held;
    }

    /** Drops everything held, for a stance that has ended. */
    public void clear() {
        this.charges.clear();
    }

    public Map<Nature, Integer> snapshot() {
        return new EnumMap<>(this.charges);
    }

    /** Replaces the counts wholesale, used when the server's copy arrives on the client. */
    public void apply(Map<Nature, Integer> counts) {
        this.charges.clear();
        for (Map.Entry<Nature, Integer> entry : counts.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                this.charges.put(entry.getKey(), Math.min(entry.getValue(), MAX_PER_NATURE));
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AbsorptionData data && this.charges.equals(data.charges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.charges);
    }
}
