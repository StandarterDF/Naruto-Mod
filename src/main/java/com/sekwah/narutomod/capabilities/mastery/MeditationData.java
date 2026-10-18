package com.sekwah.narutomod.capabilities.mastery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where a caster's meditating clones currently stand, and when they will run out.
 *
 * <p>A link is kept as a plain entry rather than found by scanning the world every tick, because a
 * mastered chakra meditation reaches many chunks and an area that size is thousands of chunks worth
 * of lookups per player per tick. Each clone writes itself in (with the moment it is due to pop)
 * while it ticks, and takes itself out when it goes.</p>
 *
 * <p>Server side only, and never written to disk: it is rebuilt from the clones themselves within a
 * tick of logging back in. Losing it on death is exactly what should happen - a caster who dies
 * keeps none of their meditations, see {@code NinjaCapabilityHandler#playerClone}.</p>
 */
public class MeditationData {

    /** One standing meditating clone: where it is and when it is due to go. */
    public record Link(ResourceLocation dimension, Vec3 position, long expiresAt) {
    }

    private final Map<UUID, Link> links = new HashMap<>();

    public void register(UUID clone, ResourceLocation dimension, Vec3 position, long expiresAt) {
        this.links.put(clone, new Link(dimension, position, expiresAt));
    }

    public void remove(UUID clone) {
        this.links.remove(clone);
    }

    public void clear() {
        this.links.clear();
    }

    /** Drops links whose clone should already be gone, so an unloaded one cannot feed forever. */
    public void prune(long gameTime) {
        this.links.values().removeIf(link -> link.expiresAt() < gameTime);
    }

    /** The live entries. Call {@link #prune(long)} before reading: dead ones linger until then. */
    public Map<UUID, Link> getLinks() {
        return this.links;
    }
}
