package com.sekwah.narutomod.skills;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The index over {@link Skill} and the one place the rest of the game asks what a caster's learned
 * nodes add up to.
 *
 * <p>Prerequisites are stored as paths in the node table (a node cannot point at one declared after
 * it) and resolved once here, so an unknown path is a startup crash rather than a node that
 * silently never unlocks.</p>
 */
public final class SkillTree {

    private static final Map<String, Skill> BY_PATH = new HashMap<>();
    private static final Map<String, Skill> BY_UNLOCK = new HashMap<>();
    private static final Map<Skill, List<Skill>> PREREQUISITES = new HashMap<>();

    static {
        for (Skill skill : Skill.all()) {
            BY_PATH.put(skill.path(), skill);
            if (skill.isUnlock()) {
                BY_UNLOCK.put(skill.unlock(), skill);
            }
        }
        for (Skill skill : Skill.all()) {
            List<Skill> required = new ArrayList<>();
            for (String path : skill.requires()) {
                Skill prerequisite = BY_PATH.get(path);
                if (prerequisite == null) {
                    throw new IllegalStateException("Skill " + skill.path()
                            + " requires unknown skill " + path);
                }
                required.add(prerequisite);
            }
            PREREQUISITES.put(skill, List.copyOf(required));
        }
    }

    private SkillTree() {
    }

    public static Skill byPath(String path) {
        return BY_PATH.get(path);
    }

    public static Skill byId(ResourceLocation id) {
        return id == null ? null : BY_PATH.get(id.getPath());
    }

    public static List<Skill> prerequisites(Skill skill) {
        return PREREQUISITES.get(skill);
    }

    public static boolean isUnlocked(Set<ResourceLocation> unlocked, Skill skill) {
        return unlocked.contains(skill.id());
    }

    public static boolean prerequisitesMet(Skill skill, Set<ResourceLocation> unlocked) {
        for (Skill prerequisite : PREREQUISITES.get(skill)) {
            if (!unlocked.contains(prerequisite.id())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the caster has learned the node that teaches the given unlock key. This is what the
     * technique gating and the worker trades ask; an unknown key is simply never unlocked, so a node
     * that was renamed or removed fails safe rather than opening something by accident.
     */
    public static boolean hasUnlock(Set<ResourceLocation> unlocked, String unlockKey) {
        if (unlocked == null || unlocked.isEmpty() || unlockKey == null) {
            return false;
        }
        Skill skill = BY_UNLOCK.get(unlockKey);
        return skill != null && unlocked.contains(skill.id());
    }

    /**
     * Sums every learned node that grants the given effect. Called from the player data on demand;
     * the tree is small enough that walking it is cheaper than keeping a cache in sync.
     */
    public static float bonus(Set<ResourceLocation> unlocked, SkillEffect effect) {
        if (unlocked == null || unlocked.isEmpty()) {
            return 0.0f;
        }
        float total = 0.0f;
        for (Skill skill : Skill.all()) {
            if (skill.effect() == effect && unlocked.contains(skill.id())) {
                total += skill.amount();
            }
        }
        return total;
    }
}
