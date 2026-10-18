package com.sekwah.narutomod.capabilities.skills;

import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The skill nodes a caster has learned.
 *
 * <p>Only the ids are stored: everything a node is worth is looked up from the enum, so retuning a
 * node's numbers never touches a save. The set is synced to the client (see
 * {@code SkillDataSyncTracker}) because the skill screen and the HUD both need to know what is
 * open, and it is written to NBT so levels spent are never lost.</p>
 */
public class SkillData {

    private final Set<ResourceLocation> unlocked = new HashSet<>();

    public Set<ResourceLocation> getUnlocked() {
        return this.unlocked;
    }

    public boolean isUnlocked(ResourceLocation id) {
        return this.unlocked.contains(id);
    }

    /** @return true if the node was not already learned. */
    public boolean unlock(ResourceLocation id) {
        return this.unlocked.add(id);
    }

    public void reset() {
        this.unlocked.clear();
    }

    /** Fraction taken off technique costs by learned nodes. */
    public float costReduction() {
        return SkillTree.bonus(this.unlocked, SkillEffect.COST_REDUCTION);
    }

    /** Fraction taken off technique cooldowns by learned nodes. */
    public float cooldownReduction() {
        return SkillTree.bonus(this.unlocked, SkillEffect.COOLDOWN_REDUCTION);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SkillData data && this.unlocked.equals(data.unlocked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.unlocked);
    }
}
