package com.sekwah.narutomod.abilities;

import net.minecraft.util.StringRepresentable;

/**
 * The five natures a ninja can be born to.
 *
 * <p>A body leans towards one element. The lean is small - a tenth off the techniques of that nature
 * and a tenth on the others - because it should shape how a ninja solves problems, not lock them out
 * of half the tree. The choice is written once and kept in the player's persistent data; the chakra
 * paper that makes it is a piece of story, not a resource to farm.</p>
 */
public enum Nature implements StringRepresentable {

    FIRE("fire"),
    WATER("water"),
    WIND("wind"),
    EARTH("earth"),
    WOOD("wood");

    private final String id;

    Nature(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** Translation key of the nature's name. */
    public String translationKey() {
        return "naruto.nature." + this.id;
    }

    /** The nature with this id, or null when the id names nothing. */
    public static Nature byId(String id) {
        for (Nature nature : values()) {
            if (nature.id.equals(id)) {
                return nature;
            }
        }
        return null;
    }
}
