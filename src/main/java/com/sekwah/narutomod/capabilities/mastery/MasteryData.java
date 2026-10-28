package com.sekwah.narutomod.capabilities.mastery;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How many times a player has cast each technique, which is the raw material mastery is worked out
 * from. Kept as a plain map of counts rather than a stored level so the curve (see
 * {@code abilities/mastery/Mastery}) can be retuned without touching anyone's save.
 *
 * <p>Equals and hashCode are on the contents, because the sync layer only ships this when it has
 * actually changed.</p>
 */
public class MasteryData {

    private final Map<ResourceLocation, Integer> casts;

    /**
     * Mastery credit that has not yet added up to a whole cast, per technique. A technique whose
     * effort is measured out over a channel - chakra charge restoring chakra, say - pays in as it
     * works, and the odd fraction is kept here so a run of short channels still counts. It is
     * scratch, server side only: never saved and never synced, because a fraction below one cast
     * would not move the bar anyway.
     */
    private final Map<ResourceLocation, Float> credit;

    public MasteryData() {
        this.casts = new HashMap<>();
        this.credit = new HashMap<>();
    }

    public int getCasts(ResourceLocation ability) {
        return this.casts.getOrDefault(ability, 0);
    }

    public void addCast(ResourceLocation ability) {
        this.casts.merge(ability, 1, Integer::sum);
    }

    /** Adds several casts at once, for a technique that counts its mastery in batches. */
    public void addCasts(ResourceLocation ability, int amount) {
        if (amount > 0) {
            this.casts.merge(ability, amount, Integer::sum);
        }
    }

    /** The banked fraction of a cast for a technique, which is not a cast yet. */
    public float getCredit(ResourceLocation ability) {
        return this.credit.getOrDefault(ability, 0f);
    }

    /** Banks a fraction of a cast, dropping the entry once it has been spent. */
    public void setCredit(ResourceLocation ability, float amount) {
        if (amount <= 0f) {
            this.credit.remove(ability);
        } else {
            this.credit.put(ability, amount);
        }
    }

    /** Writes a count back, dropping the entry entirely once it has run out. */
    public void setCasts(ResourceLocation ability, int amount) {
        if (amount <= 0) {
            this.casts.remove(ability);
        } else {
            this.casts.put(ability, amount);
        }
    }

    /** The live map, for walking every technique and for saving. */
    public Map<ResourceLocation, Integer> getCasts() {
        return this.casts;
    }

    /** Wipes every technique back to nothing, count and banked fraction alike. Debug reset only. */
    public void reset() {
        this.casts.clear();
        this.credit.clear();
    }

    /** Wipes a single technique, count and banked fraction alike. Debug reset only. */
    public void reset(ResourceLocation ability) {
        this.casts.remove(ability);
        this.credit.remove(ability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.casts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MasteryData other)) {
            return false;
        }
        return this.casts.equals(other.casts);
    }
}
