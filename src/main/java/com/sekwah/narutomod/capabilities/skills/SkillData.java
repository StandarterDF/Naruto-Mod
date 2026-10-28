package com.sekwah.narutomod.capabilities.skills;

import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The skill nodes a caster has learned and the skill points they still have to spend.
 *
 * <p>Only the ids are stored: everything a node is worth is looked up from the enum, so retuning a
 * node's numbers never touches a save. Points are bought with experience and then spent on nodes,
 * which is why the balance has to travel with the learned set. Both are synced to the client (see
 * {@code SkillDataSyncTracker}) because the skill screen and the HUD need to know what is open, and
 * both are written to NBT so points and nodes are never lost.</p>
 */
public class SkillData {

    private final Set<ResourceLocation> unlocked = new HashSet<>();

    /** The techniques whose scrolls the caster has found and read. */
    private final Set<ResourceLocation> scrolls = new HashSet<>();

    /** Banked skill points, already paid for with experience. */
    private int points;

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

    public int getPoints() {
        return this.points;
    }

    /** The techniques whose scrolls are known, as technique ids. */
    public Set<ResourceLocation> getScrolls() {
        return this.scrolls;
    }

    public boolean hasScroll(ResourceLocation knowledge) {
        return this.scrolls.contains(knowledge);
    }

    /** @return true if this scroll was not known before, so the caller can tell a first read from a duplicate. */
    public boolean learnScroll(ResourceLocation knowledge) {
        return this.scrolls.add(knowledge);
    }

    public void addPoints(int amount) {
        if (amount > 0) {
            this.points += amount;
        }
    }

    /**
     * Takes points out of the bank. Refuses rather than going negative, so a purchase that raced
     * another one can never leave the caster in debt.
     *
     * @return true if the whole amount was available and spent.
     */
    public boolean spendPoints(int amount) {
        if (amount < 0 || this.points < amount) {
            return false;
        }
        this.points -= amount;
        return true;
    }

    public void reset() {
        this.unlocked.clear();
        this.scrolls.clear();
        this.points = 0;
    }

    /** Fraction taken off technique costs by learned nodes. */
    public float costReduction() {
        return SkillTree.bonus(this.unlocked, SkillEffect.COST_REDUCTION);
    }

    /** Fraction taken off technique cooldowns by learned nodes. */
    public float cooldownReduction() {
        float total = SkillTree.bonus(this.unlocked, SkillEffect.COOLDOWN_REDUCTION);
        // Each learned kenjutsu technique shaves a little off the cooldown of every blade technique.
        for (Skill skill : Skill.all()) {
            if (skill.branch() == SkillBranch.KENJUTSU
                    && skill.unlock() != null
                    && this.unlocked.contains(skill.id())) {
                total += 0.02f;
            }
        }
        return total;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SkillData data && this.points == data.points
                && this.unlocked.equals(data.unlocked)
                && this.scrolls.equals(data.scrolls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.unlocked, this.scrolls, this.points);
    }
}
