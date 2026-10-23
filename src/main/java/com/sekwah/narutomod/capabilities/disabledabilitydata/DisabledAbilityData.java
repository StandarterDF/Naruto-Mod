package com.sekwah.narutomod.capabilities.disabledabilitydata;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The techniques a caster has switched off from the jutsu menu.
 *
 * <p>This is a convenience switch, not a lock: nothing is lost by disabling a technique, it simply
 * refuses to cast until it is switched back on. It exists because the mod has dozens of techniques
 * bound to the same three keys, and a player who keeps fumbling a combo they never use wants a way
 * to take it out of the rotation without forgetting the keys.</p>
 *
 * <p>Kept server side as the authoritative copy and synced to the client, which greys the rows out
 * and skips the packet when a disabled technique is clicked. Both cast packets check it again, so a
 * hacked client cannot slip past it.</p>
 */
public class DisabledAbilityData {

    private final Set<ResourceLocation> abilities;

    public DisabledAbilityData(int size) {
        this.abilities = new HashSet<>(size);
    }

    public DisabledAbilityData() {
        this.abilities = new HashSet<>();
    }

    public Set<ResourceLocation> getAbilities() {
        return this.abilities;
    }

    public boolean contains(ResourceLocation ability) {
        return ability != null && this.abilities.contains(ability);
    }

    /** @return the new disabled state of the technique */
    public boolean setDisabled(ResourceLocation ability, boolean disabled) {
        if (ability == null) {
            return false;
        }
        if (disabled) {
            this.abilities.add(ability);
            return true;
        }
        this.abilities.remove(ability);
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.abilities);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisabledAbilityData that)) {
            return false;
        }
        return this.abilities.equals(that.abilities);
    }
}
